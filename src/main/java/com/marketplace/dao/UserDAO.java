package com.marketplace.dao;

import com.marketplace.exception.DataAccessException;
import com.marketplace.model.Customer;
import com.marketplace.model.User;
import com.marketplace.model.Vendor;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Customers and vendors share the 'users' table; the role column picks the subclass. */
public class UserDAO implements Repository<User, Integer> {

    private static final String COLUMNS =
            "id, name, email, phone, role, address, shop_name, commission_rate";

    private static final String INSERT =
            "INSERT INTO users (name, email, phone, role, address, shop_name, commission_rate) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID    = "SELECT " + COLUMNS + " FROM users WHERE id = ?";
    private static final String SELECT_BY_EMAIL = "SELECT " + COLUMNS + " FROM users WHERE email = ?";
    private static final String SELECT_ALL      = "SELECT " + COLUMNS + " FROM users ORDER BY id";
    private static final String SELECT_BY_ROLE  = "SELECT " + COLUMNS + " FROM users WHERE role = ? ORDER BY id";
    private static final String UPDATE =
            "UPDATE users SET name = ?, email = ?, phone = ?, address = ?, shop_name = ?, commission_rate = ? "
          + "WHERE id = ?";
    private static final String DELETE = "DELETE FROM users WHERE id = ?";
    // Note: COLUMNS is a fixed constant, never user input, so building these strings is safe.

    @Override
    public User save(User user) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getRole());
            setRoleColumns(ps, user, 5);
            ps.executeUpdate();
            user.setId(JdbcHelper.getGeneratedId(ps));
            return user;
        } catch (SQLException e) {
            throw new DataAccessException("Could not save user " + user.getEmail(), e);
        }
    }

    @Override
    public Optional<User> findById(Integer id) {
        return findOne(SELECT_BY_ID, id);
    }

    public Optional<User> findByEmail(String email) {
        return findOne(SELECT_BY_EMAIL, email);
    }

    /** Typed lookup so services don't have to cast. */
    public Optional<Vendor> findVendorById(int id) {
        return findById(id)
                .filter(u -> u instanceof Vendor)
                .map(u -> (Vendor) u);
    }

    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list users", e);
        }
        return users;
    }

    public List<Vendor> findAllVendors() {
        List<Vendor> vendors = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ROLE)) {
            ps.setString(1, "VENDOR");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vendors.add((Vendor) map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list vendors", e);
        }
        return vendors;
    }

    @Override
    public boolean update(User user) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            setRoleColumns(ps, user, 4);
            ps.setInt(7, user.getId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not update user #" + user.getId(), e);
        }
    }

    @Override
    public boolean delete(Integer id) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete user #" + id, e);
        }
    }

    // ---------- helpers ----------

    /** Fills address, shop_name, commission_rate starting at 'start'; unused ones become NULL. */
    private void setRoleColumns(PreparedStatement ps, User user, int start) throws SQLException {
        if (user instanceof Customer c) {
            ps.setString(start, c.getAddress());
            ps.setNull(start + 1, Types.VARCHAR);
            ps.setNull(start + 2, Types.DECIMAL);
        } else if (user instanceof Vendor v) {
            ps.setNull(start, Types.VARCHAR);
            ps.setString(start + 1, v.getShopName());
            ps.setDouble(start + 2, v.getCommissionRate());
        }
    }

    private Optional<User> findOne(String sql, Object param) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not find user " + param, e);
        }
    }

    /** Polymorphic mapping: one row becomes a Customer or a Vendor object. */
    private User map(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String phone = rs.getString("phone");

        if ("VENDOR".equals(rs.getString("role"))) {
            return new Vendor(id, name, email, phone,
                    rs.getString("shop_name"), rs.getDouble("commission_rate"));
        }
        return new Customer(id, name, email, phone, rs.getString("address"));
    }
}
