package com.marketplace.util;

import com.marketplace.exception.InvalidInputException;

/** Validation using plain String methods (no regex) so each rule is easy to explain. */
public final class InputValidator {

    private InputValidator() {
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Rules: one '@', text before it, a '.' after it that is not the last char, no spaces. */
    public static boolean isValidEmail(String email) {
        if (isBlank(email)) return false;
        String e = email.trim();
        if (e.contains(" ")) return false;

        int at = e.indexOf('@');
        if (at <= 0 || at != e.lastIndexOf('@')) return false;

        int dot = e.lastIndexOf('.');
        return dot > at + 1 && dot < e.length() - 1;
    }

    /** Indian mobile: exactly 10 digits, first digit 6-9. */
    public static boolean isValidPhone(String phone) {
        if (isBlank(phone)) return false;
        String p = phone.trim();
        if (p.length() != 10) return false;
        for (char c : p.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return p.charAt(0) >= '6';
    }

    public static boolean isPositiveQuantity(String qty) {
        try {
            return Integer.parseInt(qty.trim()) > 0;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    // --- "require" versions throw, so the UI can validate in one line ---

    public static String requireNonBlank(String s, String field) {
        if (isBlank(s)) throw new InvalidInputException(field + " cannot be empty");
        return s.trim();
    }

    public static String requireEmail(String email) {
        if (!isValidEmail(email)) throw new InvalidInputException("Invalid email: " + email);
        return email.trim().toLowerCase();
    }

    public static String requirePhone(String phone) {
        if (!isValidPhone(phone)) throw new InvalidInputException("Phone must be 10 digits starting 6-9");
        return phone.trim();
    }

    public static int requirePositiveInt(String value, String field) {
        if (!isPositiveQuantity(value)) throw new InvalidInputException(field + " must be a positive whole number");
        return Integer.parseInt(value.trim());
    }
}
