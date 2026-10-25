package ics.model;

import ics.exception.InvalidInputException;

/**
 * The "User" in the professor's words: the person who applies for a claim.
 * The Admin creates this account. An Applicant only sees their own policies and claims.
 */
public class Applicant extends User {

    public Applicant(String id, String username, String passwordHash, String fullName, String contact,
                     String address, int failedAttempts, boolean blocked) throws InvalidInputException {
        super(id, username, passwordHash, fullName, contact, address, failedAttempts, blocked);
    }

    @Override public Role getRole()              { return Role.USER; }
    @Override public boolean canManageUsers()    { return false; }
    @Override public boolean canReviewClaims()   { return false; }
    @Override public boolean canApplyForClaims() { return true; }
}
