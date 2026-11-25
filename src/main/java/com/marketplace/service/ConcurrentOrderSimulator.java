package com.marketplace.service;

import com.marketplace.exception.OutOfStockException;
import com.marketplace.exception.PaymentFailedException;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.PaymentMode;
import com.marketplace.util.NamedThreadFactory;
import com.marketplace.util.ThreadLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Many customers try to buy the same low-stock product at the same moment.
 * Demo 1 (in memory): unsafe vs synchronized stock, shows the race clearly.
 * Demo 2 (real DB):   full checkout through OrderService on several threads.
 */
public class ConcurrentOrderSimulator {

    private static final int POOL_SIZE = 5;

    /** Shared stock for the in-memory demo. */
    static class SharedStock {
        private int stock;

        SharedStock(int stock) {
            this.stock = stock;
        }

        /**
         * NOT thread-safe: between the check and the decrement another thread can
         * also pass the check. The sleep makes that gap wide enough to see every time.
         */
        boolean buyUnsafe(int qty) throws InterruptedException {
            if (stock >= qty) {
                Thread.sleep(50);          // e.g. time taken to call the payment gateway
                stock -= qty;
                return true;
            }
            return false;
        }

        /** Same code, but synchronized: check + decrement happen as one step per thread. */
        synchronized boolean buySafe(int qty) throws InterruptedException {
            return buyUnsafe(qty);
        }

        synchronized int getStock() { return stock; }
    }

    // ---------------- Demo 1: in-memory race ----------------

    public void runRaceDemo(int initialStock, int customers) {
        System.out.println("\n=== RACE DEMO 1: NOT synchronized ===");
        runInMemory(initialStock, customers, false);

        System.out.println("\n=== RACE DEMO 2: synchronized ===");
        runInMemory(initialStock, customers, true);
    }

    private void runInMemory(int initialStock, int customers, boolean safe) {
        SharedStock product = new SharedStock(initialStock);
        NamedThreadFactory factory = new NamedThreadFactory(safe ? "safe-buyer" : "unsafe-buyer", false);
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE, factory);

        List<Future<String>> futures = new ArrayList<>();
        for (int i = 1; i <= customers; i++) {
            final int customerNo = i;   // lambdas need an effectively final variable
            Callable<String> task = () -> {
                boolean ok = safe ? product.buySafe(1) : product.buyUnsafe(1);
                String result = "customer " + customerNo + (ok ? " BOUGHT 1 unit" : " -> out of stock");
                ThreadLog.log(result);
                return result;
            };
            futures.add(pool.submit(task));
        }

        int success = collectResults(futures);
        shutdownAndWait(pool, factory);

        System.out.printf("Initial stock: %d | successful buys: %d | final stock: %d%n",
                initialStock, success, product.getStock());
        // 'success' counts confirmations given to customers, which is what matters to them.
        // The final stock number itself may also be wrong: "stock -= qty" is read-modify-write,
        // so two threads can write over each other (lost update) and hide the oversell.
        if (success > initialStock || product.getStock() < 0) {
            System.out.println(">> RACE CONDITION: " + success + " customers were told they bought, but only "
                    + initialStock + " units existed. Oversold by " + (success - initialStock) + "!");
        } else {
            System.out.println(">> OK: sold exactly what was in stock, never below 0.");
        }
    }

    // ---------------- Demo 2: real checkout against MySQL ----------------

    /**
     * Each customer clears their cart, adds 'qty' of the product and checks out,
     * all at once. InventoryService (synchronized) + "WHERE stock >= ?" keep the
     * DB stock from going negative. Customers must already exist in the DB.
     */
    public void runDbCheckoutDemo(CartService cartService, OrderService orderService,
                                  InventoryService inventoryService,
                                  int productId, List<Integer> customerIds, int qty) {
        System.out.println("\n=== DB CHECKOUT DEMO: " + customerIds.size()
                + " customers buy product #" + productId + " at once ===");
        int before = inventoryService.availableStock(productId);
        System.out.println("Stock before: " + before);

        NamedThreadFactory factory = new NamedThreadFactory("checkout", false);
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE, factory);
        List<Future<String>> futures = new ArrayList<>();

        for (int customerId : customerIds) {
            futures.add(pool.submit(() -> buyThroughCheckout(cartService, orderService,
                    customerId, productId, qty)));
        }

        int success = collectResults(futures);
        shutdownAndWait(pool, factory);

        int after = inventoryService.availableStock(productId);
        System.out.printf("Stock before: %d | successful orders: %d | stock after: %d%n",
                before, success, after);
        System.out.println(after >= 0 ? ">> OK: stock never went negative." : ">> BUG: negative stock!");
    }

    /** Runs on a pool thread. Expected failures become a result string, not a crash. */
    private String buyThroughCheckout(CartService cartService, OrderService orderService,
                                      int customerId, int productId, int qty) {
        String result;
        try {
            cartService.clearCart(customerId);
            cartService.addItem(customerId, productId, qty);
            ParentOrder order = orderService.checkout(customerId, PaymentMode.UPI);
            result = "customer #" + customerId + " BOUGHT, order #" + order.getId();
        } catch (OutOfStockException e) {
            result = "customer #" + customerId + " -> out of stock (" + e.getMessage() + ")";
        } catch (PaymentFailedException e) {
            result = "customer #" + customerId + " -> payment failed, rolled back";
        } catch (RuntimeException e) {
            result = "customer #" + customerId + " -> error: " + e.getMessage();
        }
        ThreadLog.log(result);
        return result;
    }

    // ---------------- shared helpers ----------------

    /** Future.get() blocks until that task is done, so this waits for every customer. */
    private int collectResults(List<Future<String>> futures) {
        int success = 0;
        for (Future<String> f : futures) {
            try {
                if (f.get().contains("BOUGHT")) {
                    success++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // keep the interrupt flag for the caller
                break;
            } catch (ExecutionException e) {
                System.out.println("Task crashed: " + e.getCause());
            }
        }
        return success;
    }

    /** shutdown = accept no new tasks; awaitTermination = wait for running ones to finish. */
    private void shutdownAndWait(ExecutorService pool, NamedThreadFactory factory) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // force-stop tasks that are stuck
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (Thread t : factory.getThreads()) {
            ThreadLog.logState(t);   // all should now be TERMINATED
        }
    }
}
