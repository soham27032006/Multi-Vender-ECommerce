package com.marketplace.service;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.dao.ReturnDAO;
import com.marketplace.exception.DataAccessException;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.exception.OrderNotFoundException;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.ReturnRequest;
import com.marketplace.model.ReturnStatus;
import com.marketplace.model.SubOrder;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Returns work per sub-order: returning vendor A's items refunds only A's part,
 * restocks only A's products and reverses only A's payout. Vendor B is untouched.
 */
public class ReturnService {

    private final OrderDAO orderDAO;
    private final ReturnDAO returnDAO;
    private final SubOrderReversal reversal;

    public ReturnService(OrderDAO orderDAO, ProductDAO productDAO, PayoutDAO payoutDAO, ReturnDAO returnDAO) {
        this.orderDAO = orderDAO;
        this.returnDAO = returnDAO;
        this.reversal = new SubOrderReversal(orderDAO, productDAO, payoutDAO);
    }

    /**
     * One transaction: refund record, restock, payout reversal, status change.
     * If any step fails, nothing changes, so we never refund without restocking.
     * Approval is automatic in this simulation, so the request is saved as REFUNDED.
     */
    public ReturnRequest returnSubOrder(int subOrderId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidInputException("Please give a reason for the return");
        }

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                SubOrder sub = orderDAO.findSubOrderById(conn, subOrderId)
                        .orElseThrow(() -> new OrderNotFoundException("Sub-order not found: #" + subOrderId));

                // Only DELIVERED can move to RETURNED, which also blocks returning twice.
                if (!sub.getStatus().canMoveTo(OrderStatus.RETURNED)) {
                    throw new InvalidInputException("Only delivered sub-orders can be returned (current: "
                            + sub.getStatus() + ")");
                }

                // Refund = what the customer paid for this vendor's items.
                ReturnRequest request = new ReturnRequest(0, subOrderId, reason.trim(),
                        sub.getSubtotal(), ReturnStatus.REFUNDED, LocalDateTime.now());
                returnDAO.save(conn, request);

                reversal.reverse(conn, sub, OrderStatus.RETURNED);   // restock + reverse payout + status

                conn.commit();
                return request;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Return failed, nothing was changed", e);
        }
    }
}
