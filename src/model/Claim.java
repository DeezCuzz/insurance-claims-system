package ics.model;

import ics.exception.*;
import ics.util.*;
import java.time.*;

public class Claim extends Entity {
    public static final int MAX_NOTE_LENGTH = 300;

    public enum Status {
        FILED("Filed"), REJECTED("Rejected"), UNDER_REVIEW("Under review"), INFO_REQUESTED("Info requested"),
        APPROVED("Approved"), DENIED("Denied"), PAID("Paid"), CLOSED("Closed");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }

        public boolean canMoveTo(Status next) {
            if (this == FILED) {
                return next == UNDER_REVIEW || next == REJECTED;
            }
            if (this == UNDER_REVIEW) {
                return next == APPROVED || next == DENIED || next == INFO_REQUESTED;
            }
            if (this == INFO_REQUESTED) {
                return next == UNDER_REVIEW;
            }
            if (this == APPROVED || this == DENIED || this == PAID) {
                return next == CLOSED;
            }
            return false;
        }
    }

    public enum Reason {
        POLICY_INACTIVE("Policy is not active"),
        OUTSIDE_COVERAGE_PERIOD("Incident is outside the coverage period"),
        WAITING_PERIOD("Incident happened inside the waiting period"),
        LATE_FILING("Claim was filed too late"),
        DUPLICATE_CLAIM("Same incident was already claimed"),
        LIMIT_EXHAUSTED("Coverage limit is used up"),
        EXCLUDED_CAUSE("Cause of loss is not covered"),
        SUSPECTED_FRAUD("Claim failed the fraud review"),
        INSUFFICIENT_DOCUMENTS("Supporting documents are missing");

        private final String label;
        Reason(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private String policyId;
    private LocalDate incidentDate;
    private LocalDate filedDate;
    private String description;
    private double claimedAmount;
    private double reserveAmount;
    private double approvedAmount;
    private double paidAmount;
    private Status status;
    private Reason reason;
    private String handledBy;
    private String reviewerNote;
    private String applicantNote;

    public Claim(String id, String policyId, LocalDate incidentDate, LocalDate filedDate, String description,
                 double claimedAmount, double reserveAmount, double approvedAmount, double paidAmount,
                 Status status, Reason reason, String handledBy, String reviewerNote,
                 String applicantNote) throws InvalidInputException {
        super(id);
        this.policyId = Validate.notBlank("Policy", policyId);
        this.incidentDate = Validate.notNull("Incident date", incidentDate);
        this.filedDate = Validate.notNull("Filed date", filedDate);
        if (incidentDate.isAfter(filedDate)) {
            throw new InvalidInputException("Incident date", "Incident date cannot be after the filing date.");
        }
        this.description = Validate.notBlank("Description", description);
        this.claimedAmount = Validate.positive("Claimed amount", claimedAmount);
        this.reserveAmount = Validate.nonNegative("Reserve amount", reserveAmount);
        this.approvedAmount = Validate.nonNegative("Approved amount", approvedAmount);
        this.paidAmount = Validate.nonNegative("Paid amount", paidAmount);
        this.status = Validate.notNull("Status", status);
        this.reason = reason;
        this.handledBy = handledBy;
        setReviewerNote(reviewerNote);
        setApplicantNote(applicantNote);
    }

    public Claim(String policyId, LocalDate incidentDate, LocalDate filedDate, String description,
                 double claimedAmount) throws InvalidInputException {
        this(null, policyId, incidentDate, filedDate, description, claimedAmount, 0, 0, 0,
                Status.FILED, null, null, "", "");
    }

    public static Claim fromFields(String[] f) throws InvalidInputException {
        Reason reason = f[10].isEmpty() ? null : Reason.valueOf(f[10]);
        String handledBy = f[11].isEmpty() ? null : f[11];
        String reviewerNote = f.length > 12 ? f[12] : "";
        String applicantNote = f.length > 13 ? f[13] : "";
        return new Claim(f[0], f[1], LocalDate.parse(f[2]), LocalDate.parse(f[3]), f[4],
                Double.parseDouble(f[5]), Double.parseDouble(f[6]), Double.parseDouble(f[7]),
                Double.parseDouble(f[8]), Status.valueOf(f[9]), reason, handledBy, reviewerNote, applicantNote);
    }

    public void changeStatus(Status next) throws RuleException {
        if (!status.canMoveTo(next)) {
            throw new RuleException("A claim cannot move from " + status + " to " + next + ".");
        }
        status = next;
    }

    public void reject(Reason why) throws InvalidInputException, RuleException {
        Validate.notNull("Denial reason", why);
        changeStatus(Status.REJECTED);
        reason = why;
    }

    public void approve(double amount, String adminId) throws InvalidInputException, RuleException {
        double checked = Validate.positive("Approved amount", amount);
        changeStatus(Status.APPROVED);
        approvedAmount = checked;
        paidAmount = checked;
        handledBy = adminId;
    }

    public void deny(Reason why, String adminId) throws InvalidInputException, RuleException {
        Validate.notNull("Denial reason", why);
        changeStatus(Status.DENIED);
        reason = why;
        handledBy = adminId;
    }

    public String getPolicyId()        { return policyId; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public LocalDate getFiledDate()    { return filedDate; }
    public String getDescription()     { return description; }
    public double getClaimedAmount()   { return claimedAmount; }
    public double getReserveAmount()   { return reserveAmount; }
    public double getApprovedAmount()  { return approvedAmount; }
    public double getPaidAmount()      { return paidAmount; }
    public Status getStatus()          { return status; }
    public Reason getReason()          { return reason; }
    public String getHandledBy()       { return handledBy; }
    public String getReviewerNote()    { return reviewerNote; }
    public String getApplicantNote()   { return applicantNote; }

    public void setReserveAmount(double amount) throws InvalidInputException {
        reserveAmount = Validate.nonNegative("Reserve amount", amount);
    }

    public void setHandledBy(String adminId) {
        handledBy = adminId;
    }

    public final void setReviewerNote(String note) throws InvalidInputException {
        reviewerNote = checkNote("Note to the user", note);
    }

    public final void setApplicantNote(String note) throws InvalidInputException {
        applicantNote = checkNote("Your reply", note);
    }

    private static String checkNote(String field, String note) throws InvalidInputException {
        String cleaned = note == null ? "" : note.trim();
        if (cleaned.length() > MAX_NOTE_LENGTH) {
            throw new InvalidInputException(field, "The note can be at most " + MAX_NOTE_LENGTH + " characters.");
        }
        return cleaned;
    }

    @Override
    public String toFileLine() {
        return FileHelper.join(getId(), policyId, incidentDate, filedDate, description, claimedAmount,
                reserveAmount, approvedAmount, paidAmount, status, reason, handledBy, reviewerNote, applicantNote);
    }
}
