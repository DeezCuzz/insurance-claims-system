package ics.model;

import ics.exception.*;
import ics.util.*;
import java.time.*;
import java.util.*;

public class HealthPolicy extends Policy {
    public static final double PREMIUM_RATE = 0.04;

    private double reimbursementRate;
    private int waitingPeriodDays;
    private double deductibleMet;

    public HealthPolicy(String id, String ownerId, LocalDate startDate, LocalDate endDate, double deductible,
                        double coverageLimit, double limitUsed, boolean cancelled, double reimbursementRate,
                        int waitingPeriodDays, double deductibleMet) throws InvalidInputException {
        super(id, ownerId, startDate, endDate, deductible, coverageLimit, limitUsed, cancelled);
        this.reimbursementRate = Validate.between("Reimbursement rate", reimbursementRate, 0.01, 1.0);
        this.waitingPeriodDays = (int) Validate.nonNegative("Waiting period", waitingPeriodDays);
        this.deductibleMet = Validate.between("Deductible met", deductibleMet, 0, deductible);
    }

    @Override
    public Type getType() {
        return Type.HEALTH;
    }

    @Override
    public double calculatePremium() {
        return roundToCents(withDeductibleDiscount(getCoverageLimit() * PREMIUM_RATE));
    }

    @Override
    public double calculatePayout(double claimedAmount) {
        double afterDeductible = Math.max(claimedAmount - getRemainingDeductible(), 0);
        double insurerShare = afterDeductible * reimbursementRate;
        return roundToCents(Math.min(insurerShare, getRemainingLimit()));
    }

    @Override
    public List<String> explainPayout(double claimedAmount) {
        double deductibleLeft = getRemainingDeductible();
        double afterDeductible = Math.max(claimedAmount - deductibleLeft, 0);
        long percent = Math.round(reimbursementRate * 100);
        List<String> steps = new ArrayList<>();
        steps.add("Claimed amount: " + php(claimedAmount));
        steps.add("Less deductible still to be met: " + php(Math.min(deductibleLeft, claimedAmount))
                + "  ->  " + php(afterDeductible));
        steps.add("Insurer pays " + percent + "% of that: " + php(afterDeductible * reimbursementRate));
        steps.add("Coverage limit still available: " + php(getRemainingLimit()));
        steps.add("Payout (the lower of the last two): " + php(calculatePayout(claimedAmount)));
        return steps;
    }

    @Override
    public String payoutFormula(double claimedAmount, String c) {
        double deductiblePart = Math.min(getRemainingDeductible(), claimedAmount);
        double afterDeductible = Math.max(claimedAmount - deductiblePart, 0);
        double share = roundToCents(afterDeductible * reimbursementRate);
        double payout = calculatePayout(claimedAmount);
        String line = amount(c, claimedAmount) + " - " + amount(c, deductiblePart) + " = " + amount(c, afterDeductible)
                + " x " + Math.round(reimbursementRate * 100) + "% = " + amount(c, share);
        if (payout < share) {
            line = line + ", capped at the " + amount(c, getRemainingLimit()) + " limit left";
        }
        return line;
    }

    @Override
    public void applyPayout(double claimedAmount, double paidAmount) throws InvalidInputException {
        super.applyPayout(claimedAmount, paidAmount);
        deductibleMet += Math.min(claimedAmount, getRemainingDeductible());
    }

    public double getRemainingDeductible() {
        return Math.max(getDeductible() - deductibleMet, 0);
    }

    public boolean isInWaitingPeriod(LocalDate incidentDate) {
        return incidentDate.isBefore(getWaitingPeriodEnd());
    }

    public LocalDate getWaitingPeriodEnd() {
        return getStartDate().plusDays(waitingPeriodDays);
    }

    public double getReimbursementRate() { return reimbursementRate; }
    public int getWaitingPeriodDays()    { return waitingPeriodDays; }
    public double getDeductibleMet()     { return deductibleMet; }

    @Override
    protected Object[] extraFields() {
        return new Object[] {reimbursementRate, waitingPeriodDays, deductibleMet};
    }
}
