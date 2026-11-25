package com.marketplace.util;

import com.marketplace.exception.DataAccessException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shows JDBC metadata: info about the database and about a table's columns. */
public final class DBInfo {

    // Table names can't be bound with '?', so instead of concatenating user input
    // we keep one fixed query per known table. "LIMIT 0" fetches columns, no rows.
    private static final Map<String, String> TABLE_QUERIES = new LinkedHashMap<>();
    static {
        TABLE_QUERIES.put("users",           "SELECT * FROM users LIMIT 0");
        TABLE_QUERIES.put("products",        "SELECT * FROM products LIMIT 0");
        TABLE_QUERIES.put("parent_orders",   "SELECT * FROM parent_orders LIMIT 0");
        TABLE_QUERIES.put("sub_orders",      "SELECT * FROM sub_orders LIMIT 0");
        TABLE_QUERIES.put("order_items",     "SELECT * FROM order_items LIMIT 0");
        TABLE_QUERIES.put("payments",        "SELECT * FROM payments LIMIT 0");
        TABLE_QUERIES.put("payouts",         "SELECT * FROM payouts LIMIT 0");
        TABLE_QUERIES.put("return_requests", "SELECT * FROM return_requests LIMIT 0");
    }

    private DBInfo() {
    }

    public static Set<String> knownTables() {
        return TABLE_QUERIES.keySet();
    }

    /** DatabaseMetaData: facts about the DB server and the JDBC driver. */
    public static void printDatabaseInfo() {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("Database : " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
            System.out.println("Driver   : " + meta.getDriverName() + " " + meta.getDriverVersion());
            System.out.println("JDBC     : " + meta.getJDBCMajorVersion() + "." + meta.getJDBCMinorVersion());
            System.out.println("URL      : " + meta.getURL());
            System.out.println("User     : " + meta.getUserName());
            System.out.println("Transactions supported: " + meta.supportsTransactions());
        } catch (SQLException e) {
            throw new DataAccessException("Could not read database metadata", e);
        }
    }

    /** ResultSetMetaData: column names, SQL types, sizes and nullability of one table. */
    public static void printTableInfo(String table) {
        String sql = TABLE_QUERIES.get(table);
        if (sql == null) {
            System.out.println("Unknown table '" + table + "'. Choose from " + knownTables());
            return;
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            System.out.println("Table '" + table + "' has " + meta.getColumnCount() + " columns:");
            System.out.printf("  %-18s %-12s %-6s %s%n", "COLUMN", "TYPE", "SIZE", "NULLABLE");
            for (int i = 1; i <= meta.getColumnCount(); i++) {   // JDBC columns start at 1
                boolean nullable = meta.isNullable(i) == ResultSetMetaData.columnNullable;
                System.out.printf("  %-18s %-12s %-6d %s%n",
                        meta.getColumnName(i), meta.getColumnTypeName(i),
                        meta.getColumnDisplaySize(i), nullable ? "YES" : "NO");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read metadata of " + table, e);
        }
    }
}
