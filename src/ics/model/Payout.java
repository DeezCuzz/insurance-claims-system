package ics.model;

import ics.exception.*;
import ics.util.*;
import java.time.*;

public class Payout extends Entity {
    private String claimId;
    private double amount;
    private LocalDate paidDate;
    private String paidBy;
    private String breakdown;

    public Payout(String id, String claimId, double amount, LocalDate paidDate, String paidBy,
                  String breakdown) throws InvalidInputException {
        super(id);
        this.claimId = Validate.notBlank("Claim", claimId);
        this.amount = Validate.positive("Amount", amount);
        this.paidDate = Validate.notNull("Paid date", paidDate);
        this.paidBy = Validate.notBlank("Paid by", paidBy);
        this.breakdown = breakdown == null ? "" : breakdown;
    }

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
