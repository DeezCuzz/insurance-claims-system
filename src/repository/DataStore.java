package ics.repository;

import ics.exception.*;
import ics.model.*;
import java.nio.file.*;
import java.util.*;

public class DataStore {
    private final Repository<User> users;
    private final Repository<Policy> policies;
    private final Repository<Claim> claims;
    private final Repository<Payout> payouts;

    public DataStore(Path folder) {
        users = new Repository<>(folder.resolve("users.txt"), "USR", fields -> User.fromFields(fields));
        policies = new Repository<>(folder.resolve("policies.txt"), "POL", fields -> Policy.fromFields(fields));
        claims = new Repository<>(folder.resolve("claims.txt"), "CLM", fields -> Claim.fromFields(fields));
        payouts = new Repository<>(folder.resolve("payouts.txt"), "PAY", fields -> Payout.fromFields(fields));
    }

    public void loadAll() throws DataStorageException {
        users.load();
        policies.load();
        claims.load();
        payouts.load();
    }

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

    public User findUserByUsername(String username) {
        for (User u : users.getAll()) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                return u;
            }
        }
        return null;
    }

    public List<Policy> policiesOf(String ownerId) {
        List<Policy> result = new ArrayList<>();
        for (Policy p : policies.getAll()) {
            if (p.getOwnerId().equals(ownerId)) {
                result.add(p);
            }
        }
        return result;
    }

    public List<Claim> claimsOn(String policyId) {
        List<Claim> result = new ArrayList<>();
        for (Claim c : claims.getAll()) {
            if (c.getPolicyId().equals(policyId)) {
                result.add(c);
            }
        }
        return result;
    }

    public Payout payoutFor(String claimId) {
        for (Payout p : payouts.getAll()) {
            if (p.getClaimId().equals(claimId)) {
                return p;
            }
        }
        return null;
    }
}
