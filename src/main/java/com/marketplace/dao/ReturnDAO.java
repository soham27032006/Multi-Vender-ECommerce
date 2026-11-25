package com.marketplace.dao;

import com.marketplace.exception.DataAccessException;
import com.marketplace.model.ReturnRequest;
import com.marketplace.model.ReturnStatus;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReturnDAO implements Repository<ReturnRequest, Integer> {

    private static final String COLUMNS = "id, sub_order_id, reason, refund_amount, status, requested_at";

    private static final String INSERT =
            "INSERT INTO return_requests (sub_order_id, reason, refund_amount, status, requested_at) "
          + "VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID        = "SELECT " + COLUMNS + " FROM return_requests WHERE id = ?";
    private static final String SELECT_BY_SUB_ORDER = "SELECT " + COLUMNS + " FROM return_requests WHERE sub_order_id = ? ORDER BY id";
    private static final String SELECT_ALL          = "SELECT " + COLUMNS + " FROM return_requests ORDER BY id";
    private static final String UPDATE_STATUS       = "UPDATE return_requests SET status = ? WHERE id = ?";
    private static final String DELETE              = "DELETE FROM return_requests WHERE id = ?";

    @Override
    public ReturnRequest save(ReturnRequest r) {
        try (Connection conn = DBConnection.getConnection()) {
            return save(conn, r);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save return request", e);
        }
    }

    public ReturnRequest save(Connection conn, ReturnRequest r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getSubOrderId());
            ps.setString(2, r.getReason());
            ps.setDouble(3, r.getRefundAmount());
            ps.setString(4, r.getStatus().name());
            JdbcHelper.setDateTime(ps, 5, r.getRequestedAt());
            ps.executeUpdate();
            r.setId(JdbcHelper.getGeneratedId(ps));
            return r;
        }
    }

    @Override
    public Optional<ReturnRequest> findById(Integer id) {
        List<ReturnRequest> list = findList(SELECT_BY_ID, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<ReturnRequest> findBySubOrder(int subOrderId) {
        return findList(SELECT_BY_SUB_ORDER, subOrderId);
    }

    @Override
    public List<ReturnRequest> findAll() {
        return findList(SELECT_ALL, null);
    }

    @Override
    public boolean update(ReturnRequest r) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateStatus(conn, r);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update return #" + r.getId(), e);
        }
    }

    public boolean updateStatus(Connection conn, ReturnRequest r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, r.getStatus().name());
            ps.setInt(2, r.getId());
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
            throw new DataAccessException("Could not delete return #" + id, e);
        }
    }

    private List<ReturnRequest> findList(String sql, Integer param) {
        List<ReturnRequest> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) {
                ps.setInt(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read return requests", e);
        }
        return list;
    }

    private ReturnRequest map(ResultSet rs) throws SQLException {
        return new ReturnRequest(
                rs.getInt("id"),
                rs.getInt("sub_order_id"),
                rs.getString("reason"),
                rs.getDouble("refund_amount"),
                ReturnStatus.valueOf(rs.getString("status")),
                JdbcHelper.getDateTime(rs, "requested_at"));
    }
}
