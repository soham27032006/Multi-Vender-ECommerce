package com.marketplace.dao;

import com.marketplace.exception.DataAccessException;
import com.marketplace.model.Product;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDAO implements Repository<Product, Integer> {

    private static final String INSERT =
            "INSERT INTO products (vendor_id, name, category, price, stock) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT id, vendor_id, name, category, price, stock FROM products WHERE id = ?";
    private static final String SELECT_ALL =
            "SELECT id, vendor_id, name, category, price, stock FROM products ORDER BY id";
    private static final String SELECT_BY_VENDOR =
            "SELECT id, vendor_id, name, category, price, stock FROM products WHERE vendor_id = ? ORDER BY id";
    private static final String UPDATE =
            "UPDATE products SET name = ?, category = ?, price = ?, stock = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM products WHERE id = ?";

    // "AND stock >= ?" makes check-and-decrease ONE atomic SQL statement:
    // two buyers can't both take the last unit, because only one UPDATE will match.
    private static final String DECREASE_STOCK =
            "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";
    private static final String INCREASE_STOCK =
            "UPDATE products SET stock = stock + ? WHERE id = ?";

    @Override
    public Product save(Product p) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getVendorId());
            ps.setString(2, p.getName());
            ps.setString(3, p.getCategory());
            ps.setDouble(4, p.getPrice());
            ps.setInt(5, p.getStock());
            ps.executeUpdate();
            p.setId(JdbcHelper.getGeneratedId(ps));
            return p;
        } catch (SQLException e) {
            throw new DataAccessException("Could not save product " + p.getName(), e);
        }
    }

    @Override
    public Optional<Product> findById(Integer id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Could not find product #" + id, e);
        }
    }

    /** Transaction version: reads through the caller's connection. */
    public Optional<Product> findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Product> findAll() {
        List<Product> products = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                products.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list products", e);
        }
        return products;
    }

    public List<Product> findByVendor(int vendorId) {
        List<Product> products = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_VENDOR)) {
            ps.setInt(1, vendorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list products of vendor #" + vendorId, e);
        }
        return products;
    }

    @Override
    public boolean update(Product p) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE)) {
            ps.setString(1, p.getName());
            ps.setString(2, p.getCategory());
            ps.setDouble(3, p.getPrice());
            ps.setInt(4, p.getStock());
            ps.setInt(5, p.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update product #" + p.getId(), e);
        }
    }

    @Override
    public boolean delete(Integer id) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete product #" + id, e);
        }
    }

    /**
     * Runs inside the checkout transaction. Returns false when stock was too low
     * (0 rows updated), so the service can roll back the whole order.
     */
    public boolean decreaseStock(Connection conn, int productId, int qty) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(DECREASE_STOCK)) {
            ps.setInt(1, qty);
            ps.setInt(2, productId);
            ps.setInt(3, qty);
            return ps.executeUpdate() == 1;
        }
    }

    /** Used by returns to put items back on the shelf. */
    public boolean increaseStock(Connection conn, int productId, int qty) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INCREASE_STOCK)) {
            ps.setInt(1, qty);
            ps.setInt(2, productId);
            return ps.executeUpdate() == 1;
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getInt("id"),
                rs.getInt("vendor_id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getDouble("price"),
                rs.getInt("stock"));
    }
}
