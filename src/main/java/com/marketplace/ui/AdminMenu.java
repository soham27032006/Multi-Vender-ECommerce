package com.marketplace.ui;

import com.marketplace.dao.UserDAO;
import com.marketplace.model.Customer;
import com.marketplace.model.User;
import com.marketplace.service.CartService;
import com.marketplace.service.ConcurrentOrderSimulator;
import com.marketplace.service.CsvService;
import com.marketplace.service.InventoryService;
import com.marketplace.service.OrderService;
import com.marketplace.service.PaymentService;
import com.marketplace.service.PayoutProcessor;
import com.marketplace.service.ReportService;
import com.marketplace.util.DBInfo;
import com.marketplace.util.TablePrinter;

import java.util.List;
import java.util.stream.Collectors;

public class AdminMenu extends Menu {

    private final UserDAO userDAO;
    private final ReportService reportService;
    private final CsvService csvService;
    private final CartService cartService;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final PayoutProcessor payoutProcessor;

    public AdminMenu(ConsoleInput in, UserDAO userDAO, ReportService reportService, CsvService csvService,
                     CartService cartService, OrderService orderService, InventoryService inventoryService,
                     PaymentService paymentService, PayoutProcessor payoutProcessor) {
        super(in);
        this.userDAO = userDAO;
        this.reportService = reportService;
        this.csvService = csvService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.payoutProcessor = payoutProcessor;
    }

    @Override
    public void show() {
        while (true) {
            header("ADMIN");
            System.out.println("  1. Reports");
            System.out.println("  2. Import products from " + CsvService.PRODUCTS_FILE);
            System.out.println("  3. Export orders to " + CsvService.ORDERS_FILE);
            System.out.println("  4. Export payouts to " + CsvService.PAYOUTS_FILE);
            System.out.println("  5. Database info (metadata)");
            System.out.println("  6. Concurrency demo: in-memory race (unsafe vs synchronized)");
            System.out.println("  7. Concurrency demo: real checkouts on many threads");
            System.out.println("  8. Pay vendors for delivered sub-orders (producer-consumer)");
            System.out.println("  9. Set simulated payment failure %");
            System.out.println(" 10. List all users");
            System.out.println("  0. Back");
            int choice = in.readIntInRange("Choose: ", 0, 10);
            switch (choice) {
                case 1 -> runSafely(reportService::printAllReports);
                case 2 -> runSafely(() -> csvService.importProducts(CsvService.PRODUCTS_FILE));
                case 3 -> runSafely(() -> csvService.exportOrders(CsvService.ORDERS_FILE));
                case 4 -> runSafely(() -> csvService.exportPayouts(CsvService.PAYOUTS_FILE));
                case 5 -> runSafely(this::dbInfo);
                case 6 -> runSafely(this::raceDemo);
                case 7 -> runSafely(this::dbCheckoutDemo);
                case 8 -> runSafely(payoutProcessor::settleReadyPayouts);
                case 9 -> runSafely(this::setFailurePercent);
                case 10 -> runSafely(() -> TablePrinter.printList("Users", userDAO.findAll()));
                default -> { return; }
            }
        }
    }

    private void dbInfo() {
        DBInfo.printDatabaseInfo();
        System.out.println("Tables: " + DBInfo.knownTables());
        String table = in.readNonBlank("Show columns of which table? ");
        DBInfo.printTableInfo(table.toLowerCase());
    }

    private void raceDemo() {
        int stock = in.readIntInRange("Initial stock (e.g. 3): ", 1, 100);
        int customers = in.readIntInRange("Number of customers (e.g. 10): ", 2, 50);
        new ConcurrentOrderSimulator().runRaceDemo(stock, customers);
    }

    /** Every customer in the DB tries to buy the same product at the same time. */
    private void dbCheckoutDemo() {
        List<Integer> customerIds = userDAO.findAll().stream()
                .filter(u -> u instanceof Customer)
                .map(User::getId)
                .collect(Collectors.toList());
        if (customerIds.size() < 2) {
            error("Need at least 2 customers in the database for this demo.");
            return;
        }
        int productId = in.readPositiveInt("Product id (pick one with low stock): ");
        int qty = in.readPositiveInt("Quantity each customer buys: ");
        System.out.println("  Note: this places REAL orders and clears these customers' carts.");
        if (!in.confirm("Continue with " + customerIds.size() + " customers?")) {
            return;
        }
        new ConcurrentOrderSimulator().runDbCheckoutDemo(cartService, orderService, inventoryService,
                productId, customerIds, qty);
    }

    private void setFailurePercent() {
        int percent = in.readIntInRange("Payment failure % (0-100, default 10): ", 0, 100);
        paymentService.setFailurePercent(percent);
        success("Simulated payments now fail " + percent + "% of the time.");
    }
}
