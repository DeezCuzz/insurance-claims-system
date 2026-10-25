package ics.model;

import ics.exception.InvalidInputException;
import ics.util.FileHelper;
import ics.util.Validate;

import java.time.LocalDate;

/**
 * The money actually released for one approved claim.
 * WHY separate from the claim: "approved" and "paid" are different moments, and the payout keeps a copy
 * of how the amount was worked out at the moment it was paid (for the printed slip).
 *
 * payouts.txt line: id | claimId | amount | paidDate | paidBy | breakdown
 */
public class Payout extends Entity {

    private String claimId;
    private double amount;
    private LocalDate paidDate;
    private String paidBy;       // ID of the Admin who released it
    private String breakdown;    // the working-out, one step per line

    public Payout(String id, String claimId, double amount, LocalDate paidDate, String paidBy,
                  String breakdown) throws InvalidInputException {
        super(id);
        this.claimId = Validate.notBlank("Claim", claimId);
        this.amount = Validate.positive("Amount", amount);
        this.paidDate = Validate.notNull("Paid date", paidDate);
        this.paidBy = Validate.notBlank("Paid by", paidBy);
        this.breakdown = breakdown == null ? "" : breakdown;
    }

    /** Rebuilds a payout from one line of payouts.txt (older lines have no breakdown column). */
    public static Payout fromFields(String[] f) throws InvalidInputException {
        return new Payout(f[0], f[1], Double.parseDouble(f[2]), LocalDate.parse(f[3]), f[4],
                f.length > 5 ? f[5] : "");
    }

    public String getClaimId()     { return claimId; }
    public double getAmount()      { return amount; }
    public LocalDate getPaidDate() { return paidDate; }
    public String getPaidBy()      { return paidBy; }
    public String getBreakdown()   { return breakdown; }

    @Override
    public String toFileLine() {
        return FileHelper.join(getId(), claimId, amount, paidDate, paidBy, breakdown);
    }
}
