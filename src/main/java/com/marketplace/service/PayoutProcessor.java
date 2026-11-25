package com.marketplace.service;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.exception.PaymentFailedException;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.PaymentStatus;
import com.marketplace.model.Payout;
import com.marketplace.util.ThreadLog;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

/**
 * Producer-consumer with wait/notifyAll.
 * Producer thread: takes payouts that are ready (sub-order DELIVERED) and puts them in a queue.
 * Consumer thread: the "vendor payout" thread takes each one and pays it.
 * The queue is bounded, so a fast producer has to wait for the consumer.
 */
public class PayoutProcessor {

    /** Bounded queue guarded by its own lock (the 'this' monitor of this object). */
    static class BoundedPayoutQueue {
        private final Queue<Payout> queue = new LinkedList<>();
        private final int capacity;
        private boolean closed = false;

        BoundedPayoutQueue(int capacity) {
            this.capacity = capacity;
        }

        /** Blocks while full. 'while' not 'if': after waking up the queue may be full again. */
        synchronized void put(Payout payout) throws InterruptedException {
            while (queue.size() == capacity) {
                ThreadLog.log("queue FULL (" + capacity + "), producer waits");
                wait();             // releases the lock and sleeps until notifyAll
            }
            queue.add(payout);
            ThreadLog.log("produced payout #" + payout.getId() + " (queue size " + queue.size() + ")");
            notifyAll();            // wake the consumer: there is work now
        }

        /** Blocks while empty. Returns null once closed and drained, so the consumer can stop. */
        synchronized Payout take() throws InterruptedException {
            while (queue.isEmpty() && !closed) {
                wait();
            }
            if (queue.isEmpty()) {
                return null;
            }
            Payout payout = queue.poll();
            notifyAll();            // wake the producer: there is space now
            return payout;
        }

        /** No more payouts will come; wake anyone waiting so they can see it. */
        synchronized void close() {
            closed = true;
            notifyAll();
        }
    }

    private final PayoutDAO payoutDAO;   // null in the in-memory demo: then nothing is saved
    private final OrderDAO orderDAO;
    private final int capacity;

    public PayoutProcessor(PayoutDAO payoutDAO, OrderDAO orderDAO, int capacity) {
        this.payoutDAO = payoutDAO;
        this.orderDAO = orderDAO;
        this.capacity = capacity;
    }

    /** Payouts that can be paid now: still PENDING and their sub-order was DELIVERED. */
    public List<Payout> findReadyPayouts() {
        return payoutDAO.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .filter(p -> orderDAO.findSubOrderById(p.getSubOrderId())
                        .map(sub -> sub.getStatus() == OrderStatus.DELIVERED)
                        .orElse(false))
                .collect(Collectors.toList());
    }

    /** Pays every ready payout from the DB using one producer and one consumer thread. */
    public void settleReadyPayouts() throws InterruptedException {
        process(findReadyPayouts());
    }

    /** Runs producer and consumer, then waits for both to finish. */
    public void process(List<Payout> payouts) throws InterruptedException {
        // New queue every run: a queue closed by the last run must not be reused.
        BoundedPayoutQueue queue = new BoundedPayoutQueue(capacity);
        Thread consumer = new Thread(() -> consumeLoop(queue), "vendor-payout-thread");
        Thread producer = new Thread(() -> produceAll(queue, payouts), "order-payout-producer");

        consumer.start();
        Thread.sleep(100);                 // give the consumer time to block on the empty queue
        ThreadLog.logState(consumer);      // expected: WAITING (inside wait())

        producer.start();
        producer.join();                   // main waits until all payouts are queued
        queue.close();                     // tell the consumer no more will come
        consumer.join();

        ThreadLog.logState(producer);      // TERMINATED
        ThreadLog.logState(consumer);      // TERMINATED
    }

    private void produceAll(BoundedPayoutQueue queue, List<Payout> payouts) {
        try {
            for (Payout p : payouts) {
                queue.put(p);
            }
            ThreadLog.log("producer finished, " + payouts.size() + " payout(s) queued");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void consumeLoop(BoundedPayoutQueue queue) {
        try {
            Payout payout;
            while ((payout = queue.take()) != null) {   // null = closed and empty
                pay(payout);
            }
            ThreadLog.log("queue closed and empty, consumer stops");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void pay(Payout payout) throws InterruptedException {
        try {
            Thread.sleep(200);             // simulated bank transfer, slower than the producer
            payout.pay();
            if (payoutDAO != null) {
                payoutDAO.update(payout);
            }
            ThreadLog.log(String.format("PAID vendor #%d Rs.%.2f (payout #%d)",
                    payout.getVendorId(), payout.getAmount(), payout.getId()));
        } catch (PaymentFailedException e) {
            ThreadLog.log("payout #" + payout.getId() + " failed: " + e.getMessage());
        }
    }
}
