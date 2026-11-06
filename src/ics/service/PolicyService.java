package ics.service;

import ics.exception.*;
import ics.model.*;
import ics.repository.*;
import ics.util.*;
import java.time.*;
import java.util.*;

public class PolicyService {
    private final DataStore store;
    private final AuthService auth;

    public PolicyService(DataStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    public List<User> policyHolders() {
        List<User> result = new ArrayList<>();
        for (User u : store.users().getAll()) {
            if (u.canApplyForClaims()) {
                result.add(u);
            }
        }
        return result;
    }

    public String holderName(String ownerId) {
        User owner = store.users().findById(ownerId);
        return owner == null ? "(removed)" : owner.getUsername();
    }

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

    public double previewPremium(Policy.Type type, String coverageLimit, String vehicleValue,
                                 String deductibleText) throws InvalidInputException {
        LocalDate today = LocalDate.now();

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

    public Policy issue(Policy.Type type, String ownerId, String startDate, String endDate, String deductible,
                        String coverageLimit, String reimbursementPercent, String waitingDays,
                        String vehicleValue, String plateNo) throws ICSException {
        Policy policy = build(null, type, ownerId, startDate, endDate, deductible, coverageLimit,
                reimbursementPercent, waitingDays, vehicleValue, plateNo, 0, 0);
        store.policies().add(policy);
        return policy;
    }

    public Policy update(String id, String startDate, String endDate, String deductible, String coverageLimit,
                         String reimbursementPercent, String waitingDays, String vehicleValue,
                         String plateNo) throws ICSException {
        Policy old = find(id);
        if (old.isCancelled()) {
            throw new RuleException("A cancelled policy cannot be edited.");
        }
        if (old.getStatus(LocalDate.now()) == Policy.Status.EXPIRED) {
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

    public void cancel(String id, User admin, String adminPassword) throws ICSException {
        auth.confirmPassword(admin, adminPassword);
        Policy policy = find(id);
        if (policy.isCancelled()) {
            throw new RuleException("This policy is already cancelled.");
        }
        policy.cancel();
        store.policies().update(policy);
    }

    public void delete(String id, User admin, String adminPassword) throws ICSException {
        auth.confirmPassword(admin, adminPassword);
        find(id);
        if (!store.claimsOn(id).isEmpty()) {
            throw new RuleException("This policy has claims. Cancel it instead.");
        }
        store.policies().delete(id);
    }

    private Policy build(String id, Policy.Type type, String ownerId, String startDate, String endDate,
                         String deductibleText, String limitText, String percentText, String waitingText,
                         String vehicleText, String plateText, double limitUsed,
                         double deductibleMet) throws InvalidInputException {
        Validate.notNull("Policy type", type);
        Validate.notBlank("Policy holder", ownerId);
        User owner = store.users().findById(ownerId);
        if (owner == null) {
            throw new InvalidInputException("Policy holder", "Choose a policy holder.");
        }
        if (!owner.canApplyForClaims()) {
            throw new InvalidInputException("Policy holder", "Policies can only be issued to a User account.");
        }
        LocalDate start = Validate.parseDate("Start date", startDate);
        LocalDate end = Validate.parseDate("End date", endDate);
        if (!end.isAfter(start)) {
            throw new InvalidInputException("End date", "End date must be after the start date.");
        }
        if (end.isAfter(start.plusYears(1))) {
            throw new InvalidInputException("End date", "A policy can be at most 1 year long. The latest end date is "
                    + start.plusYears(1) + ".");
        }

        double deductible = Validate.nonNegative("Deductible", Validate.parseAmount("Deductible", deductibleText));
        double limit = Validate.positive("Coverage limit", Validate.parseAmount("Coverage limit", limitText));
        if (deductible >= limit) {
            throw new InvalidInputException("Deductible", "Deductible must be lower than the coverage limit.");
        }
        if (limit < limitUsed) {
            throw new InvalidInputException("Coverage limit",
                    "Coverage limit cannot be lower than the " + money(limitUsed) + " already paid out.");
        }

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
            if (limit > vehicle) {
                throw new InvalidInputException("Coverage limit", "Coverage limit cannot be higher than the vehicle value ("
                        + money(vehicle) + ").");
            }

            plate = Validate.plainText("Plate number", Validate.notBlank("Plate number", plateText), 15).toUpperCase();
        }

        checkOverlap(id, type, owner, plate, start, end);

        if (type == Policy.Type.HEALTH) {
            return new HealthPolicy(id, ownerId, start, end, deductible, limit, limitUsed, false,
                    percent / 100.0, waiting, deductibleMet);
        }
        return new AutoPolicy(id, ownerId, start, end, deductible, limit, limitUsed, false, vehicle, plate);
    }

    private void checkOverlap(String id, Policy.Type type, User owner, String plate, LocalDate start,
                              LocalDate end) throws InvalidInputException {
        for (Policy other : store.policies().getAll()) {
            if (other.getType() != type || other.isCancelled() || other.getId().equals(id)) {
                continue;
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
