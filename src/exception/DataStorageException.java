package ics.exception;

public class DataStorageException extends ICSException {
    private static final long serialVersionUID = 1L;

    public DataStorageException(String message) {
        super(message);
    }

    public DataStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
