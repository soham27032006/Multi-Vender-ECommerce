package com.marketplace.util;

import com.marketplace.model.Payable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Generic console helpers. The wildcards let one method accept lists of many types. */
public final class TablePrinter {

    private TablePrinter() {
    }

    /**
     * Prints any list of objects using their toString().
     * List<? extends Object> accepts List<Product>, List<Payout>, List<String>...
     * (a plain List<Object> parameter would NOT accept List<Product>).
     */
    public static void printList(String title, List<? extends Object> items) {
        System.out.println("\n--- " + title + " (" + items.size() + ") ---");
        if (items.isEmpty()) {
            System.out.println("  (none)");
        }
        for (Object item : items) {
            System.out.println("  " + item);
        }
    }

    /**
     * Prints rows as an aligned table. 'toColumns' turns one row object into its cell values.
     * ? extends T  : the list may hold T or any subclass of T.
     * ? super T    : the function may accept T or any superclass (e.g. Object).
     */
    public static <T> void printTable(String title, String[] headers, List<? extends T> rows,
                                      Function<? super T, List<String>> toColumns) {
        List<List<String>> cells = new ArrayList<>();
        for (T row : rows) {
            cells.add(toColumns.apply(row));
        }

        // Column width = longest value in that column (header included).
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (List<String> row : cells) {
            for (int i = 0; i < headers.length && i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        System.out.println("\n=== " + title + " ===");
        System.out.println(formatRow(List.of(headers), widths));
        StringBuilder line = new StringBuilder();
        for (int w : widths) {
            line.append("-".repeat(w)).append("-+-");
        }
        System.out.println(line);
        for (List<String> row : cells) {
            System.out.println(formatRow(row, widths));
        }
        if (cells.isEmpty()) {
            System.out.println("(no data)");
        }
    }

    /** Upper-bounded wildcard: works for List<Payment>, List<Payout> or a mix. */
    public static double totalAmount(List<? extends Payable> items) {
        double total = 0;
        for (Payable p : items) {
            total += p.getAmount();
        }
        return MoneyUtil.round(total);
    }

    private static String formatRow(List<String> values, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            String value = i < values.size() ? values.get(i) : "";
            sb.append(String.format("%-" + widths[i] + "s", value)).append(" | ");
        }
        return sb.toString();
    }
}
