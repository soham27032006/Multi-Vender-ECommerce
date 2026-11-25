package com.marketplace.service;

import com.marketplace.dao.ProductDAO;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.exception.OutOfStockException;
import com.marketplace.model.CartItem;
import com.marketplace.model.Product;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds short-lived stock reservations while a checkout is running.
 *
 * RACE CONDITION this prevents:
 *   Stock of product #7 is 1. Customers A and B check out at the same time
 *   on two threads.
 *     A: reads stock = 1, "1 >= 1, OK"
 *     B: reads stock = 1, "1 >= 1, OK"      (A has not updated the DB yet)
 *     A: places order       B: places order  -> 2 units sold, only 1 existed.
 *   The bug is that "check" and "take" are two separate steps and another
 *   thread can run between them.
 *
 * FIX: reserve() is synchronized, so only one thread at a time can do
 *   check + reserve. B waits until A has reserved, then sees available = 0.
 *
 * synchronized only protects threads inside THIS program. Another app
 * using the same DB is protected by ProductDAO.decreaseStock
 * ("... WHERE stock >= ?"), which is the final safety net.
 */
public class InventoryService {

    private final ProductDAO productDAO;
    // productId -> units held by checkouts that have not finished yet.
    // Plain HashMap is safe here because every access is inside a synchronized method.
    private final Map<Integer, Integer> reserved = new HashMap<>();

    public InventoryService(ProductDAO productDAO) {
        this.productDAO = productDAO;
    }

    /**
     * All-or-nothing: checks every line first, reserves only if all pass,
     * so a failed checkout never leaves half its items reserved.
     */
    public synchronized void reserve(List<CartItem> items) throws OutOfStockException {
        for (CartItem item : items) {
            int productId = item.getProduct().getId();
            int available = availableStock(productId);
            if (item.getQuantity() > available) {
                throw new OutOfStockException(productId, item.getQuantity(), available);
            }
        }
        for (CartItem item : items) {
            reserved.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
    }

    /**
     * Commit and release under ONE lock. If they were separate, there would be a moment
     * after commit (DB stock already lower) but before release (units still reserved)
     * where the same units are subtracted twice, and another customer would be told
     * "out of stock" by mistake. Holding the lock means availableStock() sees either
     * "before commit + reserved" or "after commit + released", never the mix.
     * (Found by test 5 in ManualTestRunner.)
     */
    public synchronized void commitAndRelease(Connection conn, List<CartItem> items) throws SQLException {
        conn.commit();
        release(items);   // same thread already holds the lock, synchronized is re-entrant
    }

    /** Used when checkout fails (after rollback): stock in the DB never changed. */
    public synchronized void release(List<CartItem> items) {
        for (CartItem item : items) {
            int productId = item.getProduct().getId();
            int left = reserved.getOrDefault(productId, 0) - item.getQuantity();
            if (left > 0) {
                reserved.put(productId, left);
            } else {
                reserved.remove(productId);
            }
        }
    }

    /** Stock in DB minus what other running checkouts are holding. */
    public synchronized int availableStock(int productId) {
        Product product = productDAO.findById(productId)
                .orElseThrow(() -> new InvalidInputException("No product with id #" + productId));
        return product.getStock() - reserved.getOrDefault(productId, 0);
    }
}
