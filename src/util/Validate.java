package ics.util;

import ics.exception.*;
import java.time.*;
import java.time.format.*;

public final class Validate {
    private Validate() {
    }

    public static String notBlank(String field, String value) throws InvalidInputException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(field, field + " is required.");
        }
        return value.trim();
    }

    public static <T> T notNull(String field, T value) throws InvalidInputException {
        if (value == null) {
            throw new InvalidInputException(field, field + " is required.");
        }
        return value;
    }

    public static double positive(String field, double value) throws InvalidInputException {
        if (Double.isNaN(value) || value <= 0) {
            throw new InvalidInputException(field, field + " must be greater than 0.");
        }
        return value;
    }

    public static double nonNegative(String field, double value) throws InvalidInputException {
        if (Double.isNaN(value) || value < 0) {
            throw new InvalidInputException(field, field + " cannot be negative.");
        }
        return value;
    }

    public static double between(String field, double value, double min, double max) throws InvalidInputException {
        if (Double.isNaN(value) || value < min || value > max) {
            throw new InvalidInputException(field, field + " must be between " + plain(min) + " and " + plain(max) + ".");
        }
        return value;
    }

    private static String plain(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

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

    public static double parseAmount(String field, String text) throws InvalidInputException {
        String cleaned = notBlank(field, text).replace(",", "").replace("\u20B1", "").trim();

        if (!cleaned.matches("-?[0-9]+(\\.[0-9]+)?")) {
            throw new InvalidInputException(field, field + " must be a number.");
        }
        double value = Double.parseDouble(cleaned);
        if (Math.abs(value) > 1_000_000_000_000.0) {
            throw new InvalidInputException(field, field + " is too large.");
        }
        return value;
    }

    public static int parseWholeNumber(String field, String text) throws InvalidInputException {
        try {
            return Integer.parseInt(notBlank(field, text).replace(",", ""));
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field, field + " must be a whole number.");
        }
    }

    public static LocalDate parseDate(String field, String text) throws InvalidInputException {
        try {
            return LocalDate.parse(notBlank(field, text));
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(field, field + " must be a date like 2026-10-16.");
        }
    }
}
