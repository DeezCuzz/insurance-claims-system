package ics.exception;

public class AccessDeniedException extends ICSException {
    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }
}
