package com.marketplace.dao;

import com.marketplace.exception.DataAccessException;
import com.marketplace.model.PaymentStatus;
import com.marketplace.model.Payout;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PayoutDAO implements Repository<Payout, Integer> {

    private static final String COLUMNS =
            "id, vendor_id, sub_order_id, gross_amount, commission, status, paid_at";

    private static final String INSERT =
            "INSERT INTO payouts (vendor_id, sub_order_id, gross_amount, commission, status, paid_at) "
          + "VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID        = "SELECT " + COLUMNS + " FROM payouts WHERE id = ?";
    private static final String SELECT_BY_SUB_ORDER = "SELECT " + COLUMNS + " FROM payouts WHERE sub_order_id = ?";
    private static final String SELECT_BY_VENDOR    = "SELECT " + COLUMNS + " FROM payouts WHERE vendor_id = ? ORDER BY id";
    private static final String SELECT_ALL          = "SELECT " + COLUMNS + " FROM payouts ORDER BY id";
    private static final String UPDATE_STATUS       = "UPDATE payouts SET status = ?, paid_at = ? WHERE id = ?";
    private static final String DELETE              = "DELETE FROM payouts WHERE id = ?";

    @Override
    public Payout save(Payout payout) {
        try (Connection conn = DBConnection.getConnection()) {
            return save(conn, payout);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save payout", e);
        }
    }

    public Payout save(Connection conn, Payout payout) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, payout.getVendorId());
            ps.setInt(2, payout.getSubOrderId());
            ps.setDouble(3, payout.getGrossAmount());
            ps.setDouble(4, payout.getCommission());
            ps.setString(5, payout.getStatus().name());
            JdbcHelper.setDateTime(ps, 6, payout.getPaidAt());
            ps.executeUpdate();
            payout.setId(JdbcHelper.getGeneratedId(ps));
            return payout;
        }
    }

    @Override
    public Optional<Payout> findById(Integer id) {
        List<Payout> list = findList(SELECT_BY_ID, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Payout> findBySubOrder(int subOrderId) {
        List<Payout> list = findList(SELECT_BY_SUB_ORDER, subOrderId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** Transaction version: return flow reads and reverses the payout in one transaction. */
    public Optional<Payout> findBySubOrder(Connection conn, int subOrderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_SUB_ORDER)) {
            ps.setInt(1, subOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Payout> findByVendor(int vendorId) {
        return findList(SELECT_BY_VENDOR, vendorId);
    }

    @Override
    public List<Payout> findAll() {
        return findList(SELECT_ALL, null);
    }

    /** Only status/paid_at change after creation; amounts are fixed at checkout. */
    @Override
    public boolean update(Payout payout) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateStatus(conn, payout);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update payout #" + payout.getId(), e);
        }
    }

    public boolean updateStatus(Connection conn, Payout payout) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, payout.getStatus().name());
            JdbcHelper.setDateTime(ps, 2, payout.getPaidAt());
            ps.setInt(3, payout.getId());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean delete(Integer id) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete payout #" + id, e);
        }
    }

    private List<Payout> findList(String sql, Integer param) {
        List<Payout> payouts = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) {
                ps.setInt(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payouts.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read payouts", e);
        }
        return payouts;
    }

    private Payout map(ResultSet rs) throws SQLException {
        return new Payout(
                rs.getInt("id"),
                rs.getInt("vendor_id"),
                rs.getInt("sub_order_id"),
                rs.getDouble("gross_amount"),
                rs.getDouble("commission"),
                PaymentStatus.valueOf(rs.getString("status")),
                JdbcHelper.getDateTime(rs, "paid_at"));
    }
}
