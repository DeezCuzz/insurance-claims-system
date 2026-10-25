package ics.exception;

/** A txt file could not be read or written, or a record we looked for is missing. */
public class DataStorageException extends ICSException {

    private static final long serialVersionUID = 1L;

    public DataStorageException(String message) {
        super(message);
    }

    public DataStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
