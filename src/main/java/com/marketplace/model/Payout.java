package com.marketplace.model;

import com.marketplace.exception.PaymentFailedException;
import com.marketplace.util.MoneyUtil;

import java.time.LocalDateTime;

/** Money the platform sends a vendor for one SubOrder, after commission. */
public class Payout implements Payable {

    private int id;
    private int vendorId;
    private int subOrderId;
    private double grossAmount;
    private double commission;
    private PaymentStatus status;
    private LocalDateTime paidAt;

    public Payout(int id, int vendorId, int subOrderId, double grossAmount,
                  double commission, PaymentStatus status, LocalDateTime paidAt) {
        this.id = id;
        this.vendorId = vendorId;
        this.subOrderId = subOrderId;
        this.grossAmount = MoneyUtil.round(grossAmount);
        this.commission = MoneyUtil.round(commission);
        this.status = status;
        this.paidAt = paidAt;
    }

    /** Net is derived, not stored separately, so it can never disagree with gross - commission. */
    @Override
    public double getAmount() {
        return MoneyUtil.round(grossAmount - commission);
    }

    @Override
    public void pay() throws PaymentFailedException {
        if (status == PaymentStatus.SUCCESS) {
            throw new PaymentFailedException("Payout #" + id + " already settled");
        }
        if (getAmount() < 0) {
            status = PaymentStatus.FAILED;
            throw new PaymentFailedException("Commission exceeds gross for payout #" + id);
        }
        status = PaymentStatus.SUCCESS;
        paidAt = LocalDateTime.now();
    }

    @Override
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getVendorId() { return vendorId; }
    public int getSubOrderId() { return subOrderId; }
    public double getGrossAmount() { return grossAmount; }
    public double getCommission() { return commission; }
    public LocalDateTime getPaidAt() { return paidAt; }

    @Override
    public String toString() {
        return String.format("Payout #%d | vendor #%d | sub-order #%d | gross Rs.%.2f | commission Rs.%.2f | net Rs.%.2f | %s",
                id, vendorId, subOrderId, grossAmount, commission, getAmount(), status);
    }
}
