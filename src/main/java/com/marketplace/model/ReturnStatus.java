package com.marketplace.model;

/** Kept separate from OrderStatus because a return has its own approval flow. */
public enum ReturnStatus {
    REQUESTED, APPROVED, REJECTED, REFUNDED
}
