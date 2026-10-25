package ics.service;

import ics.exception.AccessDeniedException;
import ics.exception.DataStorageException;
import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.exception.RuleException;
import ics.model.Claim;
import ics.model.Payout;
import ics.model.User;
import ics.repository.DataStore;
import ics.util.Validate;

import java.util.ArrayList;
import java.util.List;

/**
 * Account management for the Admin (the Users screen).
 * Accounts are created ONLY here: there is no sign-up on the login screen (the professor's rule).
 */
public class UserService {

    private final DataStore store;
    private final AuthService auth;

    public UserService(DataStore store) {
        this.store = store;
        this.auth = new AuthService(store);
    }

    /** Accounts whose ID, username, full name or contact contains the keyword. A blank keyword lists everyone. */
    public List<User> search(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<User> result = new ArrayList<>();
        for (User u : store.users().getAll()) {
            String text = (u.getId() + " " + u.getUsername() + " " + u.getFullName() + " " + u.getContact()).toLowerCase();
            if (k.isEmpty() || text.contains(k)) {
                result.add(u);
            }
        }
        return result;
    }

    public int countUsers() {
        return store.users().getAll().size();
    }

    /**
     * Creates an account (rubric II.C.2 and II.C.5). The checks follow the form from top to bottom,
     * so the first error shown is always the first field to fix.
     */
    public User createAccount(User.Role role, String username, String password, String confirmPassword,
                              String fullName, String contact, String address) throws ICSException {
        Validate.notNull("Role", role);
        String name = Validate.username("Username", username);
        if (store.findUserByUsername(name) != null) {
            throw new InvalidInputException("Username", "That username is already taken.");
        }
        checkProfile(fullName, contact, address);
        Validate.strongPassword("Password", password);
        if (!password.equals(confirmPassword)) {
            throw new InvalidInputException("Confirm password", "The two passwords do not match.");
        }
        User user = User.create(role, null, name, AuthService.hash(password), fullName, contact, address, 0, false);
        store.users().add(user);                   // gives it the next ID (USR-000X) and saves
        return user;
    }

    /**
     * Changes the name, contact and address. Username, role and password stay the same.
     * WHY build a new object: if one field is wrong, the saved account is not half-changed.
     */
    public void updateProfile(String id, String fullName, String contact, String address) throws ICSException {
        User old = find(id);
        checkProfile(fullName, contact, address);
        User updated = User.create(old.getRole(), id, old.getUsername(), old.getPasswordHash(), fullName, contact,
                address, old.getFailedAttempts(), old.isBlocked());
        store.users().update(updated);
    }

    /** Lets a blocked account log in again (rubric II.C.6: only an Admin can do this). */
    public void unblock(String id) throws ICSException {
        User user = find(id);
        user.unblock();
        store.users().update(user);
    }

    /** Switches an account off. Nothing is deleted; Unblock brings it back. */
    public void deactivate(String id, User admin) throws ICSException {
        requireAdmin(admin);
        User user = find(id);
        protectAdmins(user, admin, "deactivate");
        if (user.isBlocked()) {
            throw new RuleException("This account is already blocked.");
        }
        user.block();
        store.users().update(user);
    }

    /**
     * Removes an account for good, after the Admin types their password again (rubric II.C.4).
     * Accounts that are part of the records are kept: a User with policies, or an Admin who decided
     * claims or released payments. Those can only be deactivated.
     */
    public void delete(String id, User admin, String adminPassword) throws ICSException {
        requireAdmin(admin);
        auth.confirmPassword(admin, adminPassword);
        User user = find(id);
        protectAdmins(user, admin, "delete");

        int policies = store.policiesOf(id).size();
        if (policies > 0) {
            throw new RuleException("This account has " + policies + (policies == 1 ? " policy" : " policies")
                    + ", so it is kept for the record. Deactivate it instead.");
        }
        boolean decidedSomething = false;
        for (Claim c : store.claims().getAll()) {
            if (id.equals(c.getHandledBy())) {
                decidedSomething = true;
            }
        }
        for (Payout p : store.payouts().getAll()) {
            if (id.equals(p.getPaidBy())) {
                decidedSomething = true;
            }
        }
        if (decidedSomething) {
            throw new RuleException("This Admin decided claims or released payments, so the account is kept "
                    + "for the record. Deactivate it instead.");
        }
        store.users().delete(id);
    }

    // ---- private helpers ----

    /**
     * The checks for the profile fields. They are HERE (when typing) and not in the User class, so old records
     * saved before these rules existed still load.
     */
    private static void checkProfile(String fullName, String contact, String address) throws InvalidInputException {
        Validate.plainText("Full name", Validate.notBlank("Full name", fullName), 60);
        Validate.plainText("Contact number", Validate.notBlank("Contact number", contact), 30);
        Validate.plainText("Address", address, 120);
    }

    private User find(String id) throws DataStorageException {
        User user = store.users().findById(id);
        if (user == null) {
            throw new DataStorageException("User not found: " + id);
        }
        return user;
    }

    private static void requireAdmin(User admin) throws AccessDeniedException {
        if (admin == null || !admin.canManageUsers()) {
            throw new AccessDeniedException("Only an Admin can do this.");
        }
    }

    /** Nobody locks themselves out, and there is always at least one Admin who can log in. */
    private void protectAdmins(User target, User admin, String action) throws RuleException {
        if (target.getId().equals(admin.getId())) {
            throw new RuleException("You cannot " + action + " your own account.");
        }
        if (target.canManageUsers() && !target.isBlocked()) {
            int otherActiveAdmins = 0;
            for (User u : store.users().getAll()) {
                if (u.canManageUsers() && !u.isBlocked() && !u.getId().equals(target.getId())) {
                    otherActiveAdmins++;
                }
            }
            if (otherActiveAdmins == 0) {
                throw new RuleException("This is the last Admin who can log in, so it cannot be "
                        + (action.equals("delete") ? "deleted" : "deactivated") + ".");
            }
        }
    }
}
