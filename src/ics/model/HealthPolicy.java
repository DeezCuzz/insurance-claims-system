package ics.model;

import ics.exception.InvalidInputException;
import ics.util.Validate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Health cover: pays a share of a medical bill.
 *
 * Payout rule: deductible first, then the reimbursement %, then never more than the limit left.
 * Worked example (deductible 5,000, rate 80%, limit 100,000):
 *   bill 50,000   -> (50,000 - 5,000) x 0.8 = 36,000
 *   next 100,000  -> deductible already met -> 100,000 x 0.8 = 80,000, but only 64,000 left -> pays 64,000
 */
public class HealthPolicy extends Policy {

    /** Placeholder demo rate: the premium is 4% of the coverage limit (10% off for a high deductible). */
    public static final double PREMIUM_RATE = 0.04;

    private double reimbursementRate;   // 0.8 means the insurer pays 80%
    private int waitingPeriodDays;      // claims are not accepted this many days after the start
    private double deductibleMet;       // how much of the deductible is used up (for the whole policy, never resets)

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
        if (payout < share) {                                   // the limit left is smaller than the share
            line = line + ", capped at the " + amount(c, getRemainingLimit()) + " limit left";
        }
        return line;
    }

    /** Uses up the limit (done by the parent) AND counts how much of the deductible this claim met. */
    @Override
    public void applyPayout(double claimedAmount, double paidAmount) throws InvalidInputException {
        super.applyPayout(claimedAmount, paidAmount);
        deductibleMet += Math.min(claimedAmount, getRemainingDeductible());
    }

    public double getRemainingDeductible() {
        return Math.max(getDeductible() - deductibleMet, 0);
    }

    /** True while the waiting period has not finished. Used by the claim rules. */
    public boolean isInWaitingPeriod(LocalDate incidentDate) {
        return incidentDate.isBefore(getWaitingPeriodEnd());
    }

    /** The first day a claim can be made for. */
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
