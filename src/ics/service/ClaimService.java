package ics.service;

import ics.exception.AccessDeniedException;
import ics.exception.DataStorageException;
import ics.exception.ICSException;
import ics.exception.InvalidInputException;
import ics.exception.RuleException;
import ics.model.Claim;
import ics.model.HealthPolicy;
import ics.model.Payout;
import ics.model.Policy;
import ics.model.User;
import ics.repository.DataStore;
import ics.util.Validate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The life of a claim, from both sides.
 *
 *   User   applies   file()        automatic checks run at once: REJECTED with a reason, or UNDER_REVIEW
 *   Admin  reviews   approve()     needs the Admin password; releases the money at once and uses up part of the
 *                                  policy's limit (fix C5), and makes the payout slip
 *                    deny()        a reason is required
 *                    requestInfo() the note is the question the User will read
 *   User   answers   respond()     INFO_REQUESTED -> UNDER_REVIEW (two-way communication, rubric II.C.7)
 *   Admin  finishes  close()
 */
public class ClaimService {

    /** A claim must be filed within this many days of the incident. */
    public static final int FILING_WINDOW_DAYS = 90;              // fix E3
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    /** The choices in the status filter on the Review Claims screen. */
    public enum Filter {
        NEEDS_ACTION("Needs action"), ALL("All claims"), UNDER_REVIEW("Under review"),
        INFO_REQUESTED("Info requested"), APPROVED("Approved"), PAID("Paid (older claims)"),
        DENIED("Denied"), REJECTED("Rejected"), CLOSED("Closed");

        private final String label;
        Filter(String label) { this.label = label; }

        /** True if a claim with this status belongs in this filter. */
        public boolean accepts(Claim.Status status) {
            if (this == ALL) {
                return true;
            }
            if (this == NEEDS_ACTION) {               // waiting for the Admin to decide
                return status == Claim.Status.UNDER_REVIEW;
            }
            return name().equals(status.name());      // the other filters have the same name as a status
        }

        @Override
        public String toString() {
            return label;                             // what the drop-down list shows
        }
    }

    private final DataStore store;
    private final AuthService auth;
    private final Clock clock;      // "today"; tests replace it with a fixed date

    public ClaimService(DataStore store, AuthService auth) {
        this(store, auth, Clock.systemDefaultZone());
    }

    public ClaimService(DataStore store, AuthService auth, Clock clock) {
        this.store = store;
        this.auth = auth;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ================================================================== the User's side

    /** The User's own policies, active ones first. */
    public List<Policy> policiesOf(User applicant) {
        List<Policy> active = new ArrayList<>();
        List<Policy> others = new ArrayList<>();
        if (applicant != null) {
            for (Policy p : store.policiesOf(applicant.getId())) {
                if (p.getStatus(today()) == Policy.Status.ACTIVE) {
                    active.add(p);
                } else {
                    others.add(p);
                }
            }
        }
        active.addAll(others);
        return active;
    }

    /** What the policy would pay for this amount right now. Only an estimate: the Admin decides. */
    public double estimate(User applicant, String policyId, String claimedAmount) throws ICSException {
        Policy policy = ownPolicy(applicant, policyId);
        double amount = Validate.positive("Claimed amount", Validate.parseAmount("Claimed amount", claimedAmount));
        return policy.calculatePayout(amount);
    }

    /** Which automatic rule a claim for this date would break, WITHOUT saving anything (null = none). */
    public Claim.Reason precheck(User applicant, String policyId, String incidentDate) throws ICSException {
        Policy policy = ownPolicy(applicant, policyId);
        requireStarted(policy);
        return firstBrokenRule(policy, Validate.parseDate("Incident date", incidentDate));
    }

    /**
     * Registers a claim. Typing mistakes are refused and nothing is saved. A claim that breaks a policy
     * rule IS saved, as REJECTED with its reason, so there is a record of it.
     */
    public Claim file(User applicant, String policyId, String incidentDate, String description,
                      String claimedAmount) throws ICSException {
        Policy policy = ownPolicy(applicant, policyId);
        requireStarted(policy);
        LocalDate incident = Validate.parseDate("Incident date", incidentDate);
        if (incident.isAfter(today())) {
            throw new InvalidInputException("Incident date", "Incident date cannot be in the future.");
        }
        String text = Validate.notBlank("Description", description);
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidInputException("Description",
                    "Description can be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        double amount = Validate.positive("Claimed amount", Validate.parseAmount("Claimed amount", claimedAmount));

        Claim claim = new Claim(policy.getId(), incident, today(), text, amount);
        Claim.Reason broken = firstBrokenRule(policy, incident);
        if (broken != null) {
            claim.reject(broken);
        } else {
            claim.changeStatus(Claim.Status.UNDER_REVIEW);
            claim.setReserveAmount(Math.min(amount, policy.getRemainingLimit()));
        }
        store.claims().add(claim);                 // gives it the next ID (CLM-000X) and saves
        return claim;
    }

    /** The automatic checks, in order. Returns the first rule broken, or null if the claim may go on. */
    private Claim.Reason firstBrokenRule(Policy policy, LocalDate incident) {
        if (policy.isCancelled()) {
            return Claim.Reason.POLICY_INACTIVE;
        }
        if (incident.isBefore(policy.getStartDate()) || incident.isAfter(policy.getEndDate())) {
            return Claim.Reason.OUTSIDE_COVERAGE_PERIOD;
        }
        if (policy instanceof HealthPolicy && ((HealthPolicy) policy).isInWaitingPeriod(incident)) {
            return Claim.Reason.WAITING_PERIOD;
        }
        if (ChronoUnit.DAYS.between(incident, today()) > FILING_WINDOW_DAYS) {
            return Claim.Reason.LATE_FILING;
        }
        for (Claim other : store.claimsOn(policy.getId())) {
            if (other.getIncidentDate().equals(incident) && other.getStatus() != Claim.Status.REJECTED) {
                return Claim.Reason.DUPLICATE_CLAIM;
            }
        }
        if (policy.getRemainingLimit() <= 0) {
            return Claim.Reason.LIMIT_EXHAUSTED;
        }
        return null;
    }

    /** Every claim on the User's policies: waiting for their reply first, then in progress, then finished. */
    public List<Claim> claimsOf(User applicant) {
        List<Claim> result = new ArrayList<>();
        if (applicant == null) {
            return result;
        }
        for (Policy p : store.policiesOf(applicant.getId())) {
            result.addAll(store.claimsOn(p.getId()));
        }
        result.sort((a, b) -> compareForUser(a, b));
        return result;
    }

    /** Claims on one of the User's own policies, newest first. */
    public List<Claim> claimsOnPolicy(User applicant, String policyId) throws ICSException {
        Policy policy = ownPolicy(applicant, policyId);
        List<Claim> result = store.claimsOn(policy.getId());
        result.sort((a, b) -> compareNewestFirst(a, b));
        return result;
    }

    /** The User answers the Admin's question: INFO_REQUESTED -> UNDER_REVIEW (rubric II.C.7). */
    public void respond(User applicant, String claimId, String reply) throws ICSException {
        Claim claim = ownClaim(applicant, claimId);
        String text = Validate.notBlank("Your reply", reply);
        if (text.length() > Claim.MAX_NOTE_LENGTH) {
            throw new InvalidInputException("Your reply", "The reply can be at most " + Claim.MAX_NOTE_LENGTH + " characters.");
        }
        claim.changeStatus(Claim.Status.UNDER_REVIEW);     // refused unless the Admin asked for information
        claim.setApplicantNote(text);
        store.claims().update(claim);
    }

    /** The payout slip, but only for the User's own claim. */
    public List<String> payoutSlipFor(User applicant, String claimId) throws ICSException {
        ownClaim(applicant, claimId);
        return payoutSlip(claimId);
    }

    // ================================================================== the Admin's side: reading

    /** Claims matching the filter and keyword. Claims that need the Admin come first, oldest first. */
    public List<Claim> search(String keyword, Filter filter) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Claim> result = new ArrayList<>();
        for (Claim c : store.claims().getAll()) {
            if (!filter.accepts(c.getStatus())) {
                continue;
            }
            User holder = holderOf(c);
            String holderText = holder == null ? "" : holder.getUsername() + " " + holder.getFullName();
            String text = (c.getId() + " " + c.getPolicyId() + " " + holderText + " " + c.getDescription() + " "
                    + c.getStatus().getLabel()).toLowerCase();
            if (k.isEmpty() || text.contains(k)) {
                result.add(c);
            }
        }
        result.sort((a, b) -> compareForAdmin(a, b));
        return result;
    }

    public Claim getClaim(String claimId) throws DataStorageException {
        Claim claim = store.claims().findById(claimId);
        if (claim == null) {
            throw new DataStorageException("Claim not found: " + claimId);
        }
        return claim;
    }

    public Policy policyOf(Claim claim) throws DataStorageException {
        Policy policy = store.policies().findById(claim.getPolicyId());
        if (policy == null) {
            throw new DataStorageException("Policy not found: " + claim.getPolicyId());
        }
        return policy;
    }

    /** The payout of a paid claim, or null if it was not paid. */
    public Payout payoutOf(Claim claim) {
        return store.payoutFor(claim.getId());
    }

    /** The User who owns the claim's policy, or null if that account is gone. */
    public User holderOf(Claim claim) {
        Policy policy = store.policies().findById(claim.getPolicyId());
        return policy == null ? null : store.users().findById(policy.getOwnerId());
    }

    public String holderName(Claim claim) {
        User holder = holderOf(claim);
        return holder == null ? "(removed)" : holder.getUsername();
    }

    /** Full name for a user ID (for "Decided by"), or the ID itself if the account is gone. */
    public String userName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        User user = store.users().findById(userId);
        return user == null ? userId : user.getFullName();
    }

    // ---- counts for the dashboards ----
    public int countClaims()        { return store.claims().getAll().size(); }
    public int countPayouts()       { return store.payouts().getAll().size(); }
    public int countNeedingAction() { return search("", Filter.NEEDS_ACTION).size(); }

    /** Claims where the Admin is waiting for this User's reply. */
    public int countAwaitingReply(User user) {
        int count = 0;
        for (Claim c : claimsOf(user)) {
            if (c.getStatus() == Claim.Status.INFO_REQUESTED) {
                count++;
            }
        }
        return count;
    }

    // ================================================================== the Admin's side: deciding

    /**
     * UNDER_REVIEW -> APPROVED, and the money is released at once (fix C5). It moves money, so the Admin types
     * their password again. The payout is worked out NOW from the policy as it is today, so it can never be more
     * than the limit left (fix C4). Then, in this order:
     *   1. the claim records the amount and becomes APPROVED (approving twice is refused: fix C6)
     *   2. the policy's limit left goes down by the payout
     *   3. a payout record keeps how the amount was worked out, for the slip
     * Known limit: three files are written one after another. A real database would do it as one step.
     */
    public Payout approve(String claimId, User admin, String note, String adminPassword) throws ICSException {
        requireReviewer(admin);
        String cleanNote = cleanNote(note);          // checked BEFORE anything changes
        auth.confirmPassword(admin, adminPassword);
        Claim claim = getClaim(claimId);
        if (claim.getStatus() != Claim.Status.UNDER_REVIEW) {
            throw new RuleException("A claim cannot move from " + claim.getStatus() + " to APPROVED.");
        }
        Policy policy = policyOf(claim);
        double amount = policy.calculatePayout(claim.getClaimedAmount());
        if (amount <= 0) {
            throw new RuleException("Nothing is payable on this claim (the deductible is not exceeded, or the "
                    + "coverage limit is used up). Deny it with a reason instead.");
        }
        String breakdown = String.join("\n", policy.explainPayout(claim.getClaimedAmount()));  // BEFORE the limit changes

        claim.approve(amount, admin.getId());
        claim.setReviewerNote(cleanNote);
        policy.applyPayout(claim.getClaimedAmount(), amount);
        Payout payout = new Payout(null, claim.getId(), amount, today(), admin.getId(), breakdown);
        store.payouts().add(payout);
        store.policies().update(policy);
        store.claims().update(claim);
        return payout;
    }

    /** UNDER_REVIEW -> DENIED. A reason is required; the note is optional. */
    public void deny(String claimId, User admin, Claim.Reason reason, String note) throws ICSException {
        requireReviewer(admin);
        Validate.notNull("Denial reason", reason);
        String cleanNote = cleanNote(note);
        Claim claim = getClaim(claimId);
        claim.deny(reason, admin.getId());
        claim.setReviewerNote(cleanNote);
        store.claims().update(claim);
    }

    /** UNDER_REVIEW -> INFO_REQUESTED. The note is the question the User will read, so it is required. */
    public void requestInfo(String claimId, User admin, String note) throws ICSException {
        requireReviewer(admin);
        Validate.notBlank("Note to the user", note);
        String cleanNote = cleanNote(note);
        Claim claim = getClaim(claimId);
        claim.changeStatus(Claim.Status.INFO_REQUESTED);
        claim.setHandledBy(admin.getId());
        claim.setReviewerNote(cleanNote);
        store.claims().update(claim);
    }

    /** INFO_REQUESTED -> UNDER_REVIEW, for example when the information arrived another way. */
    public void resumeReview(String claimId, User admin) throws ICSException {
        requireReviewer(admin);
        Claim claim = getClaim(claimId);
        claim.changeStatus(Claim.Status.UNDER_REVIEW);
        store.claims().update(claim);
    }

    /** APPROVED or DENIED -> CLOSED. */
    public void close(String claimId, User admin) throws ICSException {
        requireReviewer(admin);
        Claim claim = getClaim(claimId);
        claim.changeStatus(Claim.Status.CLOSED);
        store.claims().update(claim);
    }

    // ================================================================== the payout slip

    /** The printable slip for a paid claim, one line per entry, 46 characters wide (rubric II.A.5). */
    public List<String> payoutSlip(String claimId) throws ICSException {
        Claim claim = getClaim(claimId);
        Payout payout = store.payoutFor(claimId);
        if (payout == null) {
            throw new RuleException("This claim has not been paid yet, so there is no slip.");
        }
        Policy policy = policyOf(claim);
        User holder = holderOf(claim);
        String holderText = holder == null ? "(removed)" : holder.getFullName() + " (" + holder.getUsername() + ")";

        String bar = "=".repeat(46);
        String line = "-".repeat(46);
        List<String> slip = new ArrayList<>();
        slip.add(bar);
        slip.add(center("INSURANCE CLAIMS SYSTEM"));
        slip.add(center("PAYOUT SLIP"));
        slip.add(bar);
        slip.add(row("Slip no.", payout.getId()));
        slip.add(row("Date paid", payout.getPaidDate().toString()));
        slip.add(row("Claim", claim.getId()));
        slip.add(row("Policy", policy.getId() + " (" + policy.getType().getLabel() + ")"));
        slip.add(row("Paid to", holderText));
        slip.add(row("Incident date", claim.getIncidentDate().toString()));
        slip.add(line);
        slip.add("HOW THE AMOUNT WAS WORKED OUT");
        for (String step : payout.getBreakdown().split("\n")) {
            if (!step.isBlank()) {
                slip.add("  " + step);
            }
        }
        if (claim.getApprovedAmount() != payout.getAmount()) {
            slip.add("  Approved earlier: " + money(claim.getApprovedAmount()) + ", paid less because the");
            slip.add("  policy's remaining limit had changed.");
        }
        slip.add(line);
        slip.add(row("AMOUNT PAID", money(payout.getAmount())));
        slip.add(row("Released by", userName(payout.getPaidBy())));
        if (!claim.getReviewerNote().isBlank()) {
            slip.add(row("Note", claim.getReviewerNote()));
        }
        slip.add(bar);
        return slip;
    }

    // ================================================================== private helpers

    /** The policy, but only if it belongs to this User. */
    private Policy ownPolicy(User applicant, String policyId) throws ICSException {
        if (applicant == null || !applicant.canApplyForClaims()) {
            throw new AccessDeniedException("Only a User can apply for a claim.");
        }
        Policy policy = store.policies().findById(Validate.notBlank("Policy", policyId));
        if (policy == null) {
            throw new InvalidInputException("Policy", "Choose one of your policies.");
        }
        if (!policy.getOwnerId().equals(applicant.getId())) {
            throw new AccessDeniedException("You can only apply for claims on your own policies.");
        }
        return policy;
    }

    /** A policy that has not started yet cannot take claims (fix A8). Nothing is saved. */
    private void requireStarted(Policy policy) throws InvalidInputException {
        if (policy.getStatus(today()) == Policy.Status.UPCOMING) {
            throw new InvalidInputException("Policy", policy.getId() + " starts on " + policy.getStartDate()
                    + ". You can apply for a claim once it is active.");
        }
    }

    /** The claim, but only if it is on one of this User's policies. */
    private Claim ownClaim(User applicant, String claimId) throws ICSException {
        if (applicant == null || !applicant.canApplyForClaims()) {
            throw new AccessDeniedException("Only a User can do this.");
        }
        Claim claim = getClaim(claimId);
        if (!policyOf(claim).getOwnerId().equals(applicant.getId())) {
            throw new AccessDeniedException("You can only see your own claims.");
        }
        return claim;
    }

    private static void requireReviewer(User admin) throws AccessDeniedException {
        if (admin == null || !admin.canReviewClaims()) {
            throw new AccessDeniedException("Only an Admin can review claims.");
        }
    }

    /** Checks a note BEFORE anything is changed, so a note that is too long leaves the claim untouched. */
    private static String cleanNote(String note) throws InvalidInputException {
        String cleaned = note == null ? "" : note.trim();
        if (cleaned.length() > Claim.MAX_NOTE_LENGTH) {
            throw new InvalidInputException("Note to the user", "The note can be at most " + Claim.MAX_NOTE_LENGTH + " characters.");
        }
        return cleaned;
    }

    /** Admin list order: under review, then waiting for info, then the decided ones; oldest first. */
    private static int compareForAdmin(Claim a, Claim b) {
        int byGroup = Integer.compare(adminGroup(a.getStatus()), adminGroup(b.getStatus()));
        if (byGroup != 0) {
            return byGroup;
        }
        int byDate = a.getFiledDate().compareTo(b.getFiledDate());
        return byDate != 0 ? byDate : a.getId().compareTo(b.getId());
    }

    private static int adminGroup(Claim.Status s) {
        if (s == Claim.Status.UNDER_REVIEW)   { return 0; }
        if (s == Claim.Status.INFO_REQUESTED) { return 1; }
        if (s == Claim.Status.FILED)          { return 2; }
        if (s == Claim.Status.APPROVED || s == Claim.Status.DENIED || s == Claim.Status.PAID) { return 3; }
        return 4;
    }

    /** User list order: waiting for their reply, then still moving, then finished; newest first. */
    private static int compareForUser(Claim a, Claim b) {
        int byGroup = Integer.compare(userGroup(a.getStatus()), userGroup(b.getStatus()));
        return byGroup != 0 ? byGroup : compareNewestFirst(a, b);
    }

    private static int userGroup(Claim.Status s) {
        if (s == Claim.Status.INFO_REQUESTED) { return 0; }
        if (s == Claim.Status.FILED || s == Claim.Status.UNDER_REVIEW) { return 1; }
        return 2;
    }

    private static int compareNewestFirst(Claim a, Claim b) {
        int byDate = b.getFiledDate().compareTo(a.getFiledDate());
        return byDate != 0 ? byDate : b.getId().compareTo(a.getId());
    }

    private static String money(double value) {
        return String.format(Locale.US, "PHP %,.2f", value);
    }

    private static String row(String label, String value) {
        return String.format("%-14s: %s", label, value);
    }

    private static String center(String text) {
        return " ".repeat(Math.max(0, (46 - text.length()) / 2)) + text;
    }
}
