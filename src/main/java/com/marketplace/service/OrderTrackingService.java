package com.marketplace.service;

import com.marketplace.dao.OrderDAO;
import com.marketplace.dao.PayoutDAO;
import com.marketplace.dao.ProductDAO;
import com.marketplace.exception.DataAccessException;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.exception.OrderNotFoundException;
import com.marketplace.model.OrderStatus;
import com.marketplace.model.ParentOrder;
import com.marketplace.model.SubOrder;
import com.marketplace.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Each vendor moves its own sub-order through the status flow independently. */
public class OrderTrackingService {

    private final OrderDAO orderDAO;
    private final SubOrderReversal reversal;

    public OrderTrackingService(OrderDAO orderDAO, ProductDAO productDAO, PayoutDAO payoutDAO) {
        this.orderDAO = orderDAO;
        this.reversal = new SubOrderReversal(orderDAO, productDAO, payoutDAO);
    }

    public ParentOrder getOrder(int parentOrderId) {
        return orderDAO.findById(parentOrderId)
                .orElseThrow(() -> new OrderNotFoundException(parentOrderId));
    }

    public List<ParentOrder> getOrdersForCustomer(int customerId) {
        return orderDAO.findByCustomer(customerId);
    }

    public List<SubOrder> getSubOrdersForVendor(int vendorId) {
        return orderDAO.findSubOrdersByVendor(vendorId);
    }

    public SubOrder getSubOrder(int subOrderId) {
        return orderDAO.findSubOrderById(subOrderId)
                .orElseThrow(() -> new OrderNotFoundException("Sub-order not found: #" + subOrderId));
    }

    /**
     * vendorId is checked so a vendor can only move its own sub-orders.
     * Legal moves come from OrderStatus.canMoveTo (e.g. no DELIVERED -> SHIPPED).
     */
    public SubOrder updateStatus(int vendorId, int subOrderId, OrderStatus next) {
        SubOrder sub = getSubOrder(subOrderId);
        if (sub.getVendorId() != vendorId) {
            throw new InvalidInputException("Sub-order #" + subOrderId + " does not belong to vendor #" + vendorId);
        }
        if (next == OrderStatus.RETURNED) {
            throw new InvalidInputException("Returns must go through the return/refund flow");
        }
        if (!sub.getStatus().canMoveTo(next)) {
            throw new InvalidInputException("Cannot change status from " + sub.getStatus() + " to " + next);
        }

        if (next == OrderStatus.CANCELLED) {
            cancel(sub);
        } else {
            orderDAO.updateSubOrderStatus(subOrderId, next);
        }
        sub.setStatus(next);
        return sub;
    }

    /**
     * Cancelling must also put stock back and stop the vendor payout.
     * All writes share one transaction so they can't get out of sync.
     */
    private void cancel(SubOrder sub) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                reversal.reverse(conn, sub, OrderStatus.CANCELLED);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not cancel sub-order #" + sub.getId(), e);
        }
    }
}
