package ics.model;

import ics.exception.InvalidInputException;
import ics.exception.RuleException;
import ics.util.FileHelper;
import ics.util.Validate;

import java.time.LocalDate;

/**
 * A request to be paid under one policy.
 *
 * ENCAPSULATION: the status can only change through the methods in this class, and each of them asks
 * Status.canMoveTo() first, so no screen can skip a step (for example jump from FILED straight to PAID).
 *
 * Four money fields, never mixed up:
 *   claimedAmount  what the User asked for
 *   reserveAmount  the insurer's estimate when it was filed
 *   approvedAmount what the policy said it would pay when the Admin approved
 *   paidAmount     what was really paid
 *
 * claims.txt line: id | policyId | incidentDate | filedDate | description | claimed | reserve | approved | paid
 *                  | status | reason | handledBy | reviewerNote | applicantNote
 */
public class Claim extends Entity {

    public static final int MAX_NOTE_LENGTH = 300;

    /**
     * Every stage of a claim, and the only moves allowed between them:
     *   FILED -> UNDER_REVIEW or REJECTED
     *   UNDER_REVIEW -> APPROVED, DENIED or INFO_REQUESTED
     *   INFO_REQUESTED -> UNDER_REVIEW
     *   APPROVED, DENIED or PAID -> CLOSED
     * (fix C5: approving also releases the money, so there is no separate pay step. PAID is kept only so
     *  claims saved by older versions still load.)
     *   REJECTED and CLOSED are the end
     */
    public enum Status {
        FILED("Filed"), REJECTED("Rejected"), UNDER_REVIEW("Under review"), INFO_REQUESTED("Info requested"),
        APPROVED("Approved"), DENIED("Denied"), PAID("Paid"), CLOSED("Closed");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }

        /** True if a claim in this status may move to the next one. */
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
            return false;      // REJECTED and CLOSED cannot move anywhere
        }
    }

    /**
     * Why a claim is not paid. The first six come from the automatic checks;
     * the last three are chosen by the Admin when denying.
     */
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
    private double paidAmount;       // the same as approvedAmount: approving releases the money (fix C5)
    private Status status;
    private Reason reason;           // only when REJECTED or DENIED
    private String handledBy;        // ID of the Admin who decided
    private String reviewerNote;     // the Admin's message to the User (rubric II.C.7)
    private String applicantNote;    // the User's reply to the Admin

    /** Used when loading from the file: every field is given. */
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

    /** Used by Apply for Claim: a brand-new claim starts as FILED and has no ID yet. */
    public Claim(String policyId, LocalDate incidentDate, LocalDate filedDate, String description,
                 double claimedAmount) throws InvalidInputException {
        this(null, policyId, incidentDate, filedDate, description, claimedAmount, 0, 0, 0,
                Status.FILED, null, null, "", "");
    }

    /**
     * Rebuilds a claim from one line of claims.txt.
     * Lines written by older versions have fewer columns; the missing notes are read as empty.
     */
    public static Claim fromFields(String[] f) throws InvalidInputException {
        Reason reason = f[10].isEmpty() ? null : Reason.valueOf(f[10]);
        String handledBy = f[11].isEmpty() ? null : f[11];
        String reviewerNote = f.length > 12 ? f[12] : "";
        String applicantNote = f.length > 13 ? f[13] : "";
        return new Claim(f[0], f[1], LocalDate.parse(f[2]), LocalDate.parse(f[3]), f[4],
                Double.parseDouble(f[5]), Double.parseDouble(f[6]), Double.parseDouble(f[7]),
                Double.parseDouble(f[8]), Status.valueOf(f[9]), reason, handledBy, reviewerNote, applicantNote);
    }

    // ---- the life cycle: every status change goes through here ----

    /** Moves to the next status, or refuses if that move is not allowed. */
    public void changeStatus(Status next) throws RuleException {
        if (!status.canMoveTo(next)) {
            throw new RuleException("A claim cannot move from " + status + " to " + next + ".");
        }
        status = next;
    }

    /** An automatic check failed: FILED -> REJECTED, with the reason. */
    public void reject(Reason why) throws InvalidInputException, RuleException {
        Validate.notNull("Denial reason", why);
        changeStatus(Status.REJECTED);
        reason = why;
    }

    /**
     * UNDER_REVIEW -> APPROVED. Approving releases the money at once (fix C5), so the approved amount is also
     * the paid amount. The amount comes from the policy's calculatePayout(). Approving twice is impossible,
     * because APPROVED cannot move to APPROVED (fix C6).
     */
    public void approve(double amount, String adminId) throws InvalidInputException, RuleException {
        double checked = Validate.positive("Approved amount", amount);
        changeStatus(Status.APPROVED);
        approvedAmount = checked;
        paidAmount = checked;
        handledBy = adminId;
    }

    /** UNDER_REVIEW -> DENIED. A reason is required. */
    public void deny(Reason why, String adminId) throws InvalidInputException, RuleException {
        Validate.notNull("Denial reason", why);
        changeStatus(Status.DENIED);
        reason = why;
        handledBy = adminId;
    }

    // ---- getters and setters ----
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

    /** The Admin's note for the User. Blank is fine; very long text is not. */
    public final void setReviewerNote(String note) throws InvalidInputException {
        reviewerNote = checkNote("Note to the user", note);
    }

    /** The User's reply to the Admin. */
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
