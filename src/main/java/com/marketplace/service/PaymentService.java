package com.marketplace.service;

import com.marketplace.exception.PaymentFailedException;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.Payment;
import com.marketplace.model.PaymentMode;
import com.marketplace.model.PaymentStatus;
import com.marketplace.model.Payout;
import com.marketplace.model.SubOrder;
import com.marketplace.util.MoneyUtil;

import java.util.Random;

/** Simulated payment gateway plus commission/payout maths. No real money moves. */
public class PaymentService {

    public static final double COMMISSION_RATE = 0.10;   // platform keeps 10% of each vendor's subtotal
    private static final int DEFAULT_FAILURE_PERCENT = 10;

    private final Random random = new Random();
    private int failurePercent = DEFAULT_FAILURE_PERCENT;

    /** Lets the demo force failures (100) or always succeed (0). */
    public void setFailurePercent(int failurePercent) {
        this.failurePercent = Math.max(0, Math.min(100, failurePercent));
    }

    public double calculateCommission(double subtotal) {
        return MoneyUtil.round(subtotal * COMMISSION_RATE);
    }

    /** One payment for the whole parent order, however many vendors it has. */
    public Payment createPayment(ParentOrder order, PaymentMode mode) {
        return new Payment(0, order.getId(), order.getTotalAmount(), mode, PaymentStatus.PENDING, null);
    }

    /**
     * Pretends to call a bank. About failurePercent% of calls fail, like a
     * declined card, so the rollback path can be shown in the viva.
     */
    public void processPayment(Payment payment) throws PaymentFailedException {
        if (random.nextInt(100) < failurePercent) {
            payment.setStatus(PaymentStatus.FAILED);
            throw new PaymentFailedException("Bank declined payment of Rs."
                    + String.format("%.2f", payment.getAmount()) + " via " + payment.getMode().getLabel());
        }
        payment.pay(); // marks SUCCESS and sets paidAt
    }

    /**
     * Vendor gets subtotal - commission. Starts PENDING: the platform holds
     * the money until the sub-order is delivered and the return window is over.
     */
    public Payout createPayout(SubOrder subOrder) {
        return new Payout(0, subOrder.getVendorId(), subOrder.getId(),
                subOrder.getSubtotal(), subOrder.getCommission(), PaymentStatus.PENDING, null);
    }
}
