package com.marketplace.model;

import com.marketplace.util.MoneyUtil;

/** One line in the cart: a product and how many. */
public class CartItem {

    private final Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public double getLineTotal() {
        return MoneyUtil.round(product.getPrice() * quantity);
    }

    public Product getProduct() { return product; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return String.format("%-25s x%-3d @ Rs.%.2f = Rs.%.2f",
                product.getName(), quantity, product.getPrice(), getLineTotal());
    }
}
