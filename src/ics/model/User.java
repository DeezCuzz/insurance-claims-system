package ics.model;

import ics.exception.*;
import ics.util.*;

public abstract class User extends Entity {
    public enum Role { ADMIN, USER }

    public static final int MAX_FAILED_ATTEMPTS = 3;

    private String username;
    private String passwordHash;
    private String fullName;
    private String contact;
    private String address;
    private int failedAttempts;
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

    public static User create(Role role, String id, String username, String passwordHash, String fullName,
                              String contact, String address, int failedAttempts,
                              boolean blocked) throws InvalidInputException {
        if (role == Role.ADMIN) {
            return new Admin(id, username, passwordHash, fullName, contact, address, failedAttempts, blocked);
        }
        return new Applicant(id, username, passwordHash, fullName, contact, address, failedAttempts, blocked);
    }

    public static User fromFields(String[] f) throws InvalidInputException {
        return create(Role.valueOf(f[1]), f[0], f[2], f[3], f[4], f[5], f[6],
                Integer.parseInt(f[7]), Boolean.parseBoolean(f[8]));
    }

    public abstract Role getRole();
    public abstract boolean canManageUsers();
    public abstract boolean canReviewClaims();
    public abstract boolean canApplyForClaims();

    public boolean verifyPassword(String typedHash) {
        return passwordHash.equals(typedHash);
    }

    public void recordFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            blocked = true;
        }
    }

    public void resetFailedAttempts() {
        failedAttempts = 0;
    }

    public void block() {
        blocked = true;
    }

    public void unblock() {
        blocked = false;
        failedAttempts = 0;
    }

    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName()     { return fullName; }
    public String getContact()      { return contact; }
    public String getAddress()      { return address; }
    public int getFailedAttempts()  { return failedAttempts; }
    public boolean isBlocked()      { return blocked; }

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

    public final void setAddress(String address) {
        this.address = address == null ? "" : address.trim();
    }

    @Override
    public String toFileLine() {
        return FileHelper.join(getId(), getRole(), username, passwordHash, fullName, contact, address,
                failedAttempts, blocked);
    }
}
