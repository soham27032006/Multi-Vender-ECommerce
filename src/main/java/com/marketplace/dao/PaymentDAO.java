package com.marketplace.dao;

import com.marketplace.exception.DataAccessException;
import com.marketplace.model.Payment;
import com.marketplace.model.PaymentMode;
import com.marketplace.model.PaymentStatus;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PaymentDAO implements Repository<Payment, Integer> {

    private static final String INSERT =
            "INSERT INTO payments (parent_order_id, amount, mode, status, paid_at) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT id, parent_order_id, amount, mode, status, paid_at FROM payments WHERE id = ?";
    private static final String SELECT_BY_ORDER =
            "SELECT id, parent_order_id, amount, mode, status, paid_at FROM payments WHERE parent_order_id = ?";
    private static final String SELECT_ALL =
            "SELECT id, parent_order_id, amount, mode, status, paid_at FROM payments ORDER BY id";
    private static final String UPDATE =
            "UPDATE payments SET amount = ?, status = ?, paid_at = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM payments WHERE id = ?";

    @Override
    public Payment save(Payment payment) {
        try (Connection conn = DBConnection.getConnection()) {
            return save(conn, payment);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save payment", e);
        }
    }

    public Payment save(Connection conn, Payment payment) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, payment.getParentOrderId());
            ps.setDouble(2, payment.getAmount());
            ps.setString(3, payment.getMode().name());
            ps.setString(4, payment.getStatus().name());
            JdbcHelper.setDateTime(ps, 5, payment.getPaidAt());
            ps.executeUpdate();
            payment.setId(JdbcHelper.getGeneratedId(ps));
            return payment;
        }
    }

    @Override
    public Optional<Payment> findById(Integer id) {
        return findOne(SELECT_BY_ID, id);
    }

    /** One payment per parent order, so this returns at most one row. */
    public Optional<Payment> findByOrderId(int parentOrderId) {
        return findOne(SELECT_BY_ORDER, parentOrderId);
    }

    @Override
    public List<Payment> findAll() {
        List<Payment> payments = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                payments.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list payments", e);
        }
        return payments;
    }

    @Override
    public boolean update(Payment payment) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, payment);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update payment #" + payment.getId(), e);
        }
    }

    /** Transaction version: refunds update amount/status together with restock. */
    public boolean update(Connection conn, Payment payment) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE)) {
            ps.setDouble(1, payment.getAmount());
            ps.setString(2, payment.getStatus().name());
            JdbcHelper.setDateTime(ps, 3, payment.getPaidAt());
            ps.setInt(4, payment.getId());
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
            throw new DataAccessException("Could not delete payment #" + id, e);
        }
    }

    private Optional<Payment> findOne(String sql, int param) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not find payment " + param, e);
        }
    }

    private Payment map(ResultSet rs) throws SQLException {
        return new Payment(
                rs.getInt("id"),
                rs.getInt("parent_order_id"),
                rs.getDouble("amount"),
                PaymentMode.valueOf(rs.getString("mode")),
                PaymentStatus.valueOf(rs.getString("status")),
                JdbcHelper.getDateTime(rs, "paid_at"));
    }
}
