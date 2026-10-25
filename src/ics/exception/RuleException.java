package ics.exception;

/**
 * The input is fine, but a RULE of the system says no.
 * Examples: "This policy is already cancelled", "A claim cannot move from FILED to PAID",
 * "This account has policies, so it cannot be deleted".
 */
public class RuleException extends ICSException {

    private static final long serialVersionUID = 1L;

    public RuleException(String message) {
        super(message);
    }
}
