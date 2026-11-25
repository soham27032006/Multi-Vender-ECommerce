package com.marketplace.model;

import com.marketplace.exception.PaymentFailedException;
import com.marketplace.util.MoneyUtil;

import java.time.LocalDateTime;

/** One payment per ParentOrder, even when several vendors are involved. */
public class Payment implements Payable {

    private int id;
    private int parentOrderId;
    private double amount;
    private PaymentMode mode;
    private PaymentStatus status;
    private LocalDateTime paidAt;

    public Payment(int id, int parentOrderId, double amount, PaymentMode mode,
                   PaymentStatus status, LocalDateTime paidAt) {
        this.id = id;
        this.parentOrderId = parentOrderId;
        this.amount = MoneyUtil.round(amount);
        this.mode = mode;
        this.status = status;
        this.paidAt = paidAt;
    }

    /** Simulated gateway: rejects invalid amounts so the failure path can be demoed. */
    @Override
    public void pay() throws PaymentFailedException {
        if (status == PaymentStatus.SUCCESS) {
            throw new PaymentFailedException("Payment #" + id + " is already completed");
        }
        if (amount <= 0) {
            status = PaymentStatus.FAILED;
            throw new PaymentFailedException("Invalid payment amount: " + amount);
        }
        status = PaymentStatus.SUCCESS;
        paidAt = LocalDateTime.now();
    }

    @Override
    public double getAmount() { return amount; }

    @Override
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getParentOrderId() { return parentOrderId; }
    public PaymentMode getMode() { return mode; }
    public LocalDateTime getPaidAt() { return paidAt; }

    @Override
    public String toString() {
        return String.format("Payment #%d | order #%d | Rs.%.2f | %s | %s | %s",
                id, parentOrderId, amount, mode.getLabel(), status, paidAt);
    }
}
