package ics.service;

import ics.exception.DataStorageException;
import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.exception.RuleException;
import ics.model.AutoPolicy;
import ics.model.HealthPolicy;
import ics.model.Policy;
import ics.model.User;
import ics.repository.DataStore;
import ics.util.Validate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Policies for the Admin (the Policies screen). The rules live HERE, not on the screen:
 *   - a policy can only be issued to a User account
 *   - the end date must be after the start date, and the deductible must be lower than the limit
 *   - no double insurance: Auto is checked by plate, Health by holder (cancelled policies are ignored)
 *   - a limit cannot drop below what was already paid out
 *   - cancelling or deleting needs the Admin password again (rubric II.C.4)
 *   - a policy that has claims can be cancelled but not deleted
 */
public class PolicyService {

    private final DataStore store;
    private final AuthService auth;

    public PolicyService(DataStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    // ------------------------------------------------------------------ reading

    /** The accounts a policy can be issued to (Users, not Admins). */
    public List<User> policyHolders() {
        List<User> result = new ArrayList<>();
        for (User u : store.users().getAll()) {
            if (u.canApplyForClaims()) {
                result.add(u);
            }
        }
        return result;
    }

    /** The username of a policy's holder, for tables. */
    public String holderName(String ownerId) {
        User owner = store.users().findById(ownerId);
        return owner == null ? "(removed)" : owner.getUsername();
    }

    /** Policies whose ID, holder, type or status contains the keyword. A blank keyword lists all. */
    public List<Policy> search(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        LocalDate today = LocalDate.now();
        List<Policy> result = new ArrayList<>();
        for (Policy p : store.policies().getAll()) {
            User owner = store.users().findById(p.getOwnerId());
            String ownerText = owner == null ? "" : owner.getUsername() + " " + owner.getFullName();
            String text = (p.getId() + " " + ownerText + " " + p.getType().getLabel() + " "
                    + p.getStatus(today).getLabel()).toLowerCase();
            if (k.isEmpty() || text.contains(k)) {
                result.add(p);
            }
        }
        return result;
    }

    public int countPolicies() {
        return store.policies().getAll().size();
    }

    /**
     * The premium for the live preview while the Admin types.
     * WHY a throw-away policy: the premium formula lives only in the policy classes, so we ask one.
     */
    public double previewPremium(Policy.Type type, String coverageLimit, String vehicleValue,
                                 String deductibleText) throws InvalidInputException {
        LocalDate today = LocalDate.now();
        // the deductible only decides the discount; while it is still empty or wrong, show the full price
        double deductible = 0;
        try {
            deductible = Math.max(0, Validate.parseAmount("Deductible", deductibleText));
        } catch (InvalidInputException notTypedYet) {
            deductible = 0;
        }
        if (type == Policy.Type.HEALTH) {
            double limit = Validate.parseAmount("Coverage limit", coverageLimit);
            return new HealthPolicy(null, "preview", today, today.plusYears(1), Math.min(deductible, limit), limit, 0,
                    false, 1.0, 0, 0).calculatePremium();
        }
        double vehicle = Validate.parseAmount("Vehicle value", vehicleValue);
        return new AutoPolicy(null, "preview", today, today.plusYears(1), deductible, deductible + 1, 0, false, vehicle,
                "preview").calculatePremium();
    }

    // ------------------------------------------------------------------ issue and edit

    /**
     * Issues a new policy. Every value is the text typed on the screen; it is checked here.
     * reimbursementPercent and waitingDays are for Health; vehicleValue and plateNo are for Auto.
     */
    public Policy issue(Policy.Type type, String ownerId, String startDate, String endDate, String deductible,
                        String coverageLimit, String reimbursementPercent, String waitingDays,
                        String vehicleValue, String plateNo) throws ICSException {
        Policy policy = build(null, type, ownerId, startDate, endDate, deductible, coverageLimit,
                reimbursementPercent, waitingDays, vehicleValue, plateNo, 0, 0);
        store.policies().add(policy);              // gives it the next ID (POL-000X) and saves
        return policy;
    }

    /**
     * Changes a policy's terms. The holder and type stay the same, and so do the amounts already used.
     * WHY build a new object: if one value is wrong, the saved policy is not half-changed.
     */
    public Policy update(String id, String startDate, String endDate, String deductible, String coverageLimit,
                         String reimbursementPercent, String waitingDays, String vehicleValue,
                         String plateNo) throws ICSException {
        Policy old = find(id);
        if (old.isCancelled()) {                                            // fix B6
            throw new RuleException("A cancelled policy cannot be edited.");
        }
        if (old.getStatus(LocalDate.now()) == Policy.Status.EXPIRED) {      // fix B6
            throw new RuleException("An expired policy cannot be edited.");
        }
        double deductibleMet = 0;
        if (old instanceof HealthPolicy) {
            deductibleMet = ((HealthPolicy) old).getDeductibleMet();
        }
        Policy updated = build(id, old.getType(), old.getOwnerId(), startDate, endDate, deductible, coverageLimit,
                reimbursementPercent, waitingDays, vehicleValue, plateNo, old.getLimitUsed(), deductibleMet);
        store.policies().update(updated);
        return updated;
    }

    // ------------------------------------------------------------------ cancel and delete (Admin password)

    /** Stops the policy from covering new claims. The record and its history stay. */
    public void cancel(String id, User admin, String adminPassword) throws ICSException {
        auth.confirmPassword(admin, adminPassword);
        Policy policy = find(id);
        if (policy.isCancelled()) {
            throw new RuleException("This policy is already cancelled.");
        }
        policy.cancel();
        store.policies().update(policy);
    }

    /** Removes a policy for good, but only if no claim was ever made on it. */
    public void delete(String id, User admin, String adminPassword) throws ICSException {
        auth.confirmPassword(admin, adminPassword);
        find(id);
        if (!store.claimsOn(id).isEmpty()) {                               // fix B7: the claims keep their history
            throw new RuleException("This policy has claims. Cancel it instead.");
        }
        store.policies().delete(id);
    }

    // ------------------------------------------------------------------ the checks
    /**
     * Checks everything typed, then builds the policy. The order follows fix A7:
     *   1. the form's own fields (holder, dates, amounts, the Health or Auto fields)
     *   2. only then the other policies (the overlap check), because that check needs good values to compare
     * The first problem found is the one shown, and the cursor goes to that field.
     */
    private Policy build(String id, Policy.Type type, String ownerId, String startDate, String endDate,
                         String deductibleText, String limitText, String percentText, String waitingText,
                         String vehicleText, String plateText, double limitUsed,
                         double deductibleMet) throws InvalidInputException {
        // ---- 1a. who and when ----
        Validate.notNull("Policy type", type);
        Validate.notBlank("Policy holder", ownerId);
        User owner = store.users().findById(ownerId);
        if (owner == null) {
            throw new InvalidInputException("Policy holder", "Choose a policy holder.");
        }
        if (!owner.canApplyForClaims()) {                                  // fix A9: never an Admin
            throw new InvalidInputException("Policy holder", "Policies can only be issued to a User account.");
        }
        LocalDate start = Validate.parseDate("Start date", startDate);
        LocalDate end = Validate.parseDate("End date", endDate);
        if (!end.isAfter(start)) {
            throw new InvalidInputException("End date", "End date must be after the start date.");
        }
        if (end.isAfter(start.plusYears(1))) {                             // fix A5: at most one year
            throw new InvalidInputException("End date", "A policy can be at most 1 year long. The latest end date is "
                    + start.plusYears(1) + ".");
        }

        // ---- 1b. the amounts ----
        double deductible = Validate.nonNegative("Deductible", Validate.parseAmount("Deductible", deductibleText));
        double limit = Validate.positive("Coverage limit", Validate.parseAmount("Coverage limit", limitText));
        if (deductible >= limit) {
            throw new InvalidInputException("Deductible", "Deductible must be lower than the coverage limit.");
        }
        if (limit < limitUsed) {
            throw new InvalidInputException("Coverage limit",
                    "Coverage limit cannot be lower than the " + money(limitUsed) + " already paid out.");
        }

        // ---- 1c. the fields only one type has ----
        double percent = 0;
        int waiting = 0;
        double vehicle = 0;
        String plate = "";
        if (type == Policy.Type.HEALTH) {
            percent = Validate.between("Reimbursement (%)", Validate.parseAmount("Reimbursement (%)", percentText), 1, 100);
            waiting = (int) Validate.between("Waiting period (days)",
                    Validate.parseWholeNumber("Waiting period (days)", waitingText), 0, 365);
            if (deductibleMet > deductible) {
                throw new InvalidInputException("Deductible",
                        "Deductible cannot be lower than the " + money(deductibleMet) + " already met.");
            }
        } else {
            vehicle = Validate.positive("Vehicle value", Validate.parseAmount("Vehicle value", vehicleText));
            if (limit > vehicle) {                                         // a car cannot be insured for more than it is worth
                throw new InvalidInputException("Coverage limit", "Coverage limit cannot be higher than the vehicle value ("
                        + money(vehicle) + ").");
            }
            // trimmed and in capitals, so "5xc1jx " and "5XC1JX" count as the same plate
            plate = Validate.plainText("Plate number", Validate.notBlank("Plate number", plateText), 15).toUpperCase();
        }

        // ---- 2. only now: compare with the other policies ----
        checkOverlap(id, type, owner, plate, start, end);

        if (type == Policy.Type.HEALTH) {
            return new HealthPolicy(id, ownerId, start, end, deductible, limit, limitUsed, false,
                    percent / 100.0, waiting, deductibleMet);
        }
        return new AutoPolicy(id, ownerId, start, end, deductible, limit, limitUsed, false, vehicle, plate);
    }

    /**
     * No double insurance for the same dates (fixes A1 to A4, A6).
     *   Auto:   checked by PLATE, whoever the holder is (one person may own two cars; one car has one policy)
     *   Health: checked by HOLDER (one person, one Health policy at a time)
     * Cancelled policies and the policy being edited are skipped.
     * Two periods overlap when: newStart <= otherEnd AND newEnd >= otherStart.
     */
    private void checkOverlap(String id, Policy.Type type, User owner, String plate, LocalDate start,
                              LocalDate end) throws InvalidInputException {
        for (Policy other : store.policies().getAll()) {
            if (other.getType() != type || other.isCancelled() || other.getId().equals(id)) {
                continue;                                                  // fix A3 (cancelled) and A4 (itself)
            }
            boolean overlaps = !start.isAfter(other.getEndDate()) && !end.isBefore(other.getStartDate());
            if (!overlaps) {
                continue;
            }
            if (type == Policy.Type.AUTO && ((AutoPolicy) other).getPlateNo().equalsIgnoreCase(plate)) {
                throw new InvalidInputException("Plate number", "Plate " + plate + " is already insured under "
                        + other.getId() + " for these dates.");
            }
            if (type == Policy.Type.HEALTH && other.getOwnerId().equals(owner.getId())) {
                throw new InvalidInputException("Start date", owner.getUsername() + " already has a Health policy ("
                        + other.getId() + ") for these dates.");
            }
        }
    }

    private Policy find(String id) throws DataStorageException {
        Policy policy = store.policies().findById(id);
        if (policy == null) {
            throw new DataStorageException("Policy not found: " + id);
        }
        return policy;
    }

    private static String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }
}
