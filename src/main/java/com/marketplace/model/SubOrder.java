package com.marketplace.model;

import com.marketplace.util.MoneyUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The part of a ParentOrder fulfilled by one vendor; tracked independently. */
public class SubOrder {

    private int id;
    private int parentOrderId;
    private int vendorId;
    private OrderStatus status;
    private double subtotal;
    private double commission;
    private LocalDateTime updatedAt;
    private final List<OrderItem> items = new ArrayList<>();

    public SubOrder(int id, int parentOrderId, int vendorId, OrderStatus status,
                    double subtotal, double commission, LocalDateTime updatedAt) {
        this.id = id;
        this.parentOrderId = parentOrderId;
        this.vendorId = vendorId;
        this.status = status;
        this.subtotal = MoneyUtil.round(subtotal);
        this.commission = MoneyUtil.round(commission);
        this.updatedAt = updatedAt;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    /** What the vendor receives after the platform keeps its commission. */
    public double getVendorEarning() {
        return MoneyUtil.round(subtotal - commission);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getParentOrderId() { return parentOrderId; }
    public void setParentOrderId(int parentOrderId) { this.parentOrderId = parentOrderId; }

    public int getVendorId() { return vendorId; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = MoneyUtil.round(subtotal); }

    public double getCommission() { return commission; }
    public void setCommission(double commission) { this.commission = MoneyUtil.round(commission); }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<OrderItem> getItems() { return items; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((SubOrder) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  SubOrder #%d | vendor #%d | %-9s | subtotal Rs.%.2f | commission Rs.%.2f%n",
                id, vendorId, status, subtotal, commission));
        for (OrderItem item : items) {
            sb.append("      ").append(item).append('\n');
        }
        return sb.toString();
    }
}
