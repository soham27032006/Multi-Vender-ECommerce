package com.marketplace.service;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.model.OrderItem;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.PaymentStatus;
import com.marketplace.model.Payout;
import com.marketplace.model.SubOrder;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Undo work shared by cancel and return: put the items back in stock,
 * reverse the vendor's payout, set the final status.
 * Kept in one place so both flows always undo exactly the same things.
 * Runs on the caller's connection, so it is part of the caller's transaction.
 */
class SubOrderReversal {

    private final OrderDAO orderDAO;
    private final ProductDAO productDAO;
    private final PayoutDAO payoutDAO;

    SubOrderReversal(OrderDAO orderDAO, ProductDAO productDAO, PayoutDAO payoutDAO) {
        this.orderDAO = orderDAO;
        this.productDAO = productDAO;
        this.payoutDAO = payoutDAO;
    }

    void reverse(Connection conn, SubOrder sub, OrderStatus finalStatus) throws SQLException {
        for (OrderItem item : sub.getItems()) {
            productDAO.increaseStock(conn, item.getProductId(), item.getQuantity());
        }
        // The vendor keeps nothing for a cancelled/returned sub-order.
        // isPresent() instead of ifPresent(lambda): a lambda can't throw the checked SQLException.
        Optional<Payout> payout = payoutDAO.findBySubOrder(conn, sub.getId());
        if (payout.isPresent()) {
            payout.get().setStatus(PaymentStatus.REFUNDED);
            payoutDAO.updateStatus(conn, payout.get());
        }
        orderDAO.updateSubOrderStatus(conn, sub.getId(), finalStatus);
    }
}
