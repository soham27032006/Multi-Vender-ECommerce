package com.marketplace.model;

import com.marketplace.util.MoneyUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** What the customer sees and pays for once; split into one SubOrder per vendor. */
public class ParentOrder {

    private int id;
    private int customerId;
    private LocalDateTime orderDate;
    private double totalAmount;
    private final List<SubOrder> subOrders = new ArrayList<>();

    public ParentOrder(int id, int customerId, LocalDateTime orderDate, double totalAmount) {
        this.id = id;
        this.customerId = customerId;
        this.orderDate = orderDate;
        this.totalAmount = MoneyUtil.round(totalAmount);
    }

    public void addSubOrder(SubOrder subOrder) {
        subOrders.add(subOrder);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCustomerId() { return customerId; }
    public LocalDateTime getOrderDate() { return orderDate; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = MoneyUtil.round(totalAmount); }

    public List<SubOrder> getSubOrders() { return subOrders; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((ParentOrder) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Order #%d | customer #%d | %s | total Rs.%.2f%n",
                id, customerId, orderDate, totalAmount));
        for (SubOrder sub : subOrders) {
            sb.append(sub);
        }
        return sb.toString();
    }
}
