package ics.exception;

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

    public static LoginException wrongPassword(int triesLeft) {
        return new LoginException("Wrong username or password.", triesLeft, false, false);
    }

    public static LoginException blocked(boolean justNow) {
        String message = justNow ? "Too many wrong passwords. This account is now blocked."
                                 : "This account is blocked. Ask an Admin to unblock it.";
        return new LoginException(message, 0, true, justNow);
    }

    public int getTriesLeft()     { return triesLeft; }
    public boolean isBlocked()    { return blocked; }
    public boolean isJustBlocked(){ return justBlocked; }
}
