package com.marketplace.util;

/** Money is double; round every result to 2 decimals so paise errors don't pile up. */
public final class MoneyUtil {

    private MoneyUtil() {
        // utility class, no objects
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** One display format for the whole app, e.g. Rs.1,499.00 */
    public static String format(double value) {
        return String.format("Rs.%,.2f", value);
    }
}
