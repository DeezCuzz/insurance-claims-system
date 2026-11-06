package ics.service;

import ics.exception.*;
import ics.model.*;
import ics.repository.*;
import ics.util.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

public class ClaimService {
    public static final int FILING_WINDOW_DAYS = 90;
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    public enum Filter {
        NEEDS_ACTION("Needs action"), ALL("All claims"), UNDER_REVIEW("Under review"),
        INFO_REQUESTED("Info requested"), APPROVED("Approved"), PAID("Paid (older claims)"),
        DENIED("Denied"), REJECTED("Rejected"), CLOSED("Closed");

        private final String label;
        Filter(String label) { this.label = label; }

        public boolean accepts(Claim.Status status) {
            if (this == ALL) {
                return true;
            }
            if (this == NEEDS_ACTION) {
                return status == Claim.Status.UNDER_REVIEW;
            }
            return name().equals(status.name());
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SortBy {
        QUEUE("Queue order"), HIGHEST_CLAIMED("Highest claimed"), NEWEST_FILED("Newest filed");

        private final String label;
        SortBy(String label) { this.label = label; }

        @Override
        public String toString() {
            return label;
        }
    }

    private final DataStore store;
    private final AuthService auth;
    private final Clock clock;
    private int lastSearchSteps = -1;

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

    public double estimate(User applicant, String policyId, String claimedAmount) throws ICSException {
        Policy policy = ownPolicy(applicant, policyId);
        double amount = Validate.positive("Claimed amount", Validate.parseAmount("Claimed amount", claimedAmount));
        return policy.calculatePayout(amount);
    }

    public Claim.Reason precheck(User applicant, String policyId, String incidentDate) throws ICSException {
        Policy policy = ownPolicy(applicant, policyId);
        requireStarted(policy);
        return firstBrokenRule(policy, Validate.parseDate("Incident date", incidentDate));
    }

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
        store.claims().add(claim);
        return claim;
    }

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

    public List<Claim> claimsOf(User applicant) {
        List<Claim> result = new ArrayList<>();
        if (applicant == null) {
            return result;
        }
        for (Policy p : store.policiesOf(applicant.getId())) {
            result.addAll(store.claimsOn(p.getId()));
        }
        Sorting.insertionSort(result, (a, b) -> compareForUser(a, b));
        return result;
    }

    public List<Claim> claimsOnPolicy(User applicant, String policyId) throws ICSException {
        Policy policy = ownPolicy(applicant, policyId);
        List<Claim> result = store.claimsOn(policy.getId());
        Sorting.insertionSort(result, (a, b) -> compareNewestFirst(a, b));
        return result;
    }

    public void respond(User applicant, String claimId, String reply) throws ICSException {
        Claim claim = ownClaim(applicant, claimId);
        String text = Validate.notBlank("Your reply", reply);
        if (text.length() > Claim.MAX_NOTE_LENGTH) {
            throw new InvalidInputException("Your reply", "The reply can be at most " + Claim.MAX_NOTE_LENGTH + " characters.");
        }
        claim.changeStatus(Claim.Status.UNDER_REVIEW);
        claim.setApplicantNote(text);
        store.claims().update(claim);
    }

    public List<String> payoutSlipFor(User applicant, String claimId) throws ICSException {
        ownClaim(applicant, claimId);
        return payoutSlip(claimId);
    }

    public List<Claim> search(String keyword, Filter filter) {
        return search(keyword, filter, SortBy.QUEUE);
    }

    public List<Claim> search(String keyword, Filter filter, SortBy sortBy) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Claim> result = new ArrayList<>();
        lastSearchSteps = -1;

        Claim exact = store.claims().findById(k.toUpperCase());
        if (exact != null) {
            lastSearchSteps = store.claims().getLastSearchSteps();
            if (filter.accepts(exact.getStatus())) {
                result.add(exact);
            }
            return result;
        }

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
        if (sortBy == SortBy.HIGHEST_CLAIMED) {
            Sorting.insertionSort(result, (a, b) -> compareHighestClaimed(a, b));
        } else if (sortBy == SortBy.NEWEST_FILED) {
            Sorting.insertionSort(result, (a, b) -> compareNewestFirst(a, b));
        } else {
            Sorting.insertionSort(result, (a, b) -> compareForAdmin(a, b));
        }
        return result;
    }

    public int getLastSearchSteps() {
        return lastSearchSteps;
    }

    private Queue<Claim> buildQueue(List<Claim> claims) {
        List<Claim> inOrder = new ArrayList<>(claims);
        Sorting.insertionSort(inOrder, (a, b) -> compareFiledOrder(a, b));
        Queue<Claim> line = new LinkedList<>();
        for (Claim c : inOrder) {
            if (c.getStatus() == Claim.Status.UNDER_REVIEW) {
                line.offer(c);
            }
        }
        return line;
    }

    public Queue<Claim> reviewQueue() {
        return buildQueue(store.claims().getAll());
    }

    public Claim nextInLine() {
        return reviewQueue().peek();
    }

    public int placeInLine(String claimId) {
        Queue<Claim> line = reviewQueue();
        int place = 0;
        while (!line.isEmpty()) {
            place++;
            if (line.poll().getId().equals(claimId)) {
                return place;
            }
        }
        return 0;
    }

    public void checkTurnToApprove(String claimId) throws ICSException {
        Claim claim = getClaim(claimId);
        Queue<Claim> policyLine = buildQueue(store.claimsOn(claim.getPolicyId()));
        Claim front = policyLine.peek();
        if (front != null && !front.getId().equals(claim.getId())) {
            throw new RuleException(claim.getId() + " is not next in line on " + claim.getPolicyId() + ". Decide "
                    + front.getId() + " (filed " + front.getFiledDate() + ") first: claims on the same policy "
                    + "share one coverage limit, so they are paid first come, first served.");
        }
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

    public Payout payoutOf(Claim claim) {
        return store.payoutFor(claim.getId());
    }

    public User holderOf(Claim claim) {
        Policy policy = store.policies().findById(claim.getPolicyId());
        return policy == null ? null : store.users().findById(policy.getOwnerId());
    }

    public String holderName(Claim claim) {
        User holder = holderOf(claim);
        return holder == null ? "(removed)" : holder.getUsername();
    }

    public String userName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        User user = store.users().findById(userId);
        return user == null ? userId : user.getFullName();
    }

    public int countClaims()        { return store.claims().getAll().size(); }
    public int countPayouts()       { return store.payouts().getAll().size(); }
    public int countNeedingAction() { return search("", Filter.NEEDS_ACTION).size(); }

    public int countAwaitingReply(User user) {
        int count = 0;
        for (Claim c : claimsOf(user)) {
            if (c.getStatus() == Claim.Status.INFO_REQUESTED) {
                count++;
            }
        }
        return count;
    }

    public Payout approve(String claimId, User admin, String note, String adminPassword) throws ICSException {
        requireReviewer(admin);
        String cleanNote = cleanNote(note);
        auth.confirmPassword(admin, adminPassword);
        Claim claim = getClaim(claimId);
        if (claim.getStatus() != Claim.Status.UNDER_REVIEW) {
            throw new RuleException("A claim cannot move from " + claim.getStatus() + " to APPROVED.");
        }
        checkTurnToApprove(claimId);
        Policy policy = policyOf(claim);
        double amount = policy.calculatePayout(claim.getClaimedAmount());
        if (amount <= 0) {
            throw new RuleException("Nothing is payable on this claim (the deductible is not exceeded, or the "
                    + "coverage limit is used up). Deny it with a reason instead.");
        }
        String breakdown = String.join("\n", policy.explainPayout(claim.getClaimedAmount()));

        claim.approve(amount, admin.getId());
        claim.setReviewerNote(cleanNote);
        policy.applyPayout(claim.getClaimedAmount(), amount);
        Payout payout = new Payout(null, claim.getId(), amount, today(), admin.getId(), breakdown);
        store.payouts().add(payout);
        store.policies().update(policy);
        store.claims().update(claim);
        return payout;
    }

    public void deny(String claimId, User admin, Claim.Reason reason, String note) throws ICSException {
        requireReviewer(admin);
        Validate.notNull("Denial reason", reason);
        String cleanNote = cleanNote(note);
        Claim claim = getClaim(claimId);
        claim.deny(reason, admin.getId());
        claim.setReviewerNote(cleanNote);
        store.claims().update(claim);
    }

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

    public void resumeReview(String claimId, User admin) throws ICSException {
        requireReviewer(admin);
        Claim claim = getClaim(claimId);
        claim.changeStatus(Claim.Status.UNDER_REVIEW);
        store.claims().update(claim);
    }

    public void close(String claimId, User admin) throws ICSException {
        requireReviewer(admin);
        Claim claim = getClaim(claimId);
        claim.changeStatus(Claim.Status.CLOSED);
        store.claims().update(claim);
    }

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

    private void requireStarted(Policy policy) throws InvalidInputException {
        if (policy.getStatus(today()) == Policy.Status.UPCOMING) {
            throw new InvalidInputException("Policy", policy.getId() + " starts on " + policy.getStartDate()
                    + ". You can apply for a claim once it is active.");
        }
    }

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

    private static String cleanNote(String note) throws InvalidInputException {
        String cleaned = note == null ? "" : note.trim();
        if (cleaned.length() > Claim.MAX_NOTE_LENGTH) {
            throw new InvalidInputException("Note to the user", "The note can be at most " + Claim.MAX_NOTE_LENGTH + " characters.");
        }
        return cleaned;
    }

    private static int compareForAdmin(Claim a, Claim b) {
        int byGroup = Integer.compare(adminGroup(a.getStatus()), adminGroup(b.getStatus()));
        if (byGroup != 0) {
            return byGroup;
        }
        int byDate = a.getFiledDate().compareTo(b.getFiledDate());
        return byDate != 0 ? byDate : a.getId().compareTo(b.getId());
    }

    private static int compareFiledOrder(Claim a, Claim b) {
        int byDate = a.getFiledDate().compareTo(b.getFiledDate());
        return byDate != 0 ? byDate : a.getId().compareTo(b.getId());
    }

    private static int compareHighestClaimed(Claim a, Claim b) {
        int byAmount = Double.compare(b.getClaimedAmount(), a.getClaimedAmount());
        return byAmount != 0 ? byAmount : compareFiledOrder(a, b);
    }

    private static int adminGroup(Claim.Status s) {
        if (s == Claim.Status.UNDER_REVIEW)   { return 0; }
        if (s == Claim.Status.INFO_REQUESTED) { return 1; }
        if (s == Claim.Status.FILED)          { return 2; }
        if (s == Claim.Status.APPROVED || s == Claim.Status.DENIED || s == Claim.Status.PAID) { return 3; }
        return 4;
    }

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
