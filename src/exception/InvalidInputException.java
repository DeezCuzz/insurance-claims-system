package ics.exception;

public class InvalidInputException extends ICSException {
    private static final long serialVersionUID = 1L;

    private final String fieldName;

    public InvalidInputException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
