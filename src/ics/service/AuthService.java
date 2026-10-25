package ics.service;

import ics.exception.AccessDeniedException;
import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.exception.LoginException;
import ics.model.Admin;
import ics.model.User;
import ics.repository.DataStore;
import ics.util.Validate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Logging in and passwords (rubric II.C).
 *
 *   - three wrong passwords block an account; the count is saved right away, so restarting gives no new tries
 *   - a blocked account cannot log in, even with the right password, until an Admin unblocks it
 *   - only an Admin creates accounts, so the very first run creates one Admin (ensureFirstAdmin)
 */
public class AuthService {

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin123";

    private final DataStore store;

    public AuthService(DataStore store) {
        this.store = store;
    }

    /**
     * Turns a password into its SHA-256 "fingerprint" (64 letters and digits).
     * WHY: we never save passwords as typed. To check a login we hash what was typed and compare fingerprints.
     */
    public static String hash(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));     // each byte as 2 hexadecimal digits
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }

    /** First run only: if there are no accounts at all, create the first Admin. Returns true if it did. */
    public boolean ensureFirstAdmin() throws ICSException {
        if (!store.users().getAll().isEmpty()) {
            return false;
        }
        store.users().add(new Admin(null, DEFAULT_ADMIN_USERNAME, hash(DEFAULT_ADMIN_PASSWORD),
                "System Administrator", "Not set", "", 0, false));
        return true;
    }

    /** Checks a username and password. Returns the account, or throws LoginException (wrong or blocked). */
    public User login(String username, String password) throws ICSException {
        String name = username == null ? "" : username.trim();
        String typed = password == null ? "" : password;

        User user = store.findUserByUsername(name);
        if (user == null) {
            throw LoginException.wrongPassword(-1);              // unknown username: nothing to count
        }
        if (user.isBlocked()) {
            throw LoginException.blocked(false);
        }
        if (!user.verifyPassword(hash(typed))) {
            user.recordFailedAttempt();
            store.users().update(user);                          // save the count now, not when the program closes
            if (user.isBlocked()) {
                throw LoginException.blocked(true);
            }
            throw LoginException.wrongPassword(User.MAX_FAILED_ATTEMPTS - user.getFailedAttempts());
        }
        if (user.getFailedAttempts() > 0) {
            user.resetFailedAttempts();                          // a correct password clears earlier wrong tries
            store.users().update(user);
        }
        return user;
    }

    /** Anyone changes their own password (rubric II.C.3). Checked in the same order as the form. */
    public void changePassword(User user, String currentPassword, String newPassword,
                               String confirmPassword) throws ICSException {
        User saved = store.users().findById(user.getId());   // the saved copy, in case the profile changed meanwhile
        if (saved == null) {
            throw new InvalidInputException("Current password", "This account no longer exists.");
        }
        Validate.notBlank("Current password", currentPassword);
        if (!saved.verifyPassword(hash(currentPassword))) {
            throw new InvalidInputException("Current password", "Current password is not correct.");
        }
        Validate.strongPassword("New password", newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new InvalidInputException("New password", "New password must be different from the current one.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new InvalidInputException("Confirm new password", "The two new passwords do not match.");
        }
        saved.setPasswordHash(hash(newPassword));
        store.users().update(saved);
    }

    /** True while this account still has the default password (everyone can read it in the README). */
    public boolean isUsingDefaultPassword(User user) {
        User saved = store.users().findById(user.getId());
        return saved != null && saved.verifyPassword(hash(DEFAULT_ADMIN_PASSWORD));
    }

    /**
     * Before a risky action (delete, cancel, pay) the Admin types their password again (rubric II.C.4).
     * WHY look up the saved account: the screens keep the account object from the moment of login, and editing
     * the profile or changing the password can make that object out of date.
     * A wrong password here does NOT count toward the three-strike block.
     */
    public void confirmPassword(User admin, String typedPassword) throws AccessDeniedException {
        User saved = admin == null ? null : store.users().findById(admin.getId());
        if (saved == null || !saved.canManageUsers() || saved.isBlocked()) {
            throw new AccessDeniedException("Only an Admin can do this.");
        }
        if (!saved.verifyPassword(hash(typedPassword == null ? "" : typedPassword))) {
            throw new AccessDeniedException("That password is not correct.");
        }
    }
}
