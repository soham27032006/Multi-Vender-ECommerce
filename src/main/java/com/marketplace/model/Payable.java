package com.marketplace.model;

import com.marketplace.exception.PaymentFailedException;

/**
 * Common behaviour for money movement. Payment (customer -> platform) and
 * Payout (platform -> vendor) both implement it, so the service can treat them alike.
 */
public interface Payable {

    double getAmount();

    PaymentStatus getStatus();

    /** Simulated transfer; throws a checked exception so callers must handle failure. */
    void pay() throws PaymentFailedException;
}
