package com.marketplace.exception;

import java.sql.SQLException;

/**
 * Wraps SQLException so service/UI code doesn't depend on JDBC types.
 * Unchecked because most DB errors can't be recovered from at the call site.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, SQLException cause) {
        super(message + ": " + cause.getMessage(), cause);
    }
}
