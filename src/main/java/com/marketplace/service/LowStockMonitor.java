package com.marketplace.service;

import com.marketplace.dao.ProductDAO;
import com.marketplace.model.Product;
import com.marketplace.util.NamedThreadFactory;
import com.marketplace.util.ThreadLog;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Background check every N seconds for products that are about to run out. */
public class LowStockMonitor {

    private final ProductDAO productDAO;
    private final int threshold;
    private ScheduledExecutorService scheduler;

    public LowStockMonitor(ProductDAO productDAO, int threshold) {
        this.productDAO = productDAO;
        this.threshold = threshold;
    }

    public synchronized void start(int periodSeconds) {
        if (scheduler != null) {
            return; // already running
        }
        // Daemon thread: even if stop() is never called, it won't keep the JVM alive.
        scheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("low-stock-monitor", true));
        scheduler.scheduleAtFixedRate(this::checkStock, periodSeconds, periodSeconds, TimeUnit.SECONDS);
        ThreadLog.log("low-stock monitor started, every " + periodSeconds + "s, threshold " + threshold);
    }

    /**
     * Must catch everything: if a scheduled task throws, the executor silently
     * cancels all future runs. A DB hiccup should skip one check, not kill the monitor.
     */
    private void checkStock() {
        try {
            List<Product> low = productDAO.findAll().stream()
                    .filter(p -> p.getStock() <= threshold)
                    .sorted() // Product is Comparable by price
                    .collect(Collectors.toList());
            if (!low.isEmpty()) {
                ThreadLog.log("LOW STOCK ALERT: " + low.size() + " product(s) at or below " + threshold);
                for (Product p : low) {
                    System.out.println("      " + p);
                }
            }
        } catch (RuntimeException e) {
            ThreadLog.log("low-stock check skipped: " + e.getMessage());
        }
    }

    /** Called on app exit. Waits briefly so a running check can finish cleanly. */
    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        scheduler = null;
        ThreadLog.log("low-stock monitor stopped");
    }
}
