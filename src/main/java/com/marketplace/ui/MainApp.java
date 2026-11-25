package com.marketplace.ui;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PaymentDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.dao.ReturnDAO;
import com.marketplace.dao.UserDAO;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.model.Customer;
import com.marketplace.model.User;
import com.marketplace.model.Vendor;
import com.marketplace.service.CartService;
import com.marketplace.service.CsvService;
import com.marketplace.service.InventoryService;
import com.marketplace.service.LowStockMonitor;
import com.marketplace.service.OrderService;
import com.marketplace.service.OrderTrackingService;
import com.marketplace.service.PaymentService;
import com.marketplace.service.PayoutProcessor;
import com.marketplace.service.ReportService;
import com.marketplace.service.ReturnService;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;

/** Entry point. Builds every object once (manual dependency injection), then shows the main menu. */
public class MainApp extends Menu {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int LOW_STOCK_PERIOD_SECONDS = 10;

    // DAO layer
    private final UserDAO userDAO = new UserDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final PayoutDAO payoutDAO = new PayoutDAO();
    private final ReturnDAO returnDAO = new ReturnDAO();

    // Service layer
    private final CartService cartService = new CartService(productDAO);
    private final InventoryService inventoryService = new InventoryService(productDAO);
    private final PaymentService paymentService = new PaymentService();
    private final OrderService orderService = new OrderService(cartService, inventoryService,
            paymentService, orderDAO, productDAO, paymentDAO, payoutDAO);
    private final OrderTrackingService trackingService = new OrderTrackingService(orderDAO, productDAO, payoutDAO);
    private final ReturnService returnService = new ReturnService(orderDAO, productDAO, payoutDAO, returnDAO);
    private final ReportService reportService = new ReportService(orderDAO, payoutDAO, userDAO);
    private final CsvService csvService = new CsvService(productDAO, userDAO, orderDAO, payoutDAO);
    private final PayoutProcessor payoutProcessor = new PayoutProcessor(payoutDAO, orderDAO, 3);
    private final LowStockMonitor lowStockMonitor = new LowStockMonitor(productDAO, LOW_STOCK_THRESHOLD);

    public MainApp(ConsoleInput in) {
        super(in);
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            MainApp app = new MainApp(new ConsoleInput(scanner));
            app.start();
        }
    }

    private void start() {
        System.out.println("Multi-Vendor Marketplace Order Engine");
        if (!databaseReachable()) {
            return;
        }
        lowStockMonitor.start(LOW_STOCK_PERIOD_SECONDS);
        // Ctrl+C skips the normal exit path; the hook still stops the monitor cleanly.
        Runtime.getRuntime().addShutdownHook(new Thread(lowStockMonitor::stop, "shutdown-hook"));
        try {
            show();
        } catch (ConsoleInput.InputClosedException e) {
            System.out.println("\nInput closed.");
        } finally {
            lowStockMonitor.stop();
            System.out.println("Goodbye.");
        }
    }

    @Override
    public void show() {
        while (true) {
            header("MAIN MENU");
            System.out.println("  1. Customer");
            System.out.println("  2. Vendor");
            System.out.println("  3. Admin");
            System.out.println("  0. Exit");
            int choice = in.readIntInRange("Choose: ", 0, 3);
            switch (choice) {
                case 1 -> runSafely(this::customerLogin);
                case 2 -> runSafely(this::vendorLogin);
                case 3 -> new AdminMenu(in, userDAO, reportService, csvService, cartService, orderService,
                        inventoryService, paymentService, payoutProcessor).show();
                default -> { return; }
            }
        }
    }

    /** Simple "login": pick an existing account by email, or register. No passwords in this project. */
    private void customerLogin() {
        System.out.println("  1. Existing customer   2. Register new customer");
        Customer customer;
        if (in.readIntInRange("Choose: ", 1, 2) == 1) {
            User user = findByEmail();
            if (!(user instanceof Customer)) {
                throw new InvalidInputException("That account is not a customer");
            }
            customer = (Customer) user;
        } else {
            customer = (Customer) userDAO.save(new Customer(0,
                    in.readNonBlank("Name: "), uniqueEmail(), in.readPhone("Phone: "),
                    in.readNonBlank("Address: ")));
            success("Registered as customer #" + customer.getId());
        }
        new CustomerMenu(in, customer, productDAO, cartService, orderService, trackingService, returnService).show();
    }

    private void vendorLogin() {
        System.out.println("  1. Existing vendor   2. Register new vendor");
        Vendor vendor;
        if (in.readIntInRange("Choose: ", 1, 2) == 1) {
            User user = findByEmail();
            if (!(user instanceof Vendor)) {
                throw new InvalidInputException("That account is not a vendor");
            }
            vendor = (Vendor) user;
        } else {
            vendor = (Vendor) userDAO.save(new Vendor(0,
                    in.readNonBlank("Name: "), uniqueEmail(), in.readPhone("Phone: "),
                    in.readNonBlank("Shop name: "), PaymentService.COMMISSION_RATE));
            success("Registered as vendor #" + vendor.getId());
        }
        new VendorMenu(in, vendor, productDAO, payoutDAO, trackingService).show();
    }

    private User findByEmail() {
        String email = in.readEmail("Email: ");
        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new InvalidInputException("No account with email " + email));
        System.out.println("  Welcome, " + user.display());   // polymorphic display()
        return user;
    }

    /** Checked before insert so the user sees a clear message instead of a DB duplicate-key error. */
    private String uniqueEmail() {
        while (true) {
            String email = in.readEmail("Email: ");
            if (userDAO.findByEmail(email).isEmpty()) {
                return email;
            }
            System.out.println("  That email is already registered.");
        }
    }

    private boolean databaseReachable() {
        try (Connection conn = DBConnection.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException | RuntimeException e) {
            System.out.println("[ERROR] Cannot connect to MySQL: " + e.getMessage());
            System.out.println("Check that MySQL is running, sql/schema.sql was run, and "
                    + "src/main/resources/db.properties has the right user/password.");
            return false;
        }
    }
}
