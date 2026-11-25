package com.marketplace.ui;

import com.marketplace.util.InputValidator;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * All keyboard input goes through here. Every read method loops until the
 * value is valid, so menus never receive bad input and never crash on it.
 */
public class ConsoleInput {

    /** Thrown when System.in is closed (e.g. Ctrl+Z / end of piped input), so the app can exit. */
    public static class InputClosedException extends RuntimeException {
        public InputClosedException() {
            super("Input closed");
        }
    }

    private final Scanner scanner;

    public ConsoleInput(Scanner scanner) {
        this.scanner = scanner;
    }

    public String readLine(String prompt) {
        System.out.print(prompt);
        try {
            return scanner.nextLine().trim();
        } catch (NoSuchElementException e) {
            throw new InputClosedException();
        }
    }

    public String readNonBlank(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("  Value cannot be empty.");
        }
    }

    public int readInt(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("  '" + value + "' is not a whole number. Try again.");
            }
        }
    }

    public int readIntInRange(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) {
                return value;
            }
            System.out.printf("  Enter a number from %d to %d.%n", min, max);
        }
    }

    public int readPositiveInt(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (InputValidator.isPositiveQuantity(value)) {
                return Integer.parseInt(value);
            }
            System.out.println("  Enter a whole number greater than 0.");
        }
    }

    public double readPositiveDouble(String prompt) {
        while (true) {
            String value = readLine(prompt);
            try {
                double d = Double.parseDouble(value);
                if (d > 0) {
                    return d;
                }
            } catch (NumberFormatException e) {
                // fall through to the message below
            }
            System.out.println("  Enter a number greater than 0 (e.g. 499.99).");
        }
    }

    public String readEmail(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (InputValidator.isValidEmail(value)) {
                return value.toLowerCase();
            }
            System.out.println("  Invalid email. Example: name@example.com");
        }
    }

    public String readPhone(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (InputValidator.isValidPhone(value)) {
                return value;
            }
            System.out.println("  Phone must be 10 digits starting with 6-9.");
        }
    }

    /** Shows the enum constants as a numbered list; works for any enum type. */
    public <E extends Enum<E>> E readEnum(String prompt, E[] options) {
        for (int i = 0; i < options.length; i++) {
            System.out.printf("  %d. %s%n", i + 1, options[i]);
        }
        int choice = readIntInRange(prompt, 1, options.length);
        return options[choice - 1];
    }

    public boolean confirm(String prompt) {
        String value = readLine(prompt + " (y/n): ");
        return value.equalsIgnoreCase("y") || value.equalsIgnoreCase("yes");
    }
}
