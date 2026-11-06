package ics.model;

import ics.exception.*;
import ics.util.*;
import java.time.*;
import java.util.*;

public class AutoPolicy extends Policy {
    public static final double PREMIUM_RATE = 0.03;

    private double vehicleValue;
    private String plateNo;

    public AutoPolicy(String id, String ownerId, LocalDate startDate, LocalDate endDate, double deductible,
                      double coverageLimit, double limitUsed, boolean cancelled, double vehicleValue,
                      String plateNo) throws InvalidInputException {
        super(id, ownerId, startDate, endDate, deductible, coverageLimit, limitUsed, cancelled);
        this.vehicleValue = Validate.positive("Vehicle value", vehicleValue);
        this.plateNo = Validate.notBlank("Plate number", plateNo);
    }

    @Override
    public Type getType() {
        return Type.AUTO;
    }

    @Override
    public double calculatePremium() {
        return roundToCents(withDeductibleDiscount(vehicleValue * PREMIUM_RATE));
    }

    @Override
    public double calculatePayout(double claimedAmount) {
        double afterDeductible = Math.max(claimedAmount - getDeductible(), 0);
        return roundToCents(Math.min(afterDeductible, getRemainingLimit()));
    }

    @Override
    public List<String> explainPayout(double claimedAmount) {
        double afterDeductible = Math.max(claimedAmount - getDeductible(), 0);
        List<String> steps = new ArrayList<>();
        steps.add("Repair estimate: " + php(claimedAmount));
        steps.add("Less deductible: " + php(Math.min(getDeductible(), claimedAmount)) + "  ->  " + php(afterDeductible));
        steps.add("Coverage limit still available: " + php(getRemainingLimit()));
        steps.add("Payout (the lower of the last two): " + php(calculatePayout(claimedAmount)));
        return steps;
    }

    @Override
    public String payoutFormula(double claimedAmount, String c) {
        double deductiblePart = Math.min(getDeductible(), claimedAmount);
        double afterDeductible = Math.max(claimedAmount - deductiblePart, 0);
        String line = amount(c, claimedAmount) + " - " + amount(c, deductiblePart) + " = " + amount(c, afterDeductible);
        if (calculatePayout(claimedAmount) < afterDeductible) {
            line = line + ", capped at the " + amount(c, getRemainingLimit()) + " limit left";
        }
        return line;
    }

    public double getVehicleValue() { return vehicleValue; }
    public String getPlateNo()      { return plateNo; }

    @Override
    protected Object[] extraFields() {
        return new Object[] {vehicleValue, plateNo};
    }
}
