package com.marketplace.service;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.UserDAO;
import com.marketplace.model.OrderItem;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.PaymentStatus;
import com.marketplace.model.Payout;
import com.marketplace.model.SubOrder;
import com.marketplace.model.Vendor;
import com.marketplace.util.MoneyUtil;
import com.marketplace.util.TablePrinter;

import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Read-only reports built with Streams over data loaded from the DB. */
public class ReportService {

    /** One row of the top-sellers report (a plain class, records are not allowed). */
    public static class ProductSales {
        private final int productId;
        private final String productName;
        private final int unitsSold;
        private final double revenue;

        public ProductSales(int productId, String productName, int unitsSold, double revenue) {
            this.productId = productId;
            this.productName = productName;
            this.unitsSold = unitsSold;
            this.revenue = MoneyUtil.round(revenue);
        }

        public int getProductId() { return productId; }
        public String getProductName() { return productName; }
        public int getUnitsSold() { return unitsSold; }
        public double getRevenue() { return revenue; }
    }

    private final OrderDAO orderDAO;
    private final PayoutDAO payoutDAO;
    private final UserDAO userDAO;

    public ReportService(OrderDAO orderDAO, PayoutDAO payoutDAO, UserDAO userDAO) {
        this.orderDAO = orderDAO;
        this.payoutDAO = payoutDAO;
        this.userDAO = userDAO;
    }

    // ---------------- reports ----------------

    /**
     * Items grouped by product, then sorted: most units first, then highest revenue,
     * then name A-Z so ties always print in the same order.
     */
    public List<ProductSales> topSellingProducts(int limit) {
        Map<Integer, List<OrderItem>> byProduct = activeSubOrders().stream()
                .flatMap(sub -> sub.getItems().stream())          // all items of all sub-orders
                .collect(Collectors.groupingBy(OrderItem::getProductId));

        return byProduct.values().stream()
                .map(items -> new ProductSales(
                        items.get(0).getProductId(),
                        items.get(0).getProductName(),
                        items.stream().mapToInt(OrderItem::getQuantity).sum(),
                        items.stream().mapToDouble(OrderItem::getLineTotal).sum()))
                .sorted(Comparator.comparing(ProductSales::getUnitsSold).reversed()
                        .thenComparing(Comparator.comparing(ProductSales::getRevenue).reversed())
                        .thenComparing(ProductSales::getProductName))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** vendorId -> total sales. TreeMap so vendors print in id order. */
    public Map<Integer, Double> revenuePerVendor() {
        Map<Integer, Double> raw = activeSubOrders().stream()
                .collect(Collectors.groupingBy(SubOrder::getVendorId, TreeMap::new,
                        Collectors.summingDouble(SubOrder::getSubtotal)));
        raw.replaceAll((vendorId, total) -> MoneyUtil.round(total)); // sums of doubles need rounding
        return raw;
    }

    /** Platform earnings: commission of sub-orders that were not cancelled or returned. */
    public double totalCommission() {
        return MoneyUtil.round(activeSubOrders().stream()
                .mapToDouble(SubOrder::getCommission)
                .sum());
    }

    /** true -> delivered sub-orders, false -> everything else. */
    public Map<Boolean, List<SubOrder>> subOrdersByDelivered() {
        return allSubOrders().stream()
                .collect(Collectors.partitioningBy(sub -> sub.getStatus() == OrderStatus.DELIVERED));
    }

    /** Count per status, for a fuller picture than delivered / not delivered. */
    public Map<OrderStatus, Long> subOrderCountByStatus() {
        return allSubOrders().stream()
                .collect(Collectors.groupingBy(SubOrder::getStatus, TreeMap::new, Collectors.counting()));
    }

    /** count, sum, min, average, max of all parent order totals in one pass. */
    public DoubleSummaryStatistics orderValueStats() {
        return orderDAO.findAll().stream()
                .collect(Collectors.summarizingDouble(ParentOrder::getTotalAmount));
    }

    /** Pending vs paid payouts, highest net amount first within each status. */
    public List<Payout> payoutsSorted() {
        return payoutDAO.findAll().stream()
                .sorted(Comparator.comparing(Payout::getStatus)
                        .thenComparing(Comparator.comparing(Payout::getAmount).reversed()))
                .collect(Collectors.toList());
    }

    // ---------------- printing ----------------

    public void printAllReports() {
        Map<Integer, String> shopNames = userDAO.findAllVendors().stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::getShopName));

        TablePrinter.printTable("Top 5 selling products",
                new String[]{"Product", "Units", "Revenue"},
                topSellingProducts(5),
                p -> List.of(p.getProductName(), String.valueOf(p.getUnitsSold()), money(p.getRevenue())));

        TablePrinter.printTable("Revenue per vendor",
                new String[]{"Vendor", "Shop", "Revenue"},
                List.copyOf(revenuePerVendor().entrySet()),
                e -> List.of("#" + e.getKey(), shopNames.getOrDefault(e.getKey(), "?"), money(e.getValue())));

        System.out.println("\nTotal platform commission: " + money(totalCommission()));

        Map<Boolean, List<SubOrder>> split = subOrdersByDelivered();
        System.out.println("\nSub-orders delivered: " + split.get(true).size()
                + " | not delivered: " + split.get(false).size());
        System.out.println("By status: " + subOrderCountByStatus());

        DoubleSummaryStatistics stats = orderValueStats();
        System.out.printf("%nOrder values -> count: %d | total: %s | min: %s | avg: %s | max: %s%n",
                stats.getCount(),
                money(stats.getSum()),
                money(stats.getCount() == 0 ? 0 : stats.getMin()),   // empty stats give +/-Infinity
                money(stats.getAverage()),
                money(stats.getCount() == 0 ? 0 : stats.getMax()));

        List<Payout> payouts = payoutsSorted();
        TablePrinter.printTable("Vendor payouts",
                new String[]{"Payout", "Vendor", "Sub-order", "Net", "Status"},
                payouts,
                p -> List.of("#" + p.getId(), "#" + p.getVendorId(), "#" + p.getSubOrderId(),
                        money(p.getAmount()), p.getStatus().name()));
        List<Payout> paid = payouts.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .collect(Collectors.toList());
        System.out.println("Total paid out to vendors: " + money(TablePrinter.totalAmount(paid)));
    }

    // ---------------- helpers ----------------

    private List<SubOrder> allSubOrders() {
        return orderDAO.findAll().stream()
                .flatMap(order -> order.getSubOrders().stream())
                .collect(Collectors.toList());
    }

    /** Cancelled and returned sub-orders earned nothing, so sales reports skip them. */
    private List<SubOrder> activeSubOrders() {
        return allSubOrders().stream()
                .filter(sub -> sub.getStatus() != OrderStatus.CANCELLED
                        && sub.getStatus() != OrderStatus.RETURNED)
                .collect(Collectors.toList());
    }

    private static String money(double value) {
        return MoneyUtil.format(value);
    }
}
