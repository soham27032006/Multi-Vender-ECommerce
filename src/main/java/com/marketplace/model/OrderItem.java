package com.marketplace.model;

import com.marketplace.util.MoneyUtil;

/**
 * A purchased line inside a SubOrder. Name and price are copied at order time
 * so later product edits don't change past orders.
 */
public class OrderItem {

    private int id;
    private int subOrderId;
    private int productId;
    private String productName;
    private int quantity;
    private double unitPrice;

    public OrderItem(int id, int subOrderId, int productId, String productName,
                     int quantity, double unitPrice) {
        this.id = id;
        this.subOrderId = subOrderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = MoneyUtil.round(unitPrice);
    }

    public double getLineTotal() {
        return MoneyUtil.round(unitPrice * quantity);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSubOrderId() { return subOrderId; }
    public void setSubOrderId(int subOrderId) { this.subOrderId = subOrderId; }

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }

    @Override
    public String toString() {
        return String.format("%-25s x%-3d @ Rs.%.2f = Rs.%.2f",
                productName, quantity, unitPrice, getLineTotal());
    }
}
