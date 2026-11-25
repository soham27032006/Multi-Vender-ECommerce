package com.marketplace.model;

/** Enum with a field + constructor so the menu can show friendly names. */
public enum PaymentMode {
    UPI("UPI"),
    CARD("Credit/Debit Card"),
    NET_BANKING("Net Banking"),
    COD("Cash on Delivery");

    private final String label;

    PaymentMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
