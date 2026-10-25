package ics.exception;

/**
 * Logging in failed. There are two cases, and the login screen treats them differently:
 *
 *   wrongPassword(triesLeft)  wrong username or password; triesLeft is how many tries the account has left
 *                             (-1 when the username does not exist, so there is nothing to count)
 *   blocked(justNow)          the account is blocked; justNow = true if THIS try was the third wrong one
 *
 * The message never says whether the username or the password was wrong, so nobody can use the
 * login screen to find out which usernames exist.
 */
public class LoginException extends ICSException {

    private static final long serialVersionUID = 1L;

    private final int triesLeft;
    private final boolean blocked;
    private final boolean justBlocked;

    private LoginException(String message, int triesLeft, boolean blocked, boolean justBlocked) {
        super(message);
        this.triesLeft = triesLeft;
        this.blocked = blocked;
        this.justBlocked = justBlocked;
    }

    /** Wrong username or password. */
    public static LoginException wrongPassword(int triesLeft) {
        return new LoginException("Wrong username or password.", triesLeft, false, false);
    }

    /** The account is blocked. justNow = this try used up the last chance. */
    public static LoginException blocked(boolean justNow) {
        String message = justNow ? "Too many wrong passwords. This account is now blocked."
                                 : "This account is blocked. Ask an Admin to unblock it.";
        return new LoginException(message, 0, true, justNow);
    }

    public int getTriesLeft()     { return triesLeft; }
    public boolean isBlocked()    { return blocked; }
    public boolean isJustBlocked(){ return justBlocked; }
}
