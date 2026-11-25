package com.marketplace.exception;

/** Unchecked so lookup methods stay clean; the UI catches it and shows a message. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(int orderId) {
        super("Order not found: #" + orderId);
    }

    public OrderNotFoundException(String message) {
        super(message);
    }
}
