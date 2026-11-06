package ics.exception;

public class ICSException extends Exception {
    private static final long serialVersionUID = 1L;

    public ICSException(String message) {
        super(message);
    }

    public ICSException(String message, Throwable cause) {
        super(message, cause);
    }
}
