package ics.service;

import ics.exception.*;
import ics.model.*;
import ics.repository.*;
import ics.util.*;
import java.nio.charset.*;
import java.security.*;

public class AuthService {
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin123";

    private final DataStore store;

    public AuthService(DataStore store) {
        this.store = store;
    }

    public static String hash(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }

    public boolean ensureFirstAdmin() throws ICSException {
        if (!store.users().getAll().isEmpty()) {
            return false;
        }
        store.users().add(new Admin(null, DEFAULT_ADMIN_USERNAME, hash(DEFAULT_ADMIN_PASSWORD),
                "System Administrator", "Not set", "", 0, false));
        return true;
    }

    public User login(String username, String password) throws ICSException {
        String name = username == null ? "" : username.trim();
        String typed = password == null ? "" : password;

        User user = store.findUserByUsername(name);
        if (user == null) {
            throw LoginException.wrongPassword(-1);
        }
        if (user.isBlocked()) {
            throw LoginException.blocked(false);
        }
        if (!user.verifyPassword(hash(typed))) {
            user.recordFailedAttempt();
            store.users().update(user);
            if (user.isBlocked()) {
                throw LoginException.blocked(true);
            }
            throw LoginException.wrongPassword(User.MAX_FAILED_ATTEMPTS - user.getFailedAttempts());
        }
        if (user.getFailedAttempts() > 0) {
            user.resetFailedAttempts();
            store.users().update(user);
        }
        return user;
    }

    public void changePassword(User user, String currentPassword, String newPassword,
                               String confirmPassword) throws ICSException {
        User saved = store.users().findById(user.getId());
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

    public boolean isUsingDefaultPassword(User user) {
        User saved = store.users().findById(user.getId());
        return saved != null && saved.verifyPassword(hash(DEFAULT_ADMIN_PASSWORD));
    }

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
