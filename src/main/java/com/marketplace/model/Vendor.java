package com.marketplace.model;

public class Vendor extends User {

    private String shopName;
    /** Platform's cut as a fraction, e.g. 0.10 = 10%. Per vendor so it can be negotiated. */
    private double commissionRate;

    public Vendor(int id, String name, String email, String phone,
                  String shopName, double commissionRate) {
        super(id, name, email, phone);
        this.shopName = shopName;
        this.commissionRate = commissionRate;
    }

    @Override
    public String getRole() {
        return "VENDOR";
    }

    @Override
    public String display() {
        return super.display() + String.format(" | Shop: %s | Commission: %.0f%%",
                shopName, commissionRate * 100);
    }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public double getCommissionRate() { return commissionRate; }
    public void setCommissionRate(double commissionRate) { this.commissionRate = commissionRate; }
}
