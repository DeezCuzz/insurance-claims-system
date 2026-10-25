package ics.model;

import ics.exception.InvalidInputException;
import ics.util.FileHelper;
import ics.util.Validate;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * An insurance policy issued to a User. You never make a plain Policy: it is a HealthPolicy or an AutoPolicy.
 *
 * WHY abstract: premium and payout are worked out differently for each type, so those methods are
 * "abstract" here and each child fills them in (polymorphism). Everything the two types share is written once here.
 *
 * policies.txt line: id | type | ownerId | start | end | deductible | coverageLimit | limitUsed | cancelled | extras...
 *                    (HEALTH extras: reimbursementRate | waitingPeriodDays | deductibleMet)
 *                    (AUTO extras:   vehicleValue | plateNo)
 */
public abstract class Policy extends Entity {

    /** The two kinds of policy. Saved in the file as HEALTH or AUTO. */
    public enum Type {
        HEALTH("Health"), AUTO("Auto");

        private final String label;
        Type(String label) { this.label = label; }
        /** The word shown on screens. */
        public String getLabel() { return label; }
    }

    /** Never typed in by anyone: worked out from the dates and the cancelled flag (see getStatus). */
    public enum Status {
        UPCOMING("Upcoming"), ACTIVE("Active"), EXPIRED("Expired"), CANCELLED("Cancelled");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    /** Fix E2: a deductible of this much or more earns a discount (the holder carries more of the risk). */
    public static final double DISCOUNT_DEDUCTIBLE = 10_000;
    public static final double DISCOUNT_RATE = 0.10;              // 10% off

    private String ownerId;          // the ID of the User account it belongs to (an ID, not the object)
    private LocalDate startDate;
    private LocalDate endDate;
    private double deductible;       // what the holder pays first, before the insurer pays
    private double coverageLimit;    // the most this policy will ever pay out
    private double limitUsed;        // how much it has paid out so far
    private boolean cancelled;

    protected Policy(String id, String ownerId, LocalDate startDate, LocalDate endDate, double deductible,
                     double coverageLimit, double limitUsed, boolean cancelled) throws InvalidInputException {
        super(id);
        this.ownerId = Validate.notBlank("Policy holder", ownerId);
        setPeriod(startDate, endDate);
        this.deductible = Validate.nonNegative("Deductible", deductible);
        this.coverageLimit = Validate.positive("Coverage limit", coverageLimit);
        this.limitUsed = Validate.between("Limit used", limitUsed, 0, coverageLimit);
        this.cancelled = cancelled;
    }

    /** Rebuilds a policy from one line of policies.txt. The type column decides Health or Auto. */
    public static Policy fromFields(String[] f) throws InvalidInputException {
        Type type = Type.valueOf(f[1]);
        LocalDate start = LocalDate.parse(f[3]);
        LocalDate end = LocalDate.parse(f[4]);
        double deductible = Double.parseDouble(f[5]);
        double limit = Double.parseDouble(f[6]);
        double used = Double.parseDouble(f[7]);
        boolean cancelled = Boolean.parseBoolean(f[8]);
        if (type == Type.HEALTH) {
            return new HealthPolicy(f[0], f[2], start, end, deductible, limit, used, cancelled,
                    Double.parseDouble(f[9]), Integer.parseInt(f[10]), Double.parseDouble(f[11]));
        }
        return new AutoPolicy(f[0], f[2], start, end, deductible, limit, used, cancelled,
                Double.parseDouble(f[9]), f[10]);
    }

    // ---- what each type works out differently (filled in by HealthPolicy and AutoPolicy) ----
    public abstract Type getType();

    /** How much the policy costs. */
    public abstract double calculatePremium();

    /** How much the insurer would pay for this claimed amount, given what was already paid. Changes nothing. */
    public abstract double calculatePayout(double claimedAmount);

    /** The same calculation written out step by step, for the Admin and the payout slip. Last line = the payout. */
    public abstract List<String> explainPayout(double claimedAmount);

    /**
     * The same calculation on ONE line, for the Admin before approving (fix C7), for example
     *   P25,000.00 - P5,000.00 = P20,000.00 x 80% = P16,000.00
     * currencySign = what to write before each amount (the screen passes the peso sign).
     */
    public abstract String payoutFormula(double claimedAmount, String currencySign);

    /** The extra columns only this type has (written after the shared columns). */
    protected abstract Object[] extraFields();

    // ---- shared behaviour ----

    /** Upcoming, Active, Expired or Cancelled, worked out from today's date. */
    public Status getStatus(LocalDate today) {
        if (cancelled) {
            return Status.CANCELLED;
        }
        if (today.isBefore(startDate)) {
            return Status.UPCOMING;               // the start date is still in the future
        }
        if (today.isAfter(endDate)) {
            return Status.EXPIRED;
        }
        return Status.ACTIVE;
    }

    public double getRemainingLimit() {
        return coverageLimit - limitUsed;
    }

    public void cancel() {
        cancelled = true;
    }

    /**
     * Call this AFTER money is really paid: it uses up part of the coverage limit.
     * HealthPolicy adds to it (it also counts how much deductible was met).
     */
    public void applyPayout(double claimedAmount, double paidAmount) throws InvalidInputException {
        Validate.between("Paid amount", paidAmount, 0, getRemainingLimit());
        limitUsed += paidAmount;
    }

    /** True when the deductible is high enough for the 10% premium discount (fix E2). */
    public boolean hasDeductibleDiscount() {
        return deductible >= DISCOUNT_DEDUCTIBLE;
    }

    /** Takes 10% off a premium when the deductible is high enough. Used by both policy types. */
    protected double withDeductibleDiscount(double basePremium) {
        if (hasDeductibleDiscount()) {
            return basePremium * (1 - DISCOUNT_RATE);
        }
        return basePremium;
    }

    /** Money as plain text for explanations and slips. ("PHP" prints on any printer, unlike the peso sign.) */
    protected static String php(double value) {
        return String.format(Locale.US, "PHP %,.2f", value);
    }

    /** An amount for payoutFormula(), for example "P20,000.00". */
    protected static String amount(String currencySign, double value) {
        return currencySign + String.format(Locale.US, "%,.2f", value);
    }

    /** Rounds to centavos, so 36000.000000001 becomes 36000.00. */
    protected static double roundToCents(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ---- getters (no public setters: a policy is changed by building a new one, see PolicyService.update) ----
    public String getOwnerId()       { return ownerId; }
    public LocalDate getStartDate()  { return startDate; }
    public LocalDate getEndDate()    { return endDate; }
    public double getDeductible()    { return deductible; }
    public double getCoverageLimit() { return coverageLimit; }
    public double getLimitUsed()     { return limitUsed; }
    public boolean isCancelled()     { return cancelled; }

    /** The end date must be after the start date. */
    private void setPeriod(LocalDate startDate, LocalDate endDate) throws InvalidInputException {
        Validate.notNull("Start date", startDate);
        Validate.notNull("End date", endDate);
        if (!endDate.isAfter(startDate)) {
            throw new InvalidInputException("End date", "End date must be after the start date.");
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public String toFileLine() {
        Object[] shared = {getId(), getType(), ownerId, startDate, endDate, deductible, coverageLimit, limitUsed, cancelled};
        Object[] extra = extraFields();
        Object[] all = new Object[shared.length + extra.length];
        System.arraycopy(shared, 0, all, 0, shared.length);          // shared columns first...
        System.arraycopy(extra, 0, all, shared.length, extra.length); // ...then this type's own columns
        return FileHelper.join(all);
    }
}
