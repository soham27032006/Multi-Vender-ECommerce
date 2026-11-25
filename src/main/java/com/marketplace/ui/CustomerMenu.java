package com.marketplace.ui;

import com.marketplace.dao.ProductDAO;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.model.Cart;
import com.marketplace.model.CartItem;
import com.marketplace.model.Customer;
import com.marketplace.model.OrderItem;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.PaymentMode;
import com.marketplace.model.Product;
import com.marketplace.model.ReturnRequest;
import com.marketplace.model.SubOrder;
import com.marketplace.service.CartService;
import com.marketplace.service.OrderService;
import com.marketplace.service.OrderTrackingService;
import com.marketplace.service.ReturnService;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerMenu extends Menu {

    private final Customer customer;
    private final ProductDAO productDAO;
    private final CartService cartService;
    private final OrderService orderService;
    private final OrderTrackingService trackingService;
    private final ReturnService returnService;

    public CustomerMenu(ConsoleInput in, Customer customer, ProductDAO productDAO, CartService cartService,
                        OrderService orderService, OrderTrackingService trackingService,
                        ReturnService returnService) {
        super(in);
        this.customer = customer;
        this.productDAO = productDAO;
        this.cartService = cartService;
        this.orderService = orderService;
        this.trackingService = trackingService;
        this.returnService = returnService;
    }

    @Override
    public void show() {
        while (true) {
            header("CUSTOMER: " + customer.getName() + " (#" + customer.getId() + ")");
            System.out.println("  1. Browse products");
            System.out.println("  2. Add to cart");
            System.out.println("  3. View cart");
            System.out.println("  4. Change quantity in cart");
            System.out.println("  5. Remove from cart");
            System.out.println("  6. Checkout");
            System.out.println("  7. Track my orders");
            System.out.println("  8. Request a return");
            System.out.println("  0. Logout");
            int choice = in.readIntInRange("Choose: ", 0, 8);
            switch (choice) {
                case 1 -> runSafely(this::browse);
                case 2 -> runSafely(this::addToCart);
                case 3 -> runSafely(this::viewCart);
                case 4 -> runSafely(this::updateQuantity);
                case 5 -> runSafely(this::removeFromCart);
                case 6 -> runSafely(this::checkout);
                case 7 -> runSafely(this::trackOrders);
                case 8 -> runSafely(this::requestReturn);
                default -> { return; }
            }
        }
    }

    private void browse() {
        System.out.println("  1. All (cheapest first)   2. All (most expensive first)   3. By name A-Z");
        System.out.println("  4. Filter by category     5. Filter by max price           6. In stock only");
        int option = in.readIntInRange("Choose view: ", 1, 6);

        List<Product> products = productDAO.findAll();
        List<Product> result;
        switch (option) {
            case 1 -> result = products.stream().sorted().collect(Collectors.toList()); // Comparable: price
            case 2 -> result = products.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            case 3 -> result = products.stream()
                    .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
            case 4 -> {
                String category = in.readNonBlank("Category: ");
                result = products.stream()
                        .filter(p -> p.getCategory().equalsIgnoreCase(category))
                        .sorted()
                        .collect(Collectors.toList());
            }
            case 5 -> {
                double max = in.readPositiveDouble("Max price: ");
                result = products.stream().filter(p -> p.getPrice() <= max).sorted().collect(Collectors.toList());
            }
            default -> result = products.stream().filter(p -> p.getStock() > 0).sorted().collect(Collectors.toList());
        }
        printProducts(result);
    }

    static void printProducts(List<Product> products) {
        System.out.printf("%n  %-5s %-25s %-12s %12s %6s %7s%n", "ID", "NAME", "CATEGORY", "PRICE", "STOCK", "VENDOR");
        System.out.println("  " + "-".repeat(72));
        for (Product p : products) {
            System.out.printf("  %-5d %-25s %-12s %12s %6d %7s%n",
                    p.getId(), shorten(p.getName(), 25), shorten(p.getCategory(), 12),
                    money(p.getPrice()), p.getStock(), "#" + p.getVendorId());
        }
        if (products.isEmpty()) {
            System.out.println("  (no products match)");
        }
    }

    private void addToCart() throws Exception {
        int productId = in.readPositiveInt("Product id: ");
        int qty = in.readPositiveInt("Quantity: ");
        cartService.addItem(customer.getId(), productId, qty);
        success("Added. Cart total now " + money(cartService.getCart(customer.getId()).getTotal()));
    }

    private void viewCart() {
        Cart cart = cartService.getCart(customer.getId());
        if (cart.isEmpty()) {
            System.out.println("  Your cart is empty.");
            return;
        }
        System.out.printf("%n  %-5s %-25s %5s %12s %12s %7s%n", "ID", "PRODUCT", "QTY", "PRICE", "LINE TOTAL", "VENDOR");
        System.out.println("  " + "-".repeat(72));
        for (CartItem item : cart.getItems()) {
            Product p = item.getProduct();
            System.out.printf("  %-5d %-25s %5d %12s %12s %7s%n",
                    p.getId(), shorten(p.getName(), 25), item.getQuantity(),
                    money(p.getPrice()), money(item.getLineTotal()), "#" + p.getVendorId());
        }
        System.out.printf("  %58s %12s%n", "TOTAL:", money(cart.getTotal()));
    }

    private void updateQuantity() throws Exception {
        int productId = in.readPositiveInt("Product id in cart: ");
        int qty = in.readPositiveInt("New quantity: ");
        cartService.updateQuantity(customer.getId(), productId, qty);
        success("Quantity updated.");
    }

    private void removeFromCart() {
        int productId = in.readPositiveInt("Product id to remove: ");
        cartService.removeItem(customer.getId(), productId);
        success("Removed.");
    }

    private void checkout() throws Exception {
        Cart cart = cartService.getCart(customer.getId());
        if (cart.isEmpty()) {
            throw new InvalidInputException("Your cart is empty");
        }
        viewCart();
        System.out.println("Payment mode:");
        PaymentMode mode = in.readEnum("Choose: ", PaymentMode.values());
        if (!in.confirm("Pay " + money(cart.getTotal()) + " by " + mode.getLabel() + "?")) {
            System.out.println("  Checkout cancelled.");
            return;
        }
        ParentOrder order = orderService.checkout(customer.getId(), mode);
        success("Order #" + order.getId() + " placed and paid: " + money(order.getTotalAmount()));
        System.out.println("  It was split into " + order.getSubOrders().size() + " vendor sub-order(s):");
        printOrder(order);
    }

    private void trackOrders() {
        List<ParentOrder> orders = trackingService.getOrdersForCustomer(customer.getId());
        if (orders.isEmpty()) {
            System.out.println("  You have no orders yet.");
            return;
        }
        for (ParentOrder order : orders) {
            System.out.printf("%n  ORDER #%d  placed %s  total %s%n",
                    order.getId(), order.getOrderDate().toLocalDate(), money(order.getTotalAmount()));
            printOrder(order);
        }
    }

    private void requestReturn() {
        int subOrderId = in.readPositiveInt("Sub-order id to return: ");
        // Ownership check: a customer may only return their own sub-orders.
        SubOrder sub = trackingService.getSubOrder(subOrderId);
        ParentOrder parent = trackingService.getOrder(sub.getParentOrderId());
        if (parent.getCustomerId() != customer.getId()) {
            throw new InvalidInputException("Sub-order #" + subOrderId + " is not one of your orders");
        }
        String reason = in.readNonBlank("Reason: ");
        ReturnRequest request = returnService.returnSubOrder(subOrderId, reason);
        success(String.format("Return #%d accepted. %s refunded for sub-order #%d.",
                request.getId(), money(request.getRefundAmount()), subOrderId));
    }

    static void printOrder(ParentOrder order) {
        for (SubOrder sub : order.getSubOrders()) {
            System.out.printf("    Sub-order #%-4d vendor #%-3d %-10s subtotal %s%n",
                    sub.getId(), sub.getVendorId(), sub.getStatus(), money(sub.getSubtotal()));
            for (OrderItem item : sub.getItems()) {
                System.out.printf("        %-25s x%-3d %12s%n",
                        shorten(item.getProductName(), 25), item.getQuantity(), money(item.getLineTotal()));
            }
        }
    }

    /** Keeps table columns aligned when a name is too long. */
    static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
