package com.marketplace.service;

import com.marketplace.dao.ProductDAO;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.exception.OutOfStockException;
import com.marketplace.model.Cart;
import com.marketplace.model.Product;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps one in-memory Cart per customer. Each Cart stores its lines in a
 * Map<Integer, CartItem> keyed by product id (see Cart).
 * Carts are not saved to the DB: they only become real at checkout.
 */
public class CartService {

    private final ProductDAO productDAO;
    private final Map<Integer, Cart> carts = new HashMap<>(); // customerId -> cart

    public CartService(ProductDAO productDAO) {
        this.productDAO = productDAO;
    }

    /**
     * Creates an empty cart the first time a customer uses it.
     * synchronized because the concurrent checkout demo calls this from many
     * threads, and a plain HashMap can corrupt itself under concurrent writes.
     */
    public synchronized Cart getCart(int customerId) {
        return carts.computeIfAbsent(customerId, Cart::new);
    }

    /**
     * Stock is checked against the total quantity in the cart, so adding 3 + 3
     * of a product with stock 5 fails on the second add, not only at checkout.
     */
    public void addItem(int customerId, int productId, int quantity) throws OutOfStockException {
        requirePositive(quantity);
        Product product = findProduct(productId);
        Cart cart = getCart(customerId);

        int newTotal = cart.getQuantity(productId) + quantity;
        if (!product.hasStock(newTotal)) {
            throw new OutOfStockException(productId, newTotal, product.getStock());
        }
        cart.addItem(product, quantity);
    }

    public void updateQuantity(int customerId, int productId, int quantity) throws OutOfStockException {
        requirePositive(quantity);
        Product product = findProduct(productId);
        if (!product.hasStock(quantity)) {
            throw new OutOfStockException(productId, quantity, product.getStock());
        }
        if (!getCart(customerId).updateQuantity(productId, quantity)) {
            throw new InvalidInputException("Product #" + productId + " is not in the cart");
        }
    }

    public void removeItem(int customerId, int productId) {
        if (!getCart(customerId).removeItem(productId)) {
            throw new InvalidInputException("Product #" + productId + " is not in the cart");
        }
    }

    public void clearCart(int customerId) {
        getCart(customerId).clear();
    }

    private Product findProduct(int productId) {
        return productDAO.findById(productId)
                .orElseThrow(() -> new InvalidInputException("No product with id #" + productId));
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InvalidInputException("Quantity must be greater than 0");
        }
    }
}
