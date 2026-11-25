package com.marketplace;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PaymentDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.dao.ReturnDAO;
import com.marketplace.dao.UserDAO;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.exception.OutOfStockException;
import com.marketplace.exception.PaymentFailedException;
import com.marketplace.model.CartItem;
import com.marketplace.model.Customer;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.Payment;
import com.marketplace.model.PaymentMode;
import com.marketplace.model.PaymentStatus;
import com.marketplace.model.Payout;
import com.marketplace.model.Product;
import com.marketplace.model.ReturnRequest;
import com.marketplace.model.SubOrder;
import com.marketplace.model.Vendor;
import com.marketplace.service.CartService;
import com.marketplace.service.InventoryService;
import com.marketplace.service.OrderService;
import com.marketplace.service.OrderTrackingService;
import com.marketplace.service.PaymentService;
import com.marketplace.service.ReturnService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Plain-Java test runner (JUnit is not allowed by the syllabus).
 * Runs against the real MySQL database: it creates its own test users and
 * products with unique emails, and deletes everything it created at the end.
 *
 * Run: mvn -q test-compile, then
 *   java -cp "target/test-classes;target/classes;<mysql-connector jar>" com.marketplace.ManualTestRunner
 */
public class ManualTestRunner {

    // DAOs and services wired the same way as MainApp
    private final UserDAO userDAO = new UserDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final PayoutDAO payoutDAO = new PayoutDAO();
    private final ReturnDAO returnDAO = new ReturnDAO();
    private final CartService cartService = new CartService(productDAO);
    private final InventoryService inventoryService = new InventoryService(productDAO);
    private final PaymentService paymentService = new PaymentService();
    private final OrderService orderService = new OrderService(cartService, inventoryService,
            paymentService, orderDAO, productDAO, paymentDAO, payoutDAO);
    private final OrderTrackingService trackingService = new OrderTrackingService(orderDAO, productDAO, payoutDAO);
    private final ReturnService returnService = new ReturnService(orderDAO, productDAO, payoutDAO, returnDAO);

    // test data (created in setUp, removed in cleanUp)
    private final List<Integer> userIds = new ArrayList<>();
    private final List<Integer> productIds = new ArrayList<>();
    private Vendor vendorA;
    private Vendor vendorB;
    private Customer buyer;
    private final List<Customer> crowd = new ArrayList<>();   // for the concurrency test
    private Product pen;        // vendor A, Rs.100.00
    private Product book;       // vendor B, Rs.250.50
    private Product lastUnit;   // vendor B, stock 1, for the rollback test
    private Product limited;    // vendor A, stock 3, for the concurrency test
    private ParentOrder splitOrder;

    private int passed = 0;
    private int failed = 0;

    public static void main(String[] args) {
        ManualTestRunner runner = new ManualTestRunner();
        boolean ok = runner.runAll();
        System.exit(ok ? 0 : 1);   // non-zero exit code tells scripts/CI that a test failed
    }

    private boolean runAll() {
        System.out.println("=== Marketplace manual tests ===");
        try {
            setUp();
            run("1. Checkout splits cart into one sub-order per vendor", this::testSplit);
            run("2. Out-of-stock inside transaction rolls everything back", this::testOutOfStockRollback);
            run("3. Payment failure rolls everything back", this::testPaymentFailureRollback);
            run("4. Return refunds only that vendor's sub-order", this::testReturnRefund);
            run("5. Concurrent checkouts never oversell", this::testConcurrencyStock);
        } catch (Exception e) {
            fail("setup", "could not create test data: " + e);
        } finally {
            cleanUp();
        }
        System.out.printf("%n=== RESULT: %d passed, %d failed ===%n", passed, failed);
        return failed == 0;
    }

    // ---------------- tests ----------------

    private void testSplit() throws Exception {
        paymentService.setFailurePercent(0);
        cartService.addItem(buyer.getId(), pen.getId(), 2);    // 200.00 from vendor A
        cartService.addItem(buyer.getId(), book.getId(), 1);   // 250.50 from vendor B

        splitOrder = orderService.checkout(buyer.getId(), PaymentMode.UPI);

        check("2 sub-orders created", splitOrder.getSubOrders().size() == 2);
        check("parent total = 450.50", splitOrder.getTotalAmount() == 450.50);

        SubOrder subA = subOrderOf(splitOrder, vendorA);
        SubOrder subB = subOrderOf(splitOrder, vendorB);
        check("vendor A subtotal 200.00, commission 20.00",
                subA.getSubtotal() == 200.00 && subA.getCommission() == 20.00);
        check("vendor B subtotal 250.50, commission 25.05",
                subB.getSubtotal() == 250.50 && subB.getCommission() == 25.05);

        Payout payoutA = payoutDAO.findBySubOrder(subA.getId()).orElseThrow();
        check("payout A net = subtotal - commission = 180.00", payoutA.getAmount() == 180.00);
        check("payout A starts PENDING", payoutA.getStatus() == PaymentStatus.PENDING);

        Payment payment = paymentDAO.findByOrderId(splitOrder.getId()).orElseThrow();
        check("one payment of 450.50 with SUCCESS",
                payment.getAmount() == 450.50 && payment.getStatus() == PaymentStatus.SUCCESS);

        check("pen stock 10 -> 8", stockOf(pen) == 8);
        check("book stock 10 -> 9", stockOf(book) == 9);
        check("cart emptied after checkout", cartService.getCart(buyer.getId()).isEmpty());
    }

    /**
     * InventoryService's in-memory check would stop this early, so we use one
     * that skips it. That simulates another app instance buying the last unit
     * between our check and our UPDATE, so the DB guard must catch it mid-transaction.
     */
    private void testOutOfStockRollback() throws Exception {
        InventoryService noReserve = new InventoryService(productDAO) {
            @Override
            public synchronized void reserve(List<CartItem> items) {
                // skip the in-memory check on purpose
            }

            @Override
            public synchronized void release(List<CartItem> items) {
                // nothing was reserved, so nothing to give back
            }
        };
        OrderService unguarded = new OrderService(cartService, noReserve, paymentService,
                orderDAO, productDAO, paymentDAO, payoutDAO);

        cartService.addItem(buyer.getId(), pen.getId(), 1);       // this UPDATE will succeed first...
        cartService.addItem(buyer.getId(), lastUnit.getId(), 1);  // ...then this one fails
        lastUnit.setStock(0);
        productDAO.update(lastUnit);                              // "someone else" bought it

        int ordersBefore = orderDAO.findByCustomer(buyer.getId()).size();
        int penBefore = stockOf(pen);

        boolean thrown = false;
        try {
            unguarded.checkout(buyer.getId(), PaymentMode.CARD);
        } catch (OutOfStockException e) {
            thrown = true;
        }
        check("OutOfStockException thrown", thrown);
        check("no new order saved", orderDAO.findByCustomer(buyer.getId()).size() == ordersBefore);
        check("pen stock decrease was rolled back", stockOf(pen) == penBefore);
        check("cart kept so customer can fix it", cartService.getCart(buyer.getId()).getItems().size() == 2);

        // The normal service rejects the same cart before touching the DB.
        boolean earlyReject = false;
        try {
            orderService.checkout(buyer.getId(), PaymentMode.CARD);
        } catch (OutOfStockException e) {
            earlyReject = true;
        }
        check("normal InventoryService rejects it up front", earlyReject);
        cartService.clearCart(buyer.getId());
    }

    private void testPaymentFailureRollback() throws Exception {
        paymentService.setFailurePercent(100);   // bank always declines
        cartService.addItem(buyer.getId(), pen.getId(), 3);

        int ordersBefore = orderDAO.findByCustomer(buyer.getId()).size();
        int paymentsBefore = paymentDAO.findAll().size();
        int penBefore = stockOf(pen);

        boolean thrown = false;
        try {
            orderService.checkout(buyer.getId(), PaymentMode.NET_BANKING);
        } catch (PaymentFailedException e) {
            thrown = true;
        } finally {
            paymentService.setFailurePercent(0);
        }
        check("PaymentFailedException thrown", thrown);
        check("no new order saved", orderDAO.findByCustomer(buyer.getId()).size() == ordersBefore);
        check("no payment row saved", paymentDAO.findAll().size() == paymentsBefore);
        check("stock unchanged", stockOf(pen) == penBefore);
        check("cart kept for retry", !cartService.getCart(buyer.getId()).isEmpty());
        cartService.clearCart(buyer.getId());
    }

    private void testReturnRefund() {
        if (splitOrder == null) {
            fail("precondition", "test 1 did not create an order");
            return;
        }
        SubOrder subA = subOrderOf(splitOrder, vendorA);
        SubOrder subB = subOrderOf(splitOrder, vendorB);

        check("can't return before delivery",
                throwsInvalid(() -> returnService.returnSubOrder(subA.getId(), "too early")));
        check("vendor B can't update vendor A's sub-order",
                throwsInvalid(() -> trackingService.updateStatus(vendorB.getId(), subA.getId(), OrderStatus.CONFIRMED)));
        check("PLACED -> DELIVERED is rejected",
                throwsInvalid(() -> trackingService.updateStatus(vendorA.getId(), subA.getId(), OrderStatus.DELIVERED)));

        trackingService.updateStatus(vendorA.getId(), subA.getId(), OrderStatus.CONFIRMED);
        trackingService.updateStatus(vendorA.getId(), subA.getId(), OrderStatus.SHIPPED);
        trackingService.updateStatus(vendorA.getId(), subA.getId(), OrderStatus.DELIVERED);

        int penBefore = stockOf(pen);
        int bookBefore = stockOf(book);
        ReturnRequest request = returnService.returnSubOrder(subA.getId(), "Damaged");

        check("refund = vendor A subtotal 200.00", request.getRefundAmount() == 200.00);
        check("sub-order A is RETURNED", trackingService.getSubOrder(subA.getId()).getStatus() == OrderStatus.RETURNED);
        check("pen restocked by 2", stockOf(pen) == penBefore + 2);
        check("payout A reversed (REFUNDED)",
                payoutDAO.findBySubOrder(subA.getId()).orElseThrow().getStatus() == PaymentStatus.REFUNDED);
        check("vendor B sub-order untouched (PLACED)",
                trackingService.getSubOrder(subB.getId()).getStatus() == OrderStatus.PLACED);
        check("vendor B payout still PENDING",
                payoutDAO.findBySubOrder(subB.getId()).orElseThrow().getStatus() == PaymentStatus.PENDING);
        check("book stock untouched", stockOf(book) == bookBefore);
        check("second return of same sub-order is rejected",
                throwsInvalid(() -> returnService.returnSubOrder(subA.getId(), "again")));
    }

    /** 6 customers, 3 units, 5 threads: exactly 3 orders must succeed and stock must end at 0. */
    private void testConcurrencyStock() throws Exception {
        paymentService.setFailurePercent(0);
        for (Customer c : crowd) {
            cartService.addItem(c.getId(), limited.getId(), 1);   // each cart passes its own check
        }

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<String>> futures = new ArrayList<>();
        for (Customer c : crowd) {
            Callable<String> task = () -> {
                try {
                    orderService.checkout(c.getId(), PaymentMode.UPI);
                    return "OK";
                } catch (OutOfStockException e) {
                    return "OUT_OF_STOCK";
                }
            };
            futures.add(pool.submit(task));
        }

        int ok = 0;
        int outOfStock = 0;
        for (Future<String> f : futures) {
            String result = f.get();   // an unexpected exception here fails the test
            if (result.equals("OK")) ok++;
            else outOfStock++;
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        int stock = stockOf(limited);
        check("exactly 3 of 6 checkouts succeeded (got " + ok + ")", ok == 3);
        check("the other 3 got OutOfStockException (got " + outOfStock + ")", outOfStock == 3);
        check("final stock is 0, never negative (got " + stock + ")", stock == 0);
    }

    // ---------------- setup / cleanup ----------------

    private void setUp() {
        long tag = System.currentTimeMillis();   // unique emails, so tests never clash with real data
        vendorA = (Vendor) track(userDAO.save(new Vendor(0, "Test Vendor A", "tva" + tag + "@test.local",
                "9000000001", "Test Shop A", 0.10)));
        vendorB = (Vendor) track(userDAO.save(new Vendor(0, "Test Vendor B", "tvb" + tag + "@test.local",
                "9000000002", "Test Shop B", 0.10)));
        buyer = (Customer) track(userDAO.save(new Customer(0, "Test Buyer", "tbuyer" + tag + "@test.local",
                "9000000003", "Test Street")));
        for (int i = 1; i <= 6; i++) {
            crowd.add((Customer) track(userDAO.save(new Customer(0, "Test Crowd " + i,
                    "tcrowd" + i + "_" + tag + "@test.local", "9000000010", "Test Street"))));
        }
        pen = trackProduct(new Product(0, vendorA.getId(), "Test Pen", "Test", 100.00, 10));
        book = trackProduct(new Product(0, vendorB.getId(), "Test Book", "Test", 250.50, 10));
        lastUnit = trackProduct(new Product(0, vendorB.getId(), "Test Last Unit", "Test", 10.00, 1));
        limited = trackProduct(new Product(0, vendorA.getId(), "Test Limited", "Test", 50.00, 3));
    }

    /** Orders first (cascade removes sub-orders, items, payments, payouts, returns), then products, then users. */
    private void cleanUp() {
        try {
            for (int userId : userIds) {
                for (ParentOrder order : orderDAO.findByCustomer(userId)) {
                    orderDAO.delete(order.getId());
                }
            }
            productIds.forEach(productDAO::delete);
            userIds.forEach(userDAO::delete);
            System.out.println("\n(test data removed)");
        } catch (RuntimeException e) {
            System.out.println("\nWARNING: cleanup failed, remove '@test.local' users manually: " + e.getMessage());
        }
    }

    private <T extends com.marketplace.model.User> T track(T user) {
        userIds.add(user.getId());
        return user;
    }

    private Product trackProduct(Product p) {
        productDAO.save(p);
        productIds.add(p.getId());
        return p;
    }

    // ---------------- tiny assertion helpers ----------------

    @FunctionalInterface
    private interface TestCase {
        void run() throws Exception;
    }

    private void run(String name, TestCase test) {
        System.out.println("\n" + name);
        try {
            test.run();
        } catch (Exception e) {
            fail("unexpected exception", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + description);
        } else {
            failed++;
            System.out.println("  FAIL  " + description);
        }
    }

    private void fail(String description, String detail) {
        failed++;
        System.out.println("  FAIL  " + description + ": " + detail);
    }

    private boolean throwsInvalid(Runnable action) {
        try {
            action.run();
            return false;
        } catch (InvalidInputException e) {
            return true;
        }
    }

    private int stockOf(Product p) {
        return productDAO.findById(p.getId()).orElseThrow().getStock();
    }

    private SubOrder subOrderOf(ParentOrder order, Vendor vendor) {
        return order.getSubOrders().stream()
                .filter(s -> s.getVendorId() == vendor.getId())
                .findFirst()
                .orElseThrow();
    }
}
