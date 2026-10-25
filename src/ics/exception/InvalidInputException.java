package ics.exception;

/**
 * Something the person TYPED is not acceptable: an empty required field, letters in an amount, a bad date.
 *
 * It remembers WHICH field was wrong, so the screen can put the cursor back in that field.
 */
public class InvalidInputException extends ICSException {

    private static final long serialVersionUID = 1L;

    private final String fieldName;    // for example "Full name" or "Claimed amount"

    public InvalidInputException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
