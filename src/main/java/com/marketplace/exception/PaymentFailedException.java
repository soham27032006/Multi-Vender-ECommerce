package com.marketplace.exception;

/** Checked: a failed payment must be handled (rollback the order), never ignored. */
public class PaymentFailedException extends Exception {

    public PaymentFailedException(String message) {
        super(message);
    }

    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
