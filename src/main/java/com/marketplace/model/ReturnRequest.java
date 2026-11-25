package com.marketplace.model;

import com.marketplace.util.MoneyUtil;

import java.time.LocalDateTime;

/** Returns are per SubOrder, so one vendor's refund doesn't touch other vendors. */
public class ReturnRequest {

    private int id;
    private int subOrderId;
    private String reason;
    private double refundAmount;
    private ReturnStatus status;
    private LocalDateTime requestedAt;

    public ReturnRequest(int id, int subOrderId, String reason, double refundAmount,
                         ReturnStatus status, LocalDateTime requestedAt) {
        this.id = id;
        this.subOrderId = subOrderId;
        this.reason = reason;
        this.refundAmount = MoneyUtil.round(refundAmount);
        this.status = status;
        this.requestedAt = requestedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSubOrderId() { return subOrderId; }
    public String getReason() { return reason; }
    public double getRefundAmount() { return refundAmount; }

    public ReturnStatus getStatus() { return status; }
    public void setStatus(ReturnStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }

    @Override
    public String toString() {
        return String.format("Return #%d | sub-order #%d | Rs.%.2f | %s | reason: %s",
                id, subOrderId, refundAmount, status, reason);
    }
}
