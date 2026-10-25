package ics.exception;

/**
 * The person is not allowed to do this (for example a User trying to approve a claim),
 * or typed the wrong password when the system asked them to confirm a risky action.
 */
public class AccessDeniedException extends ICSException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }
}
