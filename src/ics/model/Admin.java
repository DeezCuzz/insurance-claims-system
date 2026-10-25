package ics.model;

import ics.exception.InvalidInputException;

/** Creates accounts, issues policies, reviews and pays claims, unblocks accounts. */
public class Admin extends User {

    public Admin(String id, String username, String passwordHash, String fullName, String contact,
                 String address, int failedAttempts, boolean blocked) throws InvalidInputException {
        super(id, username, passwordHash, fullName, contact, address, failedAttempts, blocked);
    }

    @Override public Role getRole()              { return Role.ADMIN; }
    @Override public boolean canManageUsers()    { return true; }
    @Override public boolean canReviewClaims()   { return true; }
    @Override public boolean canApplyForClaims() { return false; }
}
