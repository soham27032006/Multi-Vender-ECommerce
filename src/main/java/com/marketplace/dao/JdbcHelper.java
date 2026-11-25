package com.marketplace.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/** Small JDBC helpers shared by all DAOs (package-private: only DAOs need them). */
final class JdbcHelper {

    private JdbcHelper() {
    }

    /** Nullable DATETIME columns (e.g. paid_at) need setNull, not setTimestamp(null). */
    static void setDateTime(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** Reads the AUTO_INCREMENT id after an INSERT prepared with RETURN_GENERATED_KEYS. */
    static int getGeneratedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
            throw new SQLException("Insert did not return a generated id");
        }
    }
}
