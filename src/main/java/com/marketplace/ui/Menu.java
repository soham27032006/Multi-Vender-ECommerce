package com.marketplace.ui;

import com.marketplace.exception.DataAccessException;
import com.marketplace.exception.InvalidInputException;
import com.marketplace.exception.OrderNotFoundException;
import com.marketplace.exception.OutOfStockException;
import com.marketplace.exception.PaymentFailedException;
import com.marketplace.util.MoneyUtil;

import java.io.IOException;

/**
 * Base class for the three role menus. runSafely() is the single place where
 * errors become friendly messages, so one failed action never crashes the app.
 */
public abstract class Menu {

    /** Like Runnable, but allowed to throw checked exceptions. */
    @FunctionalInterface
    protected interface Action {
        void run() throws Exception;
    }

    protected final ConsoleInput in;

    protected Menu(ConsoleInput in) {
        this.in = in;
    }

    /** Each subclass shows its own options (polymorphism: MainApp just calls show()). */
    public abstract void show();

    protected void runSafely(Action action) {
        try {
            action.run();
        } catch (ConsoleInput.InputClosedException e) {
            throw e;                                   // not an error: let the app exit
        } catch (OutOfStockException e) {
            error("Out of stock: " + e.getMessage());
        } catch (PaymentFailedException e) {
            error("Payment failed: " + e.getMessage() + ". Your order was NOT placed; nothing was charged.");
        } catch (InvalidInputException | OrderNotFoundException e) {
            error(e.getMessage());
        } catch (DataAccessException e) {
            error("Database problem: " + e.getMessage());
        } catch (IOException e) {
            error("File problem: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error("Operation interrupted");
        } catch (Exception e) {
            error("Unexpected error (" + e.getClass().getSimpleName() + "): " + e.getMessage());
        }
    }

    protected static void header(String title) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.printf("  %s%n", title);
        System.out.println("=".repeat(60));
    }

    protected static void success(String message) {
        System.out.println("  [OK] " + message);
    }

    protected static void error(String message) {
        System.out.println("  [ERROR] " + message);
    }

    protected static String money(double value) {
        return MoneyUtil.format(value);
    }
}
