package ics.exception;

/**
 * The parent of all our own exceptions (ICS = Insurance Claims System).
 *
 * WHY a parent class: a screen can catch "ICSException" once and show the message,
 * but it can also catch a specific child (like InvalidInputException) when it wants to do something special.
 *
 * WHY "extends Exception" (a checked exception): Java forces every method that can fail to say so
 * ("throws ICSException"), so no error is forgotten.
 */
public class ICSException extends Exception {

    private static final long serialVersionUID = 1L;

    public ICSException(String message) {
        super(message);
    }

    public ICSException(String message, Throwable cause) {
        super(message, cause);
    }
}
