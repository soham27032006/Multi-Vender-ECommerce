package com.marketplace.service;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.dao.UserDAO;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.Payout;
import com.marketplace.model.Product;
import com.marketplace.model.SubOrder;
import com.marketplace.model.Vendor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSV import/export with BufferedReader/BufferedWriter.
 * Simple format: values may not contain commas on import (no quoted fields).
 */
public class CsvService {

    public static final String PRODUCTS_FILE = "data/products.csv";
    public static final String ORDERS_FILE   = "data/orders_export.csv";
    public static final String PAYOUTS_FILE  = "data/payouts_export.csv";

    private static final int PRODUCT_COLUMNS = 5; // vendor_id,name,category,price,stock

    private final ProductDAO productDAO;
    private final UserDAO userDAO;
    private final OrderDAO orderDAO;
    private final PayoutDAO payoutDAO;

    public CsvService(ProductDAO productDAO, UserDAO userDAO, OrderDAO orderDAO, PayoutDAO payoutDAO) {
        this.productDAO = productDAO;
        this.userDAO = userDAO;
        this.orderDAO = orderDAO;
        this.payoutDAO = payoutDAO;
    }

    // ---------------- import ----------------

    /**
     * Reads line by line, so even a huge file uses little memory.
     * A bad row is reported and skipped; it does not stop the whole import.
     * @return number of products saved
     */
    public int importProducts(String path) throws IOException {
        // Load vendor ids once instead of one DB query per line.
        Set<Integer> vendorIds = userDAO.findAllVendors().stream()
                .map(Vendor::getId)
                .collect(Collectors.toSet());

        int imported = 0;
        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path, StandardCharsets.UTF_8))) {
            String line = reader.readLine();   // first line is the header
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) {
                    continue;                  // allow blank lines
                }
                try {
                    Product product = parseProduct(line, vendorIds);
                    productDAO.save(product);
                    imported++;
                } catch (InvalidInputException e) {
                    skipped++;
                    System.out.println("  Line " + lineNo + " skipped: " + e.getMessage() + "  -> \"" + line + "\"");
                }
            }
        }
        System.out.println("Import finished: " + imported + " imported, " + skipped + " skipped.");
        return imported;
    }

    /** Validates one CSV line; throws InvalidInputException with the reason if bad. */
    private Product parseProduct(String line, Set<Integer> vendorIds) {
        String[] f = line.split(",", -1);   // -1 keeps empty trailing fields, so "a,b," has 3 parts
        if (f.length != PRODUCT_COLUMNS) {
            throw new InvalidInputException("expected " + PRODUCT_COLUMNS + " values, found " + f.length);
        }

        int vendorId = parseInt(f[0], "vendor_id");
        if (!vendorIds.contains(vendorId)) {
            throw new InvalidInputException("vendor #" + vendorId + " does not exist");
        }
        String name = f[1].trim();
        String category = f[2].trim();
        if (name.isEmpty() || category.isEmpty()) {
            throw new InvalidInputException("name and category are required");
        }
        double price;
        try {
            price = Double.parseDouble(f[3].trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException("price '" + f[3].trim() + "' is not a number");
        }
        if (price <= 0) {
            throw new InvalidInputException("price must be greater than 0");
        }
        int stock = parseInt(f[4], "stock");
        if (stock < 0) {
            throw new InvalidInputException("stock cannot be negative");
        }
        return new Product(0, vendorId, name, category, price, stock);
    }

    private int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field + " '" + value.trim() + "' is not a whole number");
        }
    }

    // ---------------- export ----------------

    /** One row per sub-order, since status and money are tracked at that level. */
    public int exportOrders(String path) throws IOException {
        List<ParentOrder> orders = orderDAO.findAll();
        int rows = 0;
        try (BufferedWriter writer = openWriter(path)) {
            writer.write("order_id,order_date,customer_id,order_total,sub_order_id,vendor_id,status,subtotal,commission");
            writer.newLine();
            for (ParentOrder order : orders) {
                for (SubOrder sub : order.getSubOrders()) {
                    writer.write(String.join(",",
                            String.valueOf(order.getId()),
                            String.valueOf(order.getOrderDate()),
                            String.valueOf(order.getCustomerId()),
                            money(order.getTotalAmount()),
                            String.valueOf(sub.getId()),
                            String.valueOf(sub.getVendorId()),
                            sub.getStatus().name(),
                            money(sub.getSubtotal()),
                            money(sub.getCommission())));
                    writer.newLine();
                    rows++;
                }
            }
        }   // try-with-resources flushes and closes the file, even on error
        System.out.println("Exported " + rows + " sub-order row(s) to " + path);
        return rows;
    }

    public int exportPayouts(String path) throws IOException {
        List<Payout> payouts = payoutDAO.findAll();
        try (BufferedWriter writer = openWriter(path)) {
            writer.write("payout_id,vendor_id,sub_order_id,gross,commission,net,status,paid_at");
            writer.newLine();
            for (Payout p : payouts) {
                writer.write(String.join(",",
                        String.valueOf(p.getId()),
                        String.valueOf(p.getVendorId()),
                        String.valueOf(p.getSubOrderId()),
                        money(p.getGrossAmount()),
                        money(p.getCommission()),
                        money(p.getAmount()),
                        p.getStatus().name(),
                        p.getPaidAt() == null ? "" : p.getPaidAt().toString()));
                writer.newLine();
            }
        }
        System.out.println("Exported " + payouts.size() + " payout row(s) to " + path);
        return payouts.size();
    }

    /** Creates the data/ folder if missing, then opens the file (overwrites old export). */
    private BufferedWriter openWriter(String path) throws IOException {
        File parent = new File(path).getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }
        return new BufferedWriter(new FileWriter(path, StandardCharsets.UTF_8));
    }

    /** Plain number with 2 decimals, no "Rs." so spreadsheets read it as a number. */
    private static String money(double value) {
        return String.format("%.2f", value);
    }
}
