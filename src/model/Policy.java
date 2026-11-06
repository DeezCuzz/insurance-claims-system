package ics.model;

import ics.exception.*;
import ics.util.*;
import java.time.*;
import java.util.*;

public abstract class Policy extends Entity {
    public enum Type {
        HEALTH("Health"), AUTO("Auto");

        private final String label;
        Type(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    public enum Status {
        UPCOMING("Upcoming"), ACTIVE("Active"), EXPIRED("Expired"), CANCELLED("Cancelled");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public static final double DISCOUNT_DEDUCTIBLE = 10_000;
    public static final double DISCOUNT_RATE = 0.10;

    private String ownerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private double deductible;
    private double coverageLimit;
    private double limitUsed;
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

    public abstract Type getType();

    public abstract double calculatePremium();

    public abstract double calculatePayout(double claimedAmount);

    public abstract List<String> explainPayout(double claimedAmount);

    public abstract String payoutFormula(double claimedAmount, String currencySign);

    protected abstract Object[] extraFields();

    public Status getStatus(LocalDate today) {
        if (cancelled) {
            return Status.CANCELLED;
        }
        if (today.isBefore(startDate)) {
            return Status.UPCOMING;
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

    public void applyPayout(double claimedAmount, double paidAmount) throws InvalidInputException {
        Validate.between("Paid amount", paidAmount, 0, getRemainingLimit());
        limitUsed += paidAmount;
    }

    public boolean hasDeductibleDiscount() {
        return deductible >= DISCOUNT_DEDUCTIBLE;
    }

    protected double withDeductibleDiscount(double basePremium) {
        if (hasDeductibleDiscount()) {
            return basePremium * (1 - DISCOUNT_RATE);
        }
        return basePremium;
    }

    protected static String php(double value) {
        return String.format(Locale.US, "PHP %,.2f", value);
    }

    protected static String amount(String currencySign, double value) {
        return currencySign + String.format(Locale.US, "%,.2f", value);
    }

    protected static double roundToCents(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public String getOwnerId()       { return ownerId; }
    public LocalDate getStartDate()  { return startDate; }
    public LocalDate getEndDate()    { return endDate; }
    public double getDeductible()    { return deductible; }
    public double getCoverageLimit() { return coverageLimit; }
    public double getLimitUsed()     { return limitUsed; }
    public boolean isCancelled()     { return cancelled; }

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
        System.arraycopy(shared, 0, all, 0, shared.length);
        System.arraycopy(extra, 0, all, shared.length, extra.length);
        return FileHelper.join(all);
    }
}
