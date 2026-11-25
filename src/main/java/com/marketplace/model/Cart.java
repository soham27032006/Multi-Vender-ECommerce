package com.marketplace.model;

import com.marketplace.util.MoneyUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory cart; it becomes a ParentOrder at checkout. */
public class Cart {

    private final int customerId;
    // Keyed by product id so adding the same product twice merges quantities.
    // LinkedHashMap keeps the order items were added for display.
    private final Map<Integer, CartItem> items = new LinkedHashMap<>();

    public Cart(int customerId) {
        this.customerId = customerId;
    }

    public void addItem(Product product, int quantity) {
        CartItem existing = items.get(product.getId());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            items.put(product.getId(), new CartItem(product, quantity));
        }
    }

    /** Sets an absolute quantity (addItem adds on top). Returns false if product not in cart. */
    public boolean updateQuantity(int productId, int quantity) {
        CartItem item = items.get(productId);
        if (item == null) {
            return false;
        }
        item.setQuantity(quantity);
        return true;
    }

    /** 0 when the product is not in the cart. */
    public int getQuantity(int productId) {
        CartItem item = items.get(productId);
        return item == null ? 0 : item.getQuantity();
    }

    public boolean removeItem(int productId) {
        return items.remove(productId) != null;
    }

    /** Returns a copy so callers can't modify the cart behind its back. */
    public List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    public double getTotal() {
        double total = 0;
        for (CartItem item : items.values()) {
            total += item.getLineTotal();
        }
        return MoneyUtil.round(total);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
    }

    public int getCustomerId() { return customerId; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Cart of customer #").append(customerId).append('\n');
        for (CartItem item : items.values()) {
            sb.append("  ").append(item).append('\n');
        }
        sb.append(String.format("  TOTAL: Rs.%.2f", getTotal()));
        return sb.toString();
    }
}
