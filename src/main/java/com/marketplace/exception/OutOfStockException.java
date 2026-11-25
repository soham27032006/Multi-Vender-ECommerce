package com.marketplace.exception;

/** Checked: running out of stock is an expected business case the caller must handle. */
public class OutOfStockException extends Exception {

    private final int productId;
    private final int available;

    public OutOfStockException(int productId, int requested, int available) {
        super("Product #" + productId + ": requested " + requested + ", only " + available + " in stock");
        this.productId = productId;
        this.available = available;
    }

    public int getProductId() { return productId; }
    public int getAvailable() { return available; }
}
