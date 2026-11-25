package com.marketplace.model;

/** Lifecycle of a vendor sub-order. */
public enum OrderStatus {
    PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, RETURNED;

    /** Only forward moves are legal, so tracking can't jump back (e.g. DELIVERED -> SHIPPED). */
    public boolean canMoveTo(OrderStatus next) {
        switch (this) {
            case PLACED:    return next == CONFIRMED || next == CANCELLED;
            case CONFIRMED: return next == SHIPPED || next == CANCELLED;
            case SHIPPED:   return next == DELIVERED;
            case DELIVERED: return next == RETURNED;
            default:        return false; // CANCELLED and RETURNED are final
        }
    }
}
