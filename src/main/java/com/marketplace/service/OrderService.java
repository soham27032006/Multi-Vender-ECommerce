package com.marketplace.service;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PaymentDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.exception.DataAccessException;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.exception.OutOfStockException;
import com.marketplace.exception.PaymentFailedException;
import com.marketplace.model.Cart;
import com.marketplace.model.CartItem;
import com.marketplace.model.OrderItem;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.Payment;
import com.marketplace.model.PaymentMode;
import com.marketplace.model.Product;
import com.marketplace.model.SubOrder;
import com.marketplace.util.DBConnection;
import com.marketplace.util.MoneyUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Turns a cart into a parent order + one sub-order per vendor, paid once. */
public class OrderService {

    private final CartService cartService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderDAO orderDAO;
    private final ProductDAO productDAO;
    private final PaymentDAO paymentDAO;
    private final PayoutDAO payoutDAO;

    public OrderService(CartService cartService, InventoryService inventoryService,
                        PaymentService paymentService, OrderDAO orderDAO, ProductDAO productDAO,
                        PaymentDAO paymentDAO, PayoutDAO payoutDAO) {
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.orderDAO = orderDAO;
        this.productDAO = productDAO;
        this.paymentDAO = paymentDAO;
        this.payoutDAO = payoutDAO;
    }

    /**
     * Steps 2-6 run in ONE transaction: either all rows are written or none.
     * Without it, a payment failure after the stock update would leave stock
     * reduced for an order that was never paid.
     *
     * 1. reserve stock in memory (InventoryService)
     * 2. decrease stock of every product
     * 3. insert parent order, sub-orders, order items
     * 4. simulated payment (may fail)
     * 5. insert payment
     * 6. insert one payout per vendor
     * then commit (+ release reservation); on any exception rollback and rethrow.
     */
    public ParentOrder checkout(int customerId, PaymentMode mode)
            throws OutOfStockException, PaymentFailedException {

        Cart cart = cartService.getCart(customerId);
        if (cart.isEmpty()) {
            throw new InvalidInputException("Cart is empty");
        }
        List<CartItem> items = cart.getItems();
        ParentOrder order = buildOrder(customerId, items);

        inventoryService.reserve(items);                     // step 1, throws if not enough stock
        boolean released = false;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Step 2 BEFORE step 3, and in product-id order, to avoid DB deadlocks:
                // - inserting order_items takes a shared lock on each product row (foreign key
                //   check); if two checkouts both did that first, each would then wait for the
                //   other's lock to UPDATE stock -> MySQL "Deadlock found". Updating first takes
                //   the exclusive lock up front.
                // - same id order in every checkout means two carts can't lock A then B vs B then A.
                List<CartItem> byProductId = new ArrayList<>(items);
                byProductId.sort(Comparator.comparingInt(item -> item.getProduct().getId()));
                for (CartItem item : byProductId) {           // step 2
                    int productId = item.getProduct().getId();
                    if (!productDAO.decreaseStock(conn, productId, item.getQuantity())) {
                        // 0 rows updated: someone outside this app bought it first
                        int left = productDAO.findById(conn, productId).map(Product::getStock).orElse(0);
                        throw new OutOfStockException(productId, item.getQuantity(), left);
                    }
                }

                orderDAO.save(conn, order);                  // step 3 (sets all generated ids)

                Payment payment = paymentService.createPayment(order, mode);
                paymentService.processPayment(payment);      // step 4, may throw PaymentFailedException
                paymentDAO.save(conn, payment);              // step 5

                for (SubOrder sub : order.getSubOrders()) {  // step 6
                    payoutDAO.save(conn, paymentService.createPayout(sub));
                }

                inventoryService.commitAndRelease(conn, items);   // see InventoryService for why together
                released = true;
            } catch (OutOfStockException | PaymentFailedException | SQLException | RuntimeException e) {
                conn.rollback();   // undo every insert/update above
                throw e;           // checked ones go to the UI as-is; SQLException is wrapped below
            }
        } catch (SQLException e) {
            throw new DataAccessException("Checkout failed, nothing was saved", e);
        } finally {
            if (!released) {
                inventoryService.release(items);  // failed checkout: give the reservation back
            }
        }

        cart.clear(); // only after a successful commit
        return order;
    }

    /** Collectors.groupingBy splits the cart by vendor; TreeMap keeps vendors in id order. */
    private ParentOrder buildOrder(int customerId, List<CartItem> items) {
        Map<Integer, List<CartItem>> byVendor = items.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getVendorId(),
                        TreeMap::new,
                        Collectors.toList()));

        LocalDateTime now = LocalDateTime.now();
        ParentOrder order = new ParentOrder(0, customerId, now, 0);
        double total = 0;

        for (Map.Entry<Integer, List<CartItem>> entry : byVendor.entrySet()) {
            List<CartItem> vendorItems = entry.getValue();
            double subtotal = MoneyUtil.round(vendorItems.stream()
                    .mapToDouble(CartItem::getLineTotal)
                    .sum());
            double commission = paymentService.calculateCommission(subtotal);

            SubOrder sub = new SubOrder(0, 0, entry.getKey(), OrderStatus.PLACED, subtotal, commission, now);
            for (CartItem ci : vendorItems) {
                sub.addItem(new OrderItem(0, 0, ci.getProduct().getId(), ci.getProduct().getName(),
                        ci.getQuantity(), ci.getProduct().getPrice()));
            }
            order.addSubOrder(sub);
            total += subtotal;
        }
        order.setTotalAmount(total); // setter rounds to 2 decimals
        return order;
    }
}
