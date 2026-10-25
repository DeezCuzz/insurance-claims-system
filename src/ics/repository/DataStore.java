package ics.repository;

import ics.exception.DataStorageException;
import ics.model.Claim;
import ics.model.Payout;
import ics.model.Policy;
import ics.model.User;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * All the data of the system: four Repositories (four txt files) in one folder, plus a few searches.
 *
 * The services get one DataStore and never build file paths themselves.
 */
public class DataStore {

    private final Repository<User> users;
    private final Repository<Policy> policies;
    private final Repository<Claim> claims;
    private final Repository<Payout> payouts;

    public DataStore(Path folder) {
        // Each Repository is told: which file, which ID prefix, and how to read one line back.
        // "fields -> User.fromFields(fields)" is a lambda: a short way of passing that "how to read" method.
        users = new Repository<>(folder.resolve("users.txt"), "USR", fields -> User.fromFields(fields));
        policies = new Repository<>(folder.resolve("policies.txt"), "POL", fields -> Policy.fromFields(fields));
        claims = new Repository<>(folder.resolve("claims.txt"), "CLM", fields -> Claim.fromFields(fields));
        payouts = new Repository<>(folder.resolve("payouts.txt"), "PAY", fields -> Payout.fromFields(fields));
    }

    /** Call once when the program starts. */
    public void loadAll() throws DataStorageException {
        users.load();
        policies.load();
        claims.load();
        payouts.load();
    }

    /** Every line the last loadAll() had to skip, from all four files (fix D4). */
    public List<String> loadWarnings() {
        List<String> all = new ArrayList<>();
        all.addAll(users.getWarnings());
        all.addAll(policies.getWarnings());
        all.addAll(claims.getWarnings());
        all.addAll(payouts.getWarnings());
        return all;
    }

    public Repository<User> users()       { return users; }
    public Repository<Policy> policies()  { return policies; }
    public Repository<Claim> claims()     { return claims; }
    public Repository<Payout> payouts()   { return payouts; }

    // ---- searches several services need ----

    /** The account with this username (not case-sensitive), or null. */
    public User findUserByUsername(String username) {
        for (User u : users.getAll()) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                return u;
            }
        }
        return null;
    }

    /** Every policy issued to this account. */
    public List<Policy> policiesOf(String ownerId) {
        List<Policy> result = new ArrayList<>();
        for (Policy p : policies.getAll()) {
            if (p.getOwnerId().equals(ownerId)) {
                result.add(p);
            }
        }
        return result;
    }

    /** Every claim made on this policy. */
    public List<Claim> claimsOn(String policyId) {
        List<Claim> result = new ArrayList<>();
        for (Claim c : claims.getAll()) {
            if (c.getPolicyId().equals(policyId)) {
                result.add(c);
            }
        }
        return result;
    }

    /** The payout for this claim, or null if it was not paid. */
    public Payout payoutFor(String claimId) {
        for (Payout p : payouts.getAll()) {
            if (p.getClaimId().equals(claimId)) {
                return p;
            }
        }
        return null;
    }
}
