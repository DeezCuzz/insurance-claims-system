package ics.model;

import ics.exception.InvalidInputException;
import ics.util.FileHelper;
import ics.util.Validate;

/**
 * Anyone who can log in. There are two kinds (the professor's rule): Admin and User.
 *
 * OOP shown here:
 *   - abstraction:   "abstract class" - you can only make an Admin or an Applicant, never a plain User
 *   - inheritance:   Admin and Applicant get all the fields and methods below for free
 *   - polymorphism:  canManageUsers(), canReviewClaims(), canApplyForClaims() give a different answer
 *                    for each kind, so the screens ask "what can you do?" and never check a role name
 *   - encapsulation: every field is private; it can only change through the methods below
 *
 * users.txt line:  id | role | username | passwordHash | fullName | contact | address | failedAttempts | blocked
 */
public abstract class User extends Entity {

    /** The two kinds of account. Saved in users.txt as ADMIN or USER. */
    public enum Role { ADMIN, USER }

    /** The third wrong password blocks the account (rubric II.C.6). */
    public static final int MAX_FAILED_ATTEMPTS = 3;

    private String username;
    private String passwordHash;   // the password is never saved as typed, only as a SHA-256 "fingerprint"
    private String fullName;
    private String contact;
    private String address;
    private int failedAttempts;    // wrong passwords in a row
    private boolean blocked;

    protected User(String id, String username, String passwordHash, String fullName, String contact,
                   String address, int failedAttempts, boolean blocked) throws InvalidInputException {
        super(id);
        setUsername(username);
        setPasswordHash(passwordHash);
        setFullName(fullName);
        setContact(contact);
        setAddress(address);
        this.failedAttempts = (int) Validate.nonNegative("Failed attempts", failedAttempts);
        this.blocked = blocked;
    }

    /**
     * Makes the right kind of account for a role. WHY one place: the Users screen and the file loader
     * both need this decision, and writing it once means they can never disagree.
     */
    public static User create(Role role, String id, String username, String passwordHash, String fullName,
                              String contact, String address, int failedAttempts,
                              boolean blocked) throws InvalidInputException {
        if (role == Role.ADMIN) {
            return new Admin(id, username, passwordHash, fullName, contact, address, failedAttempts, blocked);
        }
        return new Applicant(id, username, passwordHash, fullName, contact, address, failedAttempts, blocked);
    }

    /** Rebuilds an account from the fields of one line of users.txt. */
    public static User fromFields(String[] f) throws InvalidInputException {
        return create(Role.valueOf(f[1]), f[0], f[2], f[3], f[4], f[5], f[6],
                Integer.parseInt(f[7]), Boolean.parseBoolean(f[8]));
    }

    // ---- what each kind of account answers differently (polymorphism) ----
    public abstract Role getRole();
    public abstract boolean canManageUsers();
    public abstract boolean canReviewClaims();
    public abstract boolean canApplyForClaims();

    // ---- login helpers ----

    /** True if the typed password (already turned into a hash) matches the saved one. */
    public boolean verifyPassword(String typedHash) {
        return passwordHash.equals(typedHash);
    }

    /** Counts one wrong password; the third one blocks the account. */
    public void recordFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            blocked = true;
        }
    }

    /** A correct password clears the count. */
    public void resetFailedAttempts() {
        failedAttempts = 0;
    }

    /** An Admin switches the account off. */
    public void block() {
        blocked = true;
    }

    /** An Admin lets the account log in again. */
    public void unblock() {
        blocked = false;
        failedAttempts = 0;
    }

    // ---- getters and setters (the only way in and out of the private fields) ----
    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName()     { return fullName; }
    public String getContact()      { return contact; }
    public String getAddress()      { return address; }
    public int getFailedAttempts()  { return failedAttempts; }
    public boolean isBlocked()      { return blocked; }

    // "final" because the constructor calls these; a child class must not change how they work
    public final void setUsername(String username) throws InvalidInputException {
        this.username = Validate.notBlank("Username", username);
    }

    public final void setPasswordHash(String passwordHash) throws InvalidInputException {
        this.passwordHash = Validate.notBlank("Password", passwordHash);
    }

    public final void setFullName(String fullName) throws InvalidInputException {
        this.fullName = Validate.notBlank("Full name", fullName);
    }

    public final void setContact(String contact) throws InvalidInputException {
        this.contact = Validate.notBlank("Contact number", contact);
    }

    /** Address is optional. */
    public final void setAddress(String address) {
        this.address = address == null ? "" : address.trim();
    }

    @Override
    public String toFileLine() {
        return FileHelper.join(getId(), getRole(), username, passwordHash, fullName, contact, address,
                failedAttempts, blocked);
    }
}
