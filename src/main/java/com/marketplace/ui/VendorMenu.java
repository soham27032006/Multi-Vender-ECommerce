package com.marketplace.ui;

import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.model.OrderItem;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.PaymentStatus;
import com.marketplace.model.Payout;
import com.marketplace.model.Product;
import com.marketplace.model.SubOrder;
import com.marketplace.model.Vendor;
import com.marketplace.service.OrderTrackingService;
import com.marketplace.util.TablePrinter;

import java.util.List;
import java.util.stream.Collectors;

public class VendorMenu extends Menu {

    /** Statuses a vendor may set by hand; RETURNED only comes from the return flow. */
    private static final OrderStatus[] VENDOR_STATUSES = {
            OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED
    };

    private final Vendor vendor;
    private final ProductDAO productDAO;
    private final PayoutDAO payoutDAO;
    private final OrderTrackingService trackingService;

    public VendorMenu(ConsoleInput in, Vendor vendor, ProductDAO productDAO, PayoutDAO payoutDAO,
                      OrderTrackingService trackingService) {
        super(in);
        this.vendor = vendor;
        this.productDAO = productDAO;
        this.payoutDAO = payoutDAO;
        this.trackingService = trackingService;
    }

    @Override
    public void show() {
        while (true) {
            header("VENDOR: " + vendor.getShopName() + " (#" + vendor.getId() + ")");
            System.out.println("  1. My products");
            System.out.println("  2. Add a product");
            System.out.println("  3. Restock / change price");
            System.out.println("  4. My sub-orders");
            System.out.println("  5. Update sub-order status");
            System.out.println("  6. My payouts");
            System.out.println("  0. Logout");
            int choice = in.readIntInRange("Choose: ", 0, 6);
            switch (choice) {
                case 1 -> runSafely(this::myProducts);
                case 2 -> runSafely(this::addProduct);
                case 3 -> runSafely(this::editProduct);
                case 4 -> runSafely(this::mySubOrders);
                case 5 -> runSafely(this::updateStatus);
                case 6 -> runSafely(this::myPayouts);
                default -> { return; }
            }
        }
    }

    private void myProducts() {
        CustomerMenu.printProducts(productDAO.findByVendor(vendor.getId()));
    }

    private void addProduct() {
        String name = in.readNonBlank("Name: ");
        String category = in.readNonBlank("Category: ");
        double price = in.readPositiveDouble("Price: ");
        int stock = in.readIntInRange("Stock: ", 0, 1_000_000);
        Product p = productDAO.save(new Product(0, vendor.getId(), name, category, price, stock));
        success("Product #" + p.getId() + " added.");
    }

    private void editProduct() {
        int productId = in.readPositiveInt("Product id: ");
        Product p = productDAO.findById(productId)
                .filter(prod -> prod.getVendorId() == vendor.getId())   // only own products
                .orElseThrow(() -> new InvalidInputException("You have no product #" + productId));
        System.out.printf("  Current: price %s, stock %d%n", money(p.getPrice()), p.getStock());
        p.setStock(p.getStock() + in.readIntInRange("Units to add (0 for none): ", 0, 1_000_000));
        if (in.confirm("Change price?")) {
            p.setPrice(in.readPositiveDouble("New price: "));
        }
        productDAO.update(p);
        success(String.format("Product #%d now %s, stock %d", p.getId(), money(p.getPrice()), p.getStock()));
    }

    private void mySubOrders() {
        List<SubOrder> subs = trackingService.getSubOrdersForVendor(vendor.getId());
        if (subs.isEmpty()) {
            System.out.println("  No sub-orders yet.");
            return;
        }
        for (SubOrder sub : subs) {
            System.out.printf("%n  Sub-order #%-4d (order #%d)  %-10s  subtotal %s  commission %s  you get %s%n",
                    sub.getId(), sub.getParentOrderId(), sub.getStatus(), money(sub.getSubtotal()),
                    money(sub.getCommission()), money(sub.getVendorEarning()));
            for (OrderItem item : sub.getItems()) {
                System.out.printf("      %-25s x%d%n", item.getProductName(), item.getQuantity());
            }
        }
    }

    private void updateStatus() {
        int subOrderId = in.readPositiveInt("Sub-order id: ");
        SubOrder sub = trackingService.getSubOrder(subOrderId);
        System.out.println("  Current status: " + sub.getStatus());
        System.out.println("New status:");
        OrderStatus next = in.readEnum("Choose: ", VENDOR_STATUSES);
        trackingService.updateStatus(vendor.getId(), subOrderId, next);
        success("Sub-order #" + subOrderId + " is now " + next);
    }

    private void myPayouts() {
        List<Payout> payouts = payoutDAO.findByVendor(vendor.getId());
        TablePrinter.printTable("Payouts for " + vendor.getShopName(),
                new String[]{"Payout", "Sub-order", "Gross", "Commission", "Net", "Status"},
                payouts,
                p -> List.of("#" + p.getId(), "#" + p.getSubOrderId(), money(p.getGrossAmount()),
                        money(p.getCommission()), money(p.getAmount()), p.getStatus().name()));

        List<Payout> paid = payouts.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .collect(Collectors.toList());
        List<Payout> pending = payouts.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .collect(Collectors.toList());
        System.out.printf("  Received: %s | Pending: %s%n",
                money(TablePrinter.totalAmount(paid)), money(TablePrinter.totalAmount(pending)));
    }
}
