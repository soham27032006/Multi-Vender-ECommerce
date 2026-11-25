package com.marketplace.dao;

import com.marketplace.exception.DataAccessException;
import com.marketplace.model.OrderItem;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.SubOrder;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Table group: parent_orders + sub_orders + order_items.
 * They are always saved and loaded together, so one DAO owns all three.
 */
public class OrderDAO implements Repository<ParentOrder, Integer> {

    private static final String INSERT_PARENT =
            "INSERT INTO parent_orders (customer_id, order_date, total_amount) VALUES (?, ?, ?)";
    private static final String INSERT_SUB =
            "INSERT INTO sub_orders (parent_order_id, vendor_id, status, subtotal, commission, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?)";
    private static final String INSERT_ITEM =
            "INSERT INTO order_items (sub_order_id, product_id, product_name, quantity, unit_price) "
          + "VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_PARENT_BY_ID =
            "SELECT id, customer_id, order_date, total_amount FROM parent_orders WHERE id = ?";
    private static final String SELECT_ALL_PARENTS =
            "SELECT id, customer_id, order_date, total_amount FROM parent_orders ORDER BY id";
    private static final String SELECT_PARENTS_BY_CUSTOMER =
            "SELECT id, customer_id, order_date, total_amount FROM parent_orders WHERE customer_id = ? ORDER BY id";

    private static final String SELECT_SUBS_BY_PARENT =
            "SELECT id, parent_order_id, vendor_id, status, subtotal, commission, updated_at "
          + "FROM sub_orders WHERE parent_order_id = ? ORDER BY id";
    private static final String SELECT_SUB_BY_ID =
            "SELECT id, parent_order_id, vendor_id, status, subtotal, commission, updated_at "
          + "FROM sub_orders WHERE id = ?";
    private static final String SELECT_SUBS_BY_VENDOR =
            "SELECT id, parent_order_id, vendor_id, status, subtotal, commission, updated_at "
          + "FROM sub_orders WHERE vendor_id = ? ORDER BY id";
    private static final String SELECT_ITEMS_BY_SUB =
            "SELECT id, sub_order_id, product_id, product_name, quantity, unit_price "
          + "FROM order_items WHERE sub_order_id = ? ORDER BY id";

    private static final String UPDATE_PARENT_TOTAL = "UPDATE parent_orders SET total_amount = ? WHERE id = ?";
    private static final String UPDATE_SUB_STATUS   = "UPDATE sub_orders SET status = ?, updated_at = ? WHERE id = ?";
    // sub_orders and order_items rows go too, via ON DELETE CASCADE in the schema.
    private static final String DELETE_PARENT = "DELETE FROM parent_orders WHERE id = ?";

    // ---------- save (whole order tree) ----------

    @Override
    public ParentOrder save(ParentOrder order) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                save(conn, order);
                conn.commit();
                return order;
            } catch (SQLException e) {
                conn.rollback(); // never leave a parent order without its sub-orders
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save order", e);
        }
    }

    /** Transaction version: caller controls commit/rollback (used by checkout). */
    public ParentOrder save(Connection conn, ParentOrder order) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_PARENT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getCustomerId());
            JdbcHelper.setDateTime(ps, 2, order.getOrderDate());
            ps.setDouble(3, order.getTotalAmount());
            ps.executeUpdate();
            order.setId(JdbcHelper.getGeneratedId(ps));
        }
        for (SubOrder sub : order.getSubOrders()) {
            sub.setParentOrderId(order.getId());
            saveSubOrder(conn, sub);
        }
        return order;
    }

    private void saveSubOrder(Connection conn, SubOrder sub) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SUB, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sub.getParentOrderId());
            ps.setInt(2, sub.getVendorId());
            ps.setString(3, sub.getStatus().name());
            ps.setDouble(4, sub.getSubtotal());
            ps.setDouble(5, sub.getCommission());
            JdbcHelper.setDateTime(ps, 6, sub.getUpdatedAt());
            ps.executeUpdate();
            sub.setId(JdbcHelper.getGeneratedId(ps));
        }
        // Batch: one round trip for all items of this sub-order.
        try (PreparedStatement ps = conn.prepareStatement(INSERT_ITEM)) {
            for (OrderItem item : sub.getItems()) {
                item.setSubOrderId(sub.getId());
                ps.setInt(1, item.getSubOrderId());
                ps.setInt(2, item.getProductId());
                ps.setString(3, item.getProductName());
                ps.setInt(4, item.getQuantity());
                ps.setDouble(5, item.getUnitPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------- read ----------

    @Override
    public Optional<ParentOrder> findById(Integer id) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_PARENT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                ParentOrder order = mapParent(rs);
                loadSubOrders(conn, order);
                return Optional.of(order);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not find order #" + id, e);
        }
    }

    @Override
    public List<ParentOrder> findAll() {
        return findParents(SELECT_ALL_PARENTS, null);
    }

    public List<ParentOrder> findByCustomer(int customerId) {
        return findParents(SELECT_PARENTS_BY_CUSTOMER, customerId);
    }

    public Optional<SubOrder> findSubOrderById(int subOrderId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findSubOrderById(conn, subOrderId);
        } catch (SQLException e) {
            throw new DataAccessException("Could not find sub-order #" + subOrderId, e);
        }
    }

    /** Transaction version: used by returns so the read sees the same transaction. */
    public Optional<SubOrder> findSubOrderById(Connection conn, int subOrderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_SUB_BY_ID)) {
            ps.setInt(1, subOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                SubOrder sub = mapSub(rs);
                loadItems(conn, sub);
                return Optional.of(sub);
            }
        }
    }

    public List<SubOrder> findSubOrdersByVendor(int vendorId) {
        List<SubOrder> subs = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SUBS_BY_VENDOR)) {
            ps.setInt(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    subs.add(mapSub(rs));
                }
            }
            for (SubOrder sub : subs) {
                loadItems(conn, sub);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list sub-orders of vendor #" + vendorId, e);
        }
        return subs;
    }

    // ---------- update / delete ----------

    /** Only the total is editable on a parent order; sub-orders change via status updates. */
    @Override
    public boolean update(ParentOrder order) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_PARENT_TOTAL)) {
            ps.setDouble(1, order.getTotalAmount());
            ps.setInt(2, order.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update order #" + order.getId(), e);
        }
    }

    public boolean updateSubOrderStatus(int subOrderId, OrderStatus status) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateSubOrderStatus(conn, subOrderId, status);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update status of sub-order #" + subOrderId, e);
        }
    }

    public boolean updateSubOrderStatus(Connection conn, int subOrderId, OrderStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SUB_STATUS)) {
            ps.setString(1, status.name());
            JdbcHelper.setDateTime(ps, 2, LocalDateTime.now());
            ps.setInt(3, subOrderId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean delete(Integer id) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_PARENT)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete order #" + id, e);
        }
    }

    // ---------- helpers ----------

    private List<ParentOrder> findParents(String sql, Integer customerId) {
        List<ParentOrder> orders = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (customerId != null) {
                ps.setInt(1, customerId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapParent(rs));
                }
            }
            // Simple approach: one query per order for its children. Fine for a console app.
            for (ParentOrder order : orders) {
                loadSubOrders(conn, order);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list orders", e);
        }
        return orders;
    }

    private void loadSubOrders(Connection conn, ParentOrder order) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_SUBS_BY_PARENT)) {
            ps.setInt(1, order.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    order.addSubOrder(mapSub(rs));
                }
            }
        }
        for (SubOrder sub : order.getSubOrders()) {
            loadItems(conn, sub);
        }
    }

    private void loadItems(Connection conn, SubOrder sub) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_ITEMS_BY_SUB)) {
            ps.setInt(1, sub.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sub.addItem(new OrderItem(
                            rs.getInt("id"),
                            rs.getInt("sub_order_id"),
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            rs.getInt("quantity"),
                            rs.getDouble("unit_price")));
                }
            }
        }
    }

    private ParentOrder mapParent(ResultSet rs) throws SQLException {
        return new ParentOrder(
                rs.getInt("id"),
                rs.getInt("customer_id"),
                JdbcHelper.getDateTime(rs, "order_date"),
                rs.getDouble("total_amount"));
    }

    private SubOrder mapSub(ResultSet rs) throws SQLException {
        return new SubOrder(
                rs.getInt("id"),
                rs.getInt("parent_order_id"),
                rs.getInt("vendor_id"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getDouble("subtotal"),
                rs.getDouble("commission"),
                JdbcHelper.getDateTime(rs, "updated_at"));
    }
}
