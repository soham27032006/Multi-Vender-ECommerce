package com.marketplace.model;

import com.marketplace.util.MoneyUtil;

import java.util.Objects;

/** Natural ordering is by price, so Collections.sort / TreeSet list cheapest first. */
public class Product implements Comparable<Product> {

    private int id;
    private int vendorId;
    private String name;
    private String category;
    private double price;
    private int stock;

    public Product(int id, int vendorId, String name, String category, double price, int stock) {
        this.id = id;
        this.vendorId = vendorId;
        this.name = name;
        this.category = category;
        this.price = MoneyUtil.round(price);
        this.stock = stock;
    }

    @Override
    public int compareTo(Product other) {
        int byPrice = Double.compare(this.price, other.price);
        // Tie-break on id so different products with same price are not treated as equal.
        return byPrice != 0 ? byPrice : Integer.compare(this.id, other.id);
    }

    public boolean hasStock(int qty) {
        return stock >= qty;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getVendorId() { return vendorId; }
    public void setVendorId(int vendorId) { this.vendorId = vendorId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = MoneyUtil.round(price); }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((Product) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("#%-4d %-25s %-12s Rs.%10.2f  stock:%-4d vendor:%d",
                id, name, category, price, stock, vendorId);
    }
}
