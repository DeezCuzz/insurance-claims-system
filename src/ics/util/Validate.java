package ics.util;

import ics.exception.InvalidInputException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Small checks that many classes need, written once.
 *
 * Every check either returns the good value or throws InvalidInputException with the FIELD NAME,
 * so a setter can be one short line:   this.name = Validate.notBlank("Full name", name);
 */
public final class Validate {

    private Validate() {
        // only static methods, so nobody needs to make a Validate object
    }

    /** The text must not be empty. Returns it without spaces at the ends. */
    public static String notBlank(String field, String value) throws InvalidInputException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(field, field + " is required.");
        }
        return value.trim();
    }

    /** Something must be chosen (a date, a role, a reason). */
    public static <T> T notNull(String field, T value) throws InvalidInputException {
        if (value == null) {
            throw new InvalidInputException(field, field + " is required.");
        }
        return value;
    }

    /** The number must be more than zero. */
    public static double positive(String field, double value) throws InvalidInputException {
        if (Double.isNaN(value) || value <= 0) {
            throw new InvalidInputException(field, field + " must be greater than 0.");
        }
        return value;
    }

    /** The number may be zero but not negative. */
    public static double nonNegative(String field, double value) throws InvalidInputException {
        if (Double.isNaN(value) || value < 0) {
            throw new InvalidInputException(field, field + " cannot be negative.");
        }
        return value;
    }

    /** The number must be from min to max. */
    public static double between(String field, double value, double min, double max) throws InvalidInputException {
        if (Double.isNaN(value) || value < min || value > max) {
            throw new InvalidInputException(field, field + " must be between " + plain(min) + " and " + plain(max) + ".");
        }
        return value;
    }

    /** 100.0 becomes "100" and 0.5 stays "0.5", so messages read naturally. */
    private static String plain(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * A username: 1 to 30 letters, numbers, dots, dashes or underscores (no spaces).
     * WHY so strict: it is typed at every login and shown in tables, so it should be simple and unambiguous.
     */
    public static String username(String field, String value) throws InvalidInputException {
        String name = notBlank(field, value);
        if (name.length() > 30) {
            throw new InvalidInputException(field, field + " can be at most 30 characters.");
        }
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new InvalidInputException(field, field + " can only use letters, numbers, dots, dashes and underscores.");
        }
        return name;
    }

    /**
     * Plain text with a maximum length, for names, contacts and addresses.
     * WHY no < or >: Swing shows any text that starts with <html> as a web page, so a name like "<html><h1>Hi"
     * would appear in giant letters in the tables.
     */
    public static String plainText(String field, String value, int maxLength) throws InvalidInputException {
        String text = value == null ? "" : value.trim();
        if (text.length() > maxLength) {
            throw new InvalidInputException(field, field + " can be at most " + maxLength + " characters.");
        }
        if (text.contains("<") || text.contains(">")) {
            throw new InvalidInputException(field, field + " cannot contain < or >.");
        }
        return text;
    }

    /** Our password rule (rubric II.C.2): at least 8 characters, with at least one letter and one number. */
    public static String strongPassword(String field, String value) throws InvalidInputException {
        if (value == null || value.length() < 8) {
            throw new InvalidInputException(field, field + " must be at least 8 characters.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : value.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new InvalidInputException(field, field + " must contain at least one letter and one number.");
        }
        return value;
    }

    /**
     * Turns typed text into an amount. "300,000" and "300000.50" both work.
     * Letters, blanks and absurdly large numbers are refused.
     */
    public static double parseAmount(String field, String text) throws InvalidInputException {
        String cleaned = notBlank(field, text).replace(",", "").replace("\u20B1", "").trim();
        // Only digits, with one optional decimal point (and a minus sign, so -5 gets the "greater than 0" message).
        // WHY not just Double.parseDouble: it also accepts things like "1e5" or "Infinity", which are letters.
        if (!cleaned.matches("-?[0-9]+(\\.[0-9]+)?")) {
            throw new InvalidInputException(field, field + " must be a number.");
        }
        double value = Double.parseDouble(cleaned);
        if (Math.abs(value) > 1_000_000_000_000.0) {
            throw new InvalidInputException(field, field + " is too large.");
        }
        return value;
    }

    /** Turns typed text into a whole number (for example the waiting period in days). */
    public static int parseWholeNumber(String field, String text) throws InvalidInputException {
        try {
            return Integer.parseInt(notBlank(field, text).replace(",", ""));
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field, field + " must be a whole number.");
        }
    }

    /** Dates are typed as year-month-day, for example 2026-10-16. */
    public static LocalDate parseDate(String field, String text) throws InvalidInputException {
        try {
            return LocalDate.parse(notBlank(field, text));
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(field, field + " must be a date like 2026-10-16.");
        }
    }
}
