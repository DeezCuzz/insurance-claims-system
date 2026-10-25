package ics;

import ics.exception.AccessDeniedException;
import ics.exception.DataStorageException;
import ics.exception.InvalidInputException;
import ics.exception.LoginException;
import ics.exception.RuleException;
import ics.model.Admin;
import ics.model.Applicant;
import ics.model.AutoPolicy;
import ics.model.Claim;
import ics.model.HealthPolicy;
import ics.model.Payout;
import ics.model.Policy;
import ics.model.User;
import ics.repository.DataStore;
import ics.service.AuthService;
import ics.service.ClaimService;
import ics.service.PolicyService;
import ics.service.UserService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Quick check that model + repository + user service work together.
 * Run it after every change to those packages. It uses a temporary folder, so it never touches real data.
 */
public class SmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    private interface Action {
        void run() throws Exception;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    /** Passes only if the action is refused with an InvalidInputException about this field. Returns it (or null). */
    private static InvalidInputException expectFieldError(String name, String field, Action action) {
        try {
            action.run();
            check(name, false);
        } catch (InvalidInputException e) {
            check(name + (field.equals(e.getFieldName()) ? "" : " (field was " + e.getFieldName() + ")"),
                    field.equals(e.getFieldName()));
            return e;
        } catch (Exception e) {
            check(name + " (wrong exception: " + e.getClass().getSimpleName() + ")", false);
        }
        return null;
    }

    /** Passes only if the action throws this type of exception. Returns it so the test can look inside. */
    private static <T extends Exception> T expect(String name, Class<T> type, Action action) {
        try {
            action.run();
            check(name, false);
            return null;
        } catch (Exception e) {
            if (type.isInstance(e)) {
                check(name, true);
                return type.cast(e);
            }
            check(name + " (wrong exception: " + e.getClass().getSimpleName() + ")", false);
            return null;
        }
    }

    private static String juanId(DataStore store) {
        return store.findUserByUsername("juan").getId();
    }

    /** The ten things typed on the Policies screen, kept together for the tests. */
    private static final class PolicyForm {
        final Policy.Type type;
        final String ownerId, start, end, deductible, limit, percent, waiting, vehicle, plate;
        PolicyForm(Policy.Type type, String ownerId, String start, String end, String deductible, String limit,
                   String percent, String waiting, String vehicle, String plate) {
            this.type = type; this.ownerId = ownerId; this.start = start; this.end = end; this.deductible = deductible;
            this.limit = limit; this.percent = percent; this.waiting = waiting; this.vehicle = vehicle; this.plate = plate;
        }
    }

    private static Policy issue(PolicyService service, PolicyForm f) throws Exception {
        return service.issue(f.type, f.ownerId, f.start, f.end, f.deductible, f.limit, f.percent, f.waiting, f.vehicle, f.plate);
    }

    /** Update ignores the holder and type in the form: they cannot change. */
    private static Policy update(PolicyService service, String id, PolicyForm f) throws Exception {
        return service.update(id, f.start, f.end, f.deductible, f.limit, f.percent, f.waiting, f.vehicle, f.plate);
    }

    private static List<Claim> withStatus(DataStore store, Claim.Status status) {
        List<Claim> result = new java.util.ArrayList<>();
        for (Claim c : store.claims().getAll()) {
            if (c.getStatus() == status) {
                result.add(c);
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ics-smoke");
        DataStore store = new DataStore(dir);
        store.loadAll();

        System.out.println("Users, roles, lockout, auto-numbering");
        User admin = new Admin(null, "admin", AuthService.hash("admin123"), "System Admin", "0900 000 0000", "", 0, false);
        User maria = new Applicant(null, "maria", AuthService.hash("maria123"), "Maria Santos", "0917 123 4567", "Tarlac City", 0, false);
        store.users().add(admin);
        store.users().add(maria);
        check("first user gets USR-0001", admin.getId().equals("USR-0001"));
        check("second user gets USR-0002", maria.getId().equals("USR-0002"));
        check("Admin manages users and reviews claims, but does not apply",
                admin.canManageUsers() && admin.canReviewClaims() && !admin.canApplyForClaims());
        check("User applies for claims, but cannot manage or review",
                maria.canApplyForClaims() && !maria.canManageUsers() && !maria.canReviewClaims());
        check("roles are ADMIN and USER", admin.getRole() == User.Role.ADMIN && maria.getRole() == User.Role.USER);
        check("password hash matches", maria.verifyPassword(AuthService.hash("maria123")));
        check("wrong password does not match", !maria.verifyPassword(AuthService.hash("nope")));
        maria.recordFailedAttempt();
        maria.recordFailedAttempt();
        check("two failures do not block", !maria.isBlocked());
        maria.recordFailedAttempt();
        check("third failure blocks", maria.isBlocked());
        store.users().update(maria);

        System.out.println("Admin creates accounts (UserService)");
        UserService accounts = new UserService(store);
        expectFieldError("password shorter than 8 is refused", "Password",
                () -> accounts.createAccount(User.Role.USER, "juan", "short1", "short1", "Juan dela Cruz", "0917 111 2222", ""));
        expectFieldError("password without a number is refused", "Password",
                () -> accounts.createAccount(User.Role.USER, "juan", "abcdefgh", "abcdefgh", "Juan dela Cruz", "0917 111 2222", ""));
        expectFieldError("confirm password must match", "Confirm password",
                () -> accounts.createAccount(User.Role.USER, "juan", "Passw0rd1", "Passw0rd2", "Juan dela Cruz", "0917 111 2222", ""));
        expectFieldError("duplicate username is refused, any capitalisation", "Username",
                () -> accounts.createAccount(User.Role.USER, "MARIA", "Passw0rd1", "Passw0rd1", "Another Maria", "0917 000 0000", ""));
        expectFieldError("missing full name is refused", "Full name",
                () -> accounts.createAccount(User.Role.USER, "juan", "Passw0rd1", "Passw0rd1", " ", "0917 111 2222", ""));
        expectFieldError("missing contact number is refused", "Contact number",
                () -> accounts.createAccount(User.Role.USER, "juan", "Passw0rd1", "Passw0rd1", "Juan dela Cruz", "", ""));
        check("refused attempts created nothing", store.users().getAll().size() == 2);
        User juan = accounts.createAccount(User.Role.USER, "juan", "Passw0rd1", "Passw0rd1",
                "Juan dela Cruz", "0917 111 2222", "Blk 5 | Concepcion, Tarlac");
        check("new account gets USR-0003 and is a User", juan.getId().equals("USR-0003") && juan instanceof Applicant);
        check("password is stored hashed, not as typed",
                !juan.getPasswordHash().equals("Passw0rd1") && juan.verifyPassword(AuthService.hash("Passw0rd1")));
        User newAdmin = accounts.createAccount(User.Role.ADMIN, "boss", "Passw0rd1", "Passw0rd1", "Office Boss", "0900 111 1111", "");
        check("an Admin account can be created too", newAdmin instanceof Admin);
        accounts.updateProfile(juan.getId(), "Juan D. Cruz", "0917 999 0000", "Blk 9 | Tarlac City");
        User edited = store.users().findById(juan.getId());
        check("profile update keeps username and password",
                edited.getFullName().equals("Juan D. Cruz") && edited.getUsername().equals("juan")
                        && edited.verifyPassword(AuthService.hash("Passw0rd1")));
        expectFieldError("update with an empty name changes nothing", "Full name",
                () -> accounts.updateProfile(juan.getId(), "", "0917 999 0000", "Tarlac City"));
        check("failed update left the old name", store.users().findById(juan.getId()).getFullName().equals("Juan D. Cruz"));
        User toBlock = store.users().findById(juan.getId());
        toBlock.recordFailedAttempt();
        toBlock.recordFailedAttempt();
        toBlock.recordFailedAttempt();
        store.users().update(toBlock);
        check("account is blocked after 3 failures", store.users().findById(juan.getId()).isBlocked());
        accounts.unblock(juan.getId());
        User unblocked = store.users().findById(juan.getId());
        check("Admin unblock clears the block and the counter", !unblocked.isBlocked() && unblocked.getFailedAttempts() == 0);
        check("user search finds by name", accounts.search("santos").size() == 1);

        System.out.println("Policies and payout math");
        HealthPolicy health = new HealthPolicy(null, juan.getId(), LocalDate.of(2026, 10, 10),
                LocalDate.of(2027, 10, 10), 5_000, 100_000, 0, false, 0.8, 30, 0);
        AutoPolicy auto = new AutoPolicy(null, juan.getId(), LocalDate.of(2026, 10, 10),
                LocalDate.of(2027, 10, 10), 10_000, 300_000, 0, false, 800_000, "ABC 123");
        store.policies().add(health);
        store.policies().add(auto);
        check("policies get POL-0001 and POL-0002", health.getId().equals("POL-0001") && auto.getId().equals("POL-0002"));
        check("health premium = 4% of limit", health.calculatePremium() == 4_000.0);
        check("auto premium = 3% of vehicle value, 10% off for a 10,000 deductible", auto.calculatePremium() == 21_600.0);
        check("auto 120,000 estimate pays 110,000", auto.calculatePayout(120_000) == 110_000.0);
        check("auto 350,000 estimate is capped at 300,000", auto.calculatePayout(350_000) == 300_000.0);
        double first = health.calculatePayout(50_000);
        check("health claim 1 pays 36,000", first == 36_000.0);
        check("health explanation ends with the same payout", health.explainPayout(50_000).get(health.explainPayout(50_000).size() - 1).endsWith("PHP 36,000.00"));
        check("health explanation shows the deductible step", health.explainPayout(50_000).get(1).contains("PHP 5,000.00"));
        check("auto explanation ends with the same payout", auto.explainPayout(120_000).get(auto.explainPayout(120_000).size() - 1).endsWith("PHP 110,000.00"));
        health.applyPayout(50_000, first);
        check("deductible is now used up", health.getRemainingDeductible() == 0.0);
        check("remaining limit is 64,000", health.getRemainingLimit() == 64_000.0);
        check("health claim 2 is capped at 64,000", health.calculatePayout(100_000) == 64_000.0);
        store.policies().update(health);

        System.out.println("Claim life cycle");
        Claim claim = new Claim(auto.getId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3),
                "Rear-end collision", 120_000);
        store.claims().add(claim);
        check("claim gets CLM-0001 and starts FILED", claim.getId().equals("CLM-0001") && claim.getStatus() == Claim.Status.FILED);
        try {
            claim.changeStatus(Claim.Status.PAID);
            check("FILED -> PAID is blocked", false);
        } catch (RuleException e) {
            check("FILED -> PAID is blocked", true);
        }
        claim.changeStatus(Claim.Status.UNDER_REVIEW);
        try {
            claim.deny(null, admin.getId());
            check("deny without a reason is blocked", false);
        } catch (InvalidInputException e) {
            check("deny without a reason is blocked", true);
        }
        check("failed deny did not change the status", claim.getStatus() == Claim.Status.UNDER_REVIEW);
        claim.approve(auto.calculatePayout(claim.getClaimedAmount()), admin.getId());
        check("C5: approving also sets the paid amount", claim.getPaidAmount() == 110_000.0 && claim.getStatus() == Claim.Status.APPROVED);
        auto.applyPayout(claim.getClaimedAmount(), claim.getPaidAmount());
        try {
            claim.approve(1, admin.getId());
            check("T17 / C6: approving twice is blocked", false);
        } catch (RuleException e) {
            check("T17 / C6: approving twice is blocked", true);
        }
        store.claims().update(claim);
        store.policies().update(auto);
        Payout payout = new Payout(null, claim.getId(), claim.getPaidAmount(), LocalDate.of(2026, 11, 10), admin.getId(), "");
        store.payouts().add(payout);
        check("payout gets PAY-0001", payout.getId().equals("PAY-0001"));

        Claim bad = new Claim(health.getId(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), "Hospital bill", 9_000);
        bad.reject(Claim.Reason.WAITING_PERIOD);
        store.claims().add(bad);

        System.out.println("Reload from the txt files");
        DataStore again = new DataStore(dir);
        again.loadAll();
        check("4 users reloaded", again.users().getAll().size() == 4);
        User reloadedMaria = again.findUserByUsername("MARIA");
        check("blocked flag survived a restart", reloadedMaria.isBlocked());
        check("user comes back as an Applicant with her profile",
                reloadedMaria instanceof Applicant && reloadedMaria.getFullName().equals("Maria Santos")
                        && reloadedMaria.getAddress().equals("Tarlac City"));
        check("Admin comes back as an Admin", again.users().findById("USR-0001") instanceof Admin);
        check("pipe character survived the file",
                again.users().findById(juan.getId()).getAddress().equals("Blk 9 | Tarlac City"));
        check("policy keeps the ID of the user it was issued to",
                again.policies().findById("POL-0001").getOwnerId().equals(juan.getId()));
        Policy reloadedHealth = again.policies().findById("POL-0001");
        check("policy comes back as a HealthPolicy", reloadedHealth instanceof HealthPolicy);
        check("limit used survived", reloadedHealth.getRemainingLimit() == 64_000.0);
        check("deductible met survived", ((HealthPolicy) reloadedHealth).getRemainingDeductible() == 0.0);
        check("policy comes back as an AutoPolicy", again.policies().findById("POL-0002") instanceof AutoPolicy);
        List<Claim> paidClaims = withStatus(again, Claim.Status.APPROVED);
        check("approved (paid) claim reloaded with its amount", paidClaims.size() == 1 && paidClaims.get(0).getPaidAmount() == 110_000.0);
        Claim reloadedBad = again.claims().findById("CLM-0002");
        check("denial reason survived", reloadedBad.getReason() == Claim.Reason.WAITING_PERIOD);
        check("payout reloaded", again.payoutFor("CLM-0001") != null);

        System.out.println("Delete and bad data");
        User temp = User.create(User.Role.USER, null, "temp", AuthService.hash("Passw0rd1"),
                "Temp Person", "0900 000 0000", "", 0, false);
        again.users().add(temp);
        check("fifth account is USR-0005", temp.getId().equals("USR-0005"));
        again.users().delete("USR-0005");
        check("delete removes the record", again.users().getAll().size() == 4);
        try {
            again.users().delete("USR-9999");
            check("deleting a missing ID is reported", false);
        } catch (DataStorageException e) {
            check("deleting a missing ID is reported", true);
        }
        Files.writeString(dir.resolve("users.txt"), "USR-0001|ADMIN|Only three fields\n");
        DataStore damaged = new DataStore(dir);
        damaged.loadAll();                                       // fix D4: no exception any more
        check("D4: a broken line is skipped with a warning naming the file and line",
                damaged.loadWarnings().size() == 1 && damaged.loadWarnings().get(0).startsWith("users.txt line 1"));
        check("D4: the damaged file is kept as users.txt.bak", Files.readString(dir.resolve("users.txt.bak")).contains("Only three fields"));

        System.out.println("Login and lockout (AuthService)");
        Path authDir = Files.createTempDirectory("ics-auth");
        DataStore authStore = new DataStore(authDir);
        authStore.loadAll();
        AuthService auth = new AuthService(authStore);
        check("an empty system gets a first Admin", auth.ensureFirstAdmin());
        check("the first Admin is created only once", !auth.ensureFirstAdmin());
        check("exactly one account exists", authStore.users().getAll().size() == 1);
        User firstAdmin = auth.login(AuthService.DEFAULT_ADMIN_USERNAME, AuthService.DEFAULT_ADMIN_PASSWORD);
        check("the default Admin can log in", firstAdmin instanceof Admin);
        check("username is not case-sensitive at login", auth.login("ADMIN", "Admin123").getId().equals(firstAdmin.getId()));
        new UserService(authStore).createAccount(User.Role.USER, "maria", "Passw0rd1", "Passw0rd1",
                "Maria Santos", "0917 123 4567", "");
        LoginException nobody = expect("unknown username is refused", LoginException.class,
                () -> auth.login("nobody", "Passw0rd1"));
        check("unknown username has nothing to count", nobody != null && nobody.getTriesLeft() == -1);
        check("no account was created by a failed login", authStore.users().getAll().size() == 2);
        LoginException one = expect("wrong password is refused", LoginException.class,
                () -> auth.login("maria", "wrong"));
        check("first wrong password leaves 2 tries", one != null && one.getTriesLeft() == 2);
        check("the message does not say which part was wrong", one != null && one.getMessage().equals("Wrong username or password."));

        DataStore reopened = new DataStore(authDir);        // simulate closing and reopening the program
        reopened.loadAll();
        AuthService auth2 = new AuthService(reopened);
        check("the failed try was saved and survives a restart",
                reopened.findUserByUsername("maria").getFailedAttempts() == 1);
        LoginException two = expect("second wrong password is refused", LoginException.class,
                () -> auth2.login("maria", "wrong"));
        check("second wrong password leaves 1 try", two != null && two.getTriesLeft() == 1);
        LoginException third = expect("third wrong password blocks the account", LoginException.class,
                () -> auth2.login("maria", "wrong"));
        check("the third try reports that it just blocked", third != null && third.isJustBlocked());
        LoginException afterBlock = expect("a blocked account refuses even the right password",
                LoginException.class, () -> auth2.login("maria", "Passw0rd1"));
        check("a later attempt is not reported as a new block", afterBlock != null && !afterBlock.isJustBlocked());

        DataStore blockedStore = new DataStore(authDir);
        blockedStore.loadAll();
        check("the block survives a restart", blockedStore.findUserByUsername("maria").isBlocked());
        new UserService(blockedStore).unblock(blockedStore.findUserByUsername("maria").getId());
        AuthService auth3 = new AuthService(blockedStore);
        User back = auth3.login("maria", "Passw0rd1");
        check("after an Admin unblocks, the right password works again", back instanceof Applicant && !back.isBlocked());
        check("unblocking cleared the failed-try count", back.getFailedAttempts() == 0);
        expect("one wrong try after unblocking", LoginException.class, () -> auth3.login("maria", "wrong"));
        User cleared = auth3.login("maria", "Passw0rd1");
        check("a correct login clears earlier wrong tries", cleared.getFailedAttempts() == 0);
        DataStore afterClear = new DataStore(authDir);
        afterClear.loadAll();
        check("the saved count was cleared too",
                afterClear.findUserByUsername("maria").getFailedAttempts() == 0);

        System.out.println("Policies screen rules (PolicyService)");
        Path polDir = Files.createTempDirectory("ics-policy");
        DataStore ps = new DataStore(polDir);
        ps.loadAll();
        AuthService polAuth = new AuthService(ps);
        polAuth.ensureFirstAdmin();
        User polAdmin = polAuth.login("admin", "Admin123");
        UserService polUsers = new UserService(ps);
        User holder = polUsers.createAccount(User.Role.USER, "maria", "Passw0rd1", "Passw0rd1", "Maria Santos", "0917 123 4567", "");
        polUsers.createAccount(User.Role.USER, "juan", "Passw0rd1", "Passw0rd1", "Juan dela Cruz", "0917 111 2222", "");
        PolicyService pol = new PolicyService(ps, polAuth);
        check("only User accounts can hold policies", pol.policyHolders().size() == 2
                && pol.policyHolders().stream().noneMatch(u -> u.getId().equals(polAdmin.getId())));
        check("health premium preview is 4% of the limit", pol.previewPremium(Policy.Type.HEALTH, "100,000", "", "5000") == 4_000.0);
        check("auto premium preview is 3% of the vehicle value", pol.previewPremium(Policy.Type.AUTO, "", "800000", "") == 24_000.0);
        expectFieldError("preview with letters is refused", "Coverage limit",
                () -> pol.previewPremium(Policy.Type.HEALTH, "lots", "", ""));

        PolicyForm healthForm = new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2026-10-16", "2027-10-16",
                "5000", "100,000", "80", "30", "", "");
        Policy p1 = issue(pol, healthForm);
        check("first policy is POL-0001 and a HealthPolicy", p1.getId().equals("POL-0001") && p1 instanceof HealthPolicy);
        check("typed 80 became a rate of 0.8", ((HealthPolicy) p1).getReimbursementRate() == 0.8);
        check("typed '100,000' became 100000", p1.getCoverageLimit() == 100_000.0);
        check("status is worked out from the dates", p1.getStatus(LocalDate.of(2026, 12, 1)) == Policy.Status.ACTIVE
                && p1.getStatus(LocalDate.of(2028, 1, 1)) == Policy.Status.EXPIRED
                && p1.getStatus(LocalDate.of(2026, 1, 1)) == Policy.Status.UPCOMING);

        expectFieldError("a policy cannot be issued to an Admin", "Policy holder",
                () -> issue(pol, new PolicyForm(Policy.Type.HEALTH, polAdmin.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", "")));
        expectFieldError("no holder chosen is refused", "Policy holder",
                () -> issue(pol, new PolicyForm(Policy.Type.HEALTH, "", "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", "")));
        expectFieldError("a badly typed date is refused", "Start date",
                () -> issue(pol, new PolicyForm(Policy.Type.AUTO, holder.getId(), "16/10/2026", "2027-10-16", "5000", "100000", "", "", "800000", "ABC 123")));
        expectFieldError("end date before start date is refused", "End date",
                () -> issue(pol, new PolicyForm(Policy.Type.AUTO, holder.getId(), "2026-10-16", "2026-10-01", "5000", "100000", "", "", "800000", "ABC 123")));
        expectFieldError("deductible must be lower than the limit", "Deductible",
                () -> issue(pol, new PolicyForm(Policy.Type.AUTO, holder.getId(), "2026-10-16", "2027-10-16", "100000", "100000", "", "", "800000", "ABC 123")));
        expectFieldError("letters in the limit are refused", "Coverage limit",
                () -> issue(pol, new PolicyForm(Policy.Type.AUTO, holder.getId(), "2026-10-16", "2027-10-16", "5000", "a lot", "", "", "800000", "ABC 123")));
        expectFieldError("reimbursement above 100% is refused", "Reimbursement (%)",
                () -> issue(pol, new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2028-01-01", "2029-01-01", "5000", "100000", "150", "30", "", "")));
        expectFieldError("waiting period must be a whole number", "Waiting period (days)",
                () -> issue(pol, new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2028-01-01", "2029-01-01", "5000", "100000", "80", "abc", "", "")));
        expectFieldError("an Auto policy needs a plate number", "Plate number",
                () -> issue(pol, new PolicyForm(Policy.Type.AUTO, holder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "", "", "800000", " ")));
        expectFieldError("a second Health policy with overlapping dates is refused", "Start date",
                () -> issue(pol, new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2027-06-01", "2028-06-01", "5000", "100000", "80", "30", "", "")));
        check("refused policies created nothing", ps.policies().getAll().size() == 1);

        Policy p2 = issue(pol, new PolicyForm(Policy.Type.AUTO, holder.getId(), "2026-10-16", "2027-10-16",
                "10,000", "300000", "", "", "800000", "ABC 123"));
        check("a different type for the same dates is allowed", p2.getId().equals("POL-0002") && p2 instanceof AutoPolicy);
        Policy p3 = issue(pol, new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2027-10-17", "2028-10-16",
                "5000", "100000", "80", "30", "", ""));
        check("the same type after the first one ends is allowed", p3.getId().equals("POL-0003"));

        System.out.println("  (editing)");
        HealthPolicy stored = (HealthPolicy) ps.policies().findById("POL-0001");
        stored.applyPayout(50_000, 36_000);          // pretend a claim was paid
        ps.policies().update(stored);
        expectFieldError("limit cannot drop below what was paid out", "Coverage limit",
                () -> update(pol, "POL-0001", new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2026-10-16", "2027-10-16", "5000", "30000", "80", "30", "", "")));
        expectFieldError("deductible cannot drop below what was met", "Deductible",
                () -> update(pol, "POL-0001", new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2026-10-16", "2027-10-16", "1000", "100000", "80", "30", "", "")));
        check("failed updates changed nothing", ps.policies().findById("POL-0001").getCoverageLimit() == 100_000.0);
        expectFieldError("moving a policy onto the next one is refused", "Start date",
                () -> update(pol, "POL-0001", new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2027-01-01", "2027-12-31", "5000", "100000", "80", "30", "", "")));
        Policy editedPolicy = update(pol, "POL-0001", new PolicyForm(Policy.Type.AUTO, juanId(ps), "2026-10-16", "2027-10-16",
                "6000", "120000", "90", "10", "", ""));
        check("update keeps the holder and the type (they cannot be changed)",
                editedPolicy.getOwnerId().equals(holder.getId()) && editedPolicy instanceof HealthPolicy);
        check("update applies the new terms", editedPolicy.getCoverageLimit() == 120_000.0
                && editedPolicy.getDeductible() == 6_000.0 && ((HealthPolicy) editedPolicy).getReimbursementRate() == 0.9
                && ((HealthPolicy) editedPolicy).getWaitingPeriodDays() == 10);
        check("update keeps what was already used", editedPolicy.getLimitUsed() == 36_000.0
                && ((HealthPolicy) editedPolicy).getDeductibleMet() == 5_000.0);

        System.out.println("  (cancel and delete need the Admin password)");
        expect("a wrong password cannot cancel", AccessDeniedException.class, () -> pol.cancel("POL-0001", polAdmin, "nope"));
        check("the policy is still live after a wrong password", !ps.policies().findById("POL-0001").isCancelled());
        expect("a User cannot cancel, even with their own password", AccessDeniedException.class,
                () -> pol.cancel("POL-0001", holder, "Passw0rd1"));
        pol.cancel("POL-0001", polAdmin, "Admin123");
        Policy cancelled = ps.policies().findById("POL-0001");
        check("the right password cancels it", cancelled.isCancelled() && cancelled.getStatus(LocalDate.of(2026, 12, 1)) == Policy.Status.CANCELLED);
        check("cancelling keeps the record and its history", cancelled.getLimitUsed() == 36_000.0);
        expect("cancelling twice is refused", RuleException.class, () -> pol.cancel("POL-0001", polAdmin, "Admin123"));
        expect("a cancelled policy cannot be edited", RuleException.class,
                () -> update(pol, "POL-0001", new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", "")));
        Policy replacement = issue(pol, new PolicyForm(Policy.Type.HEALTH, holder.getId(), "2026-10-16", "2027-10-16",
                "5000", "100000", "80", "30", "", ""));
        check("a cancelled policy no longer blocks a new one for the same dates", replacement.getId().equals("POL-0004"));

        ps.claims().add(new Claim("POL-0002", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), "Collision", 50_000));
        expect("a policy with claims cannot be deleted", RuleException.class, () -> pol.delete("POL-0002", polAdmin, "Admin123"));
        expect("a wrong password cannot delete", AccessDeniedException.class, () -> pol.delete("POL-0003", polAdmin, "nope"));
        check("the policy survived the wrong password", ps.policies().findById("POL-0003") != null);
        pol.delete("POL-0003", polAdmin, "Admin123");
        check("a policy without claims is deleted with the right password", ps.policies().findById("POL-0003") == null);

        check("search finds by holder name", pol.search("santos").size() == 3);
        check("search finds by type", pol.search("auto").size() == 1);
        check("search finds by status", pol.search("cancelled").size() == 1);
        check("blank search lists everything", pol.search("").size() == 3);
        DataStore polAgain = new DataStore(polDir);
        polAgain.loadAll();
        check("policies, owners and the cancelled flag survive a restart",
                polAgain.policies().getAll().size() == 3 && polAgain.policies().findById("POL-0001").isCancelled()
                        && polAgain.policies().findById("POL-0004").getOwnerId().equals(holder.getId()));

        System.out.println("Claims: applying and the automatic rules (ClaimService)");
        Path claimDir = Files.createTempDirectory("ics-claims");
        DataStore cs = new DataStore(claimDir);
        cs.loadAll();
        AuthService cAuth = new AuthService(cs);
        cAuth.ensureFirstAdmin();
        User rvAdmin = cAuth.login("admin", "Admin123");
        UserService cUsers = new UserService(cs);
        User rvMaria = cUsers.createAccount(User.Role.USER, "maria", "Passw0rd1", "Passw0rd1", "Maria Santos", "0917 123 4567", "");
        User rvJuan = cUsers.createAccount(User.Role.USER, "juan", "Passw0rd1", "Passw0rd1", "Juan dela Cruz", "0917 111 2222", "");
        PolicyService cPolicies = new PolicyService(cs, cAuth);
        issue(cPolicies, new PolicyForm(Policy.Type.HEALTH, rvMaria.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", ""));
        issue(cPolicies, new PolicyForm(Policy.Type.AUTO, rvMaria.getId(), "2026-10-16", "2027-10-16", "10000", "300000", "", "", "800000", "ABC 123"));
        issue(cPolicies, new PolicyForm(Policy.Type.AUTO, rvJuan.getId(), "2026-10-16", "2027-10-16", "10000", "300000", "", "", "600000", "JUAN 1"));
        issue(cPolicies, new PolicyForm(Policy.Type.HEALTH, rvJuan.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "0", "", ""));
        cPolicies.cancel("POL-0004", rvAdmin, "Admin123");
        cs.policies().add(new AutoPolicy(null, rvJuan.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), 1000, 50_000, 50_000, false, 100_000, "FULL 1"));
        Clock today = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
        ClaimService cl = new ClaimService(cs, cAuth, today);

        expect("an Admin cannot apply for a claim", AccessDeniedException.class,
                () -> cl.file(rvAdmin, "POL-0002", "2026-11-01", "Collision", "120000"));
        expect("a User cannot claim on someone else's policy", AccessDeniedException.class,
                () -> cl.file(rvJuan, "POL-0002", "2026-11-01", "Collision", "120000"));
        expectFieldError("no policy chosen is refused", "Policy", () -> cl.file(rvMaria, "", "2026-11-01", "Collision", "120000"));
        expectFieldError("an incident in the future is refused", "Incident date", () -> cl.file(rvMaria, "POL-0002", "2026-11-11", "Collision", "120000"));
        expectFieldError("a badly typed date is refused", "Incident date", () -> cl.file(rvMaria, "POL-0002", "yesterday", "Collision", "120000"));
        expectFieldError("a blank description is refused", "Description", () -> cl.file(rvMaria, "POL-0002", "2026-11-01", "  ", "120000"));
        expectFieldError("letters in the amount are refused", "Claimed amount", () -> cl.file(rvMaria, "POL-0002", "2026-11-01", "Collision", "a lot"));
        expectFieldError("an amount of zero is refused", "Claimed amount", () -> cl.file(rvMaria, "POL-0002", "2026-11-01", "Collision", "0"));
        check("typing mistakes saved nothing", cs.claims().getAll().isEmpty());

        check("the service knows what day it is", cl.today().equals(LocalDate.of(2026, 11, 10)));
        check("a User sees only their own policies", cl.policiesOf(rvMaria).size() == 2
                && cl.policiesOf(rvMaria).stream().allMatch(p -> p.getOwnerId().equals(rvMaria.getId())));
        check("active policies are listed before cancelled or expired ones",
                cl.policiesOf(rvJuan).size() == 3 && cl.policiesOf(rvJuan).get(0).getId().equals("POL-0003"));
        check("the estimate uses the policy's own formula", cl.estimate(rvMaria, "POL-0002", "120,000") == 110_000.0);
        expect("no estimate on someone else's policy", AccessDeniedException.class, () -> cl.estimate(rvJuan, "POL-0002", "120000"));
        expectFieldError("no estimate for letters", "Claimed amount", () -> cl.estimate(rvMaria, "POL-0002", "lots"));
        check("precheck: a normal incident breaks no rule", cl.precheck(rvMaria, "POL-0002", "2026-11-01") == null);
        check("precheck: warns about the waiting period", cl.precheck(rvMaria, "POL-0001", "2026-11-05") == Claim.Reason.WAITING_PERIOD);
        check("precheck saves nothing", cs.claims().getAll().isEmpty());
        Claim c1 = cl.file(rvMaria, "POL-0002", "2026-11-01", "Rear-end collision", "120,000");
        check("precheck: now warns that it would be a duplicate", cl.precheck(rvMaria, "POL-0002", "2026-11-01") == Claim.Reason.DUPLICATE_CLAIM);
        check("a good claim is CLM-0001 and goes straight to review", c1.getId().equals("CLM-0001") && c1.getStatus() == Claim.Status.UNDER_REVIEW);
        check("it is stamped with today's date and the reserve is set", c1.getFiledDate().equals(LocalDate.of(2026, 11, 10)) && c1.getReserveAmount() == 120_000.0);
        Claim big = cl.file(rvJuan, "POL-0003", "2026-11-01", "Big loss", "500000");
        check("the reserve never exceeds what the policy has left", big.getReserveAmount() == 300_000.0);

        Claim waiting = cl.file(rvMaria, "POL-0001", "2026-11-05", "Hospital bill", "9000");
        check("incident inside the waiting period is rejected", waiting.getStatus() == Claim.Status.REJECTED && waiting.getReason() == Claim.Reason.WAITING_PERIOD);
        check("a rejected claim is still recorded", cs.claims().findById(waiting.getId()) != null);
        check("incident before the policy started is rejected",
                cl.file(rvMaria, "POL-0002", "2026-10-01", "Scratch", "5000").getReason() == Claim.Reason.OUTSIDE_COVERAGE_PERIOD);
        check("the same incident twice is a duplicate",
                cl.file(rvMaria, "POL-0002", "2026-11-01", "Rear-end collision again", "120000").getReason() == Claim.Reason.DUPLICATE_CLAIM);
        check("a cancelled policy rejects claims",
                cl.file(rvJuan, "POL-0004", "2026-11-01", "Clinic visit", "3000").getReason() == Claim.Reason.POLICY_INACTIVE);
        check("a used-up limit rejects claims",
                new ClaimService(cs, cAuth, Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), ZoneOffset.UTC))
                        .file(rvJuan, "POL-0005", "2025-05-20", "Dent", "9000").getReason() == Claim.Reason.LIMIT_EXHAUSTED);
        check("a claim filed more than 90 days after the incident is late",
                new ClaimService(cs, cAuth, Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"), ZoneOffset.UTC))
                        .file(rvMaria, "POL-0002", "2026-10-20", "Old dent", "20000").getReason() == Claim.Reason.LATE_FILING);

        System.out.println("  (reviewing)");
        Policy c1Policy = cl.policyOf(cl.getClaim(c1.getId()));
        List<String> c1Steps = c1Policy.explainPayout(c1.getClaimedAmount());
        check("the review screen can see what would be paid now", c1Policy.calculatePayout(c1.getClaimedAmount()) == 110_000.0
                && c1Steps.get(c1Steps.size() - 1).endsWith("PHP 110,000.00") && cl.holderOf(c1).getUsername().equals("maria"));
        check("claims needing action are listed", cl.search("", ClaimService.Filter.NEEDS_ACTION).stream().anyMatch(c -> c.getId().equals(c1.getId())));
        check("rejected claims are not 'needing action'", cl.search("", ClaimService.Filter.NEEDS_ACTION).stream().noneMatch(c -> c.getStatus() == Claim.Status.REJECTED));
        check("search finds a claim by its description", cl.search("rear-end", ClaimService.Filter.ALL).size() == 2);
        check("search finds a claim by the holder's name", cl.search("juan", ClaimService.Filter.ALL).size() == 3);

        expect("a User cannot approve a claim", AccessDeniedException.class, () -> cl.approve(c1.getId(), rvMaria, "", "Passw0rd1"));
        expect("a rejected claim cannot be approved", RuleException.class, () -> cl.approve(waiting.getId(), rvAdmin, "", "Admin123"));
        expectFieldError("a note that is too long is refused", "Note to the user", () -> cl.approve(c1.getId(), rvAdmin, "x".repeat(301), "Admin123"));
        check("the refused note changed nothing", cs.claims().findById(c1.getId()).getStatus() == Claim.Status.UNDER_REVIEW);

        System.out.println("  (approving releases the money and needs the Admin password)");
        expect("a wrong password cannot approve or release money", AccessDeniedException.class, () -> cl.approve(c1.getId(), rvAdmin, "", "nope"));
        check("nothing changed after the wrong password", cs.payouts().getAll().isEmpty()
                && cs.claims().findById(c1.getId()).getStatus() == Claim.Status.UNDER_REVIEW);
        Payout pay1 = cl.approve(c1.getId(), rvAdmin, "Photos checked, repair quote looks fair.", "Admin123");
        Claim approved = cs.claims().findById(c1.getId());
        check("approving works out the payout from the policy", approved.getStatus() == Claim.Status.APPROVED && approved.getApprovedAmount() == 110_000.0);
        check("approving records who decided and the note for the user",
                approved.getHandledBy().equals(rvAdmin.getId()) && approved.getReviewerNote().startsWith("Photos checked"));
        check("C5: approving pays at once: PAY-0001 for 110,000", pay1.getId().equals("PAY-0001") && pay1.getAmount() == 110_000.0
                && approved.getPaidAmount() == 110_000.0);
        check("C5: the policy's limit left goes down when approving", cs.policies().findById("POL-0002").getRemainingLimit() == 190_000.0);
        check("the payout keeps how it was worked out", pay1.getBreakdown().contains("Payout") && pay1.getBreakdown().contains("\n"));
        expect("T17 / C6: approving twice is refused", RuleException.class, () -> cl.approve(c1.getId(), rvAdmin, "", "Admin123"));
        check("no second payout was written, and the limit did not move again", cs.payouts().getAll().size() == 1
                && cs.policies().findById("POL-0002").getRemainingLimit() == 190_000.0);
        List<String> slip = cl.payoutSlip(c1.getId());
        String slipText = String.join("\n", slip);
        check("the slip shows the slip number, payee and amount",
                slipText.contains("PAYOUT SLIP") && slipText.contains("PAY-0001") && slipText.contains("Maria Santos") && slipText.contains("AMOUNT PAID   : PHP 110,000.00"));
        check("the slip shows the working-out", slipText.contains("Less deductible") && slipText.contains("Repair estimate: PHP 120,000.00"));
        expect("a claim that is not paid has no slip", RuleException.class, () -> cl.payoutSlip(big.getId()));
        check("the User can get the slip for their own paid claim", String.join("\n", cl.payoutSlipFor(rvMaria, c1.getId())).contains("PAY-0001"));
        expect("another User cannot get it", AccessDeniedException.class, () -> cl.payoutSlipFor(rvJuan, c1.getId()));
        cl.close(c1.getId(), rvAdmin);
        check("an approved claim can be closed", cs.claims().findById(c1.getId()).getStatus() == Claim.Status.CLOSED);
        expect("closing twice is refused", RuleException.class, () -> cl.close(c1.getId(), rvAdmin));

        System.out.println("  (asking for information and denying)");
        Claim c4 = cl.file(rvMaria, "POL-0002", "2026-11-04", "Flood damage", "30000");
        expectFieldError("asking for info needs a note", "Note to the user", () -> cl.requestInfo(c4.getId(), rvAdmin, " "));
        cl.requestInfo(c4.getId(), rvAdmin, "Please send a photo of the damage.");
        check("info requested keeps the note for the user", cs.claims().findById(c4.getId()).getStatus() == Claim.Status.INFO_REQUESTED
                && cs.claims().findById(c4.getId()).getReviewerNote().contains("photo"));
        expect("a claim waiting for info cannot be approved", RuleException.class, () -> cl.approve(c4.getId(), rvAdmin, "", "Admin123"));
        check("the User sees the claim waiting for their reply first", cl.claimsOf(rvMaria).get(0).getId().equals(c4.getId()));
        check("a User's claim list holds only their own claims", cl.claimsOf(rvMaria).stream()
                .allMatch(c -> cs.policies().findById(c.getPolicyId()).getOwnerId().equals(rvMaria.getId())));
        expect("another User cannot reply to it", AccessDeniedException.class, () -> cl.respond(rvJuan, c4.getId(), "hi"));
        expect("an Admin cannot reply as the User", AccessDeniedException.class, () -> cl.respond(rvAdmin, c4.getId(), "hi"));
        expectFieldError("an empty reply is refused", "Your reply", () -> cl.respond(rvMaria, c4.getId(), "  "));
        cl.respond(rvMaria, c4.getId(), "Photo sent by email today.");
        Claim replied = cs.claims().findById(c4.getId());
        check("the reply sends the claim back to review and is kept",
                replied.getStatus() == Claim.Status.UNDER_REVIEW && replied.getApplicantNote().equals("Photo sent by email today."));
        check("the Admin's question is still there next to the reply", replied.getReviewerNote().contains("photo"));
        expect("replying again when nothing was asked is refused", RuleException.class,
                () -> cl.respond(rvMaria, c4.getId(), "again"));
        check("the dashboard counts claims waiting for a User's reply", cl.countAwaitingReply(rvMaria) == 0);
        cl.requestInfo(c4.getId(), rvAdmin, "One more photo of the engine, please.");
        check("...and sees one once the Admin asks again", cl.countAwaitingReply(rvMaria) == 1);
        cl.resumeReview(c4.getId(), rvAdmin);
        check("review can resume", cs.claims().findById(c4.getId()).getStatus() == Claim.Status.UNDER_REVIEW);
        expectFieldError("denying needs a reason code", "Denial reason", () -> cl.deny(c4.getId(), rvAdmin, null, "no"));
        cl.deny(c4.getId(), rvAdmin, Claim.Reason.EXCLUDED_CAUSE, "Flood is not covered by this policy.");
        Claim denied = cs.claims().findById(c4.getId());
        check("denying records the reason, the note and who decided",
                denied.getStatus() == Claim.Status.DENIED && denied.getReason() == Claim.Reason.EXCLUDED_CAUSE
                        && denied.getReviewerNote().startsWith("Flood") && denied.getHandledBy().equals(rvAdmin.getId()));
        cl.close(c4.getId(), rvAdmin);
        Claim small = cl.file(rvMaria, "POL-0002", "2026-11-05", "Tiny scratch", "8000");
        expect("T14: a claim below the deductible cannot be approved", RuleException.class, () -> cl.approve(small.getId(), rvAdmin, "", "Admin123"));
        check("T14: its payout works out to P0", cl.policyOf(small).calculatePayout(small.getClaimedAmount()) == 0.0);

        System.out.println("  (two claims on the same limit)");
        Claim c2 = cl.file(rvMaria, "POL-0002", "2026-11-02", "Windshield and door", "150000");
        Claim c3 = cl.file(rvMaria, "POL-0002", "2026-11-03", "Bumper and lights", "150000");
        check("the first is approved in full: 150,000 - 10,000", cl.approve(c2.getId(), rvAdmin, "", "Admin123").getAmount() == 140_000.0);
        Payout pay3 = cl.approve(c3.getId(), rvAdmin, "", "Admin123");
        Claim paid3 = cs.claims().findById(c3.getId());
        check("T15: the second is capped at the 50,000 limit left", pay3.getAmount() == 50_000.0
                && paid3.getPaidAmount() == 50_000.0 && paid3.getApprovedAmount() == 50_000.0);
        check("the policy limit is now fully used", cs.policies().findById("POL-0002").getRemainingLimit() == 0.0);
        List<Claim> onAuto = cl.claimsOnPolicy(rvMaria, "POL-0002");
        check("a User can list the claims on their own policy, newest first",
                onAuto.size() >= 6 && onAuto.stream().allMatch(c -> c.getPolicyId().equals("POL-0002"))
                        && !onAuto.get(0).getFiledDate().isBefore(onAuto.get(onAuto.size() - 1).getFiledDate()));
        expect("but not the claims on someone else's policy", AccessDeniedException.class, () -> cl.claimsOnPolicy(rvJuan, "POL-0002"));
        check("the capped claim's slip shows 50,000", String.join("\n", cl.payoutSlip(c3.getId())).contains("AMOUNT PAID   : PHP 50,000.00"));
        check("two claims still need action (the small one and the big one)", cl.countNeedingAction() == 2);
        check("the Approved filter lists the two approved claims", cl.search("", ClaimService.Filter.APPROVED).size() == 2
                && cl.search("", ClaimService.Filter.CLOSED).size() == 2);

        DataStore claimsAgain = new DataStore(claimDir);
        claimsAgain.loadAll();
        check("claims, notes and reasons survive a restart",
                claimsAgain.claims().findById(c4.getId()).getReviewerNote().startsWith("Flood")
                        && claimsAgain.claims().findById(waiting.getId()).getReason() == Claim.Reason.WAITING_PERIOD);
        check("the User's reply survives a restart",
                claimsAgain.claims().findById(c4.getId()).getApplicantNote().equals("Photo sent by email today."));
        Path oldDir = Files.createTempDirectory("ics-oldformat");
        Files.writeString(oldDir.resolve("claims.txt"),
                "CLM-0001|POL-0001|2026-11-01|2026-11-02|Old line without notes|100.0|100.0|0.0|0.0|UNDER_REVIEW||\n");
        Files.writeString(oldDir.resolve("payouts.txt"), "PAY-0001|CLM-0001|100.0|2026-11-03|USR-0001\n");
        DataStore oldStore = new DataStore(oldDir);
        oldStore.loadAll();
        check("files from an older version still load (missing columns become empty)",
                oldStore.claims().findById("CLM-0001").getReviewerNote().isEmpty()
                        && oldStore.payouts().findById("PAY-0001").getBreakdown().isEmpty());
        check("the payout breakdown (several lines) survives a restart",
                claimsAgain.payouts().findById("PAY-0003").getBreakdown().split("\n").length >= 4);

        System.out.println("Change password, deactivate and delete accounts");
        Path accDir = Files.createTempDirectory("ics-accounts");
        DataStore as = new DataStore(accDir);
        as.loadAll();
        AuthService aAuth = new AuthService(as);
        aAuth.ensureFirstAdmin();
        User boss = aAuth.login("admin", "Admin123");
        UserService aUsers = new UserService(as);
        check("the first Admin starts on the default password", aAuth.isUsingDefaultPassword(boss));
        expectFieldError("wrong current password is refused", "Current password",
                () -> aAuth.changePassword(boss, "nope", "NewPass123", "NewPass123"));
        expectFieldError("a weak new password is refused", "New password",
                () -> aAuth.changePassword(boss, "Admin123", "short", "short"));
        expectFieldError("the new password must differ from the old one", "New password",
                () -> aAuth.changePassword(boss, "Admin123", "Admin123", "Admin123"));
        expectFieldError("the confirmation must match", "Confirm new password",
                () -> aAuth.changePassword(boss, "Admin123", "NewPass123", "NewPass124"));
        check("refused changes kept the old password", as.users().findById(boss.getId())
                .verifyPassword(AuthService.hash("Admin123")));
        aAuth.changePassword(boss, "Admin123", "NewPass123", "NewPass123");
        check("the new password works", aAuth.login("admin", "NewPass123").getId().equals(boss.getId()));
        check("the old one does not", !as.users().findById(boss.getId()).verifyPassword(AuthService.hash("Admin123")));
        check("the default-password warning goes away", !aAuth.isUsingDefaultPassword(boss));
        DataStore asAgain = new DataStore(accDir);
        asAgain.loadAll();
        check("the new password survives a restart", asAgain.users().findById(boss.getId())
                .verifyPassword(AuthService.hash("NewPass123")));

        User ana = aUsers.createAccount(User.Role.USER, "ana", "Passw0rd1", "Passw0rd1", "Ana Lim", "0917 222 3333", "");
        User ben = aUsers.createAccount(User.Role.USER, "ben", "Passw0rd1", "Passw0rd1", "Ben Cruz", "0917 444 5555", "");
        User second = aUsers.createAccount(User.Role.ADMIN, "second", "Passw0rd1", "Passw0rd1", "Second Admin", "0900 111 1111", "");
        issue(new PolicyService(as, aAuth), new PolicyForm(Policy.Type.AUTO, ben.getId(), "2026-10-16", "2027-10-16",
                "10000", "300000", "", "", "800000", "BEN 1"));
        expect("a User cannot deactivate anyone", AccessDeniedException.class, () -> aUsers.deactivate(ana.getId(), ben));
        expect("you cannot deactivate yourself", RuleException.class, () -> aUsers.deactivate(boss.getId(), boss));
        aUsers.deactivate(ana.getId(), boss);
        check("a deactivated account is blocked", as.users().findById(ana.getId()).isBlocked());
        expect("a deactivated account cannot log in", LoginException.class, () -> aAuth.login("ana", "Passw0rd1"));
        expect("deactivating twice is refused", RuleException.class, () -> aUsers.deactivate(ana.getId(), boss));
        aUsers.unblock(ana.getId());
        check("unblocking brings it back", aAuth.login("ana", "Passw0rd1").getId().equals(ana.getId()));
        aUsers.deactivate(second.getId(), boss);
        expect("the last Admin who can log in cannot be deactivated", RuleException.class,
                () -> aUsers.deactivate(boss.getId(), second));
        aUsers.unblock(second.getId());

        expect("deleting needs the right Admin password", AccessDeniedException.class, () -> aUsers.delete(ana.getId(), boss, "nope"));
        check("a wrong password deleted nothing", as.users().findById(ana.getId()) != null);
        expect("an account with policies is kept", RuleException.class, () -> aUsers.delete(ben.getId(), boss, "NewPass123"));
        expect("you cannot delete yourself", RuleException.class, () -> aUsers.delete(boss.getId(), boss, "NewPass123"));
        aUsers.delete(ana.getId(), boss, "NewPass123");
        check("an account without policies is deleted", as.users().findById(ana.getId()) == null);
        check("the others are untouched", as.users().getAll().size() == 3);

        System.out.println("Fixes: stale login copy, data folder, deductible wording");
        Path staleDir = Files.createTempDirectory("ics-stale");
        DataStore st = new DataStore(staleDir);
        st.loadAll();
        AuthService sAuth = new AuthService(st);
        sAuth.ensureFirstAdmin();
        User sessionCopy = sAuth.login("admin", "Admin123");
        new UserService(st).updateProfile(sessionCopy.getId(), "Renamed Admin", "0917 000 0000", "");
        sAuth.changePassword(sessionCopy, "Admin123", "NewPass123", "NewPass123");
        boolean newAccepted;
        try { sAuth.confirmPassword(sessionCopy, "NewPass123"); newAccepted = true; } catch (Exception e) { newAccepted = false; }
        boolean oldAccepted;
        try { sAuth.confirmPassword(sessionCopy, "Admin123"); oldAccepted = true; } catch (Exception e) { oldAccepted = false; }
        check("after a profile edit and a password change, the dialog takes the NEW password", newAccepted);
        check("...and refuses the old one", !oldAccepted);

        String savedHome = System.getProperty("user.home");
        Path fakeHome = Files.createTempDirectory("ics-home");
        System.setProperty("user.home", fakeHome.toString());
        check("the data folder is <home>/ICS-data, however the program is started",
                ics.util.FileHelper.dataFolder().equals(fakeHome.resolve("ICS-data")));
        System.setProperty("ics.data", fakeHome.resolve("demo").toString());
        check("-Dics.data can point it somewhere else", ics.util.FileHelper.dataFolder().equals(fakeHome.resolve("demo")));
        System.clearProperty("ics.data");
        System.setProperty("user.home", savedHome);
        Path oldData = Path.of("data");
        boolean hadOld = Files.exists(oldData);
        if (!hadOld) {
            Files.createDirectories(oldData);
            Files.writeString(oldData.resolve("users.txt"), Files.readString(staleDir.resolve("users.txt")));
            Path target = Files.createTempDirectory("ics-target").resolve("ICS-data");
            check("old files are copied to the new folder the first time", ics.util.FileHelper.moveOldDataIfNeeded(target)
                    && Files.readString(target.resolve("users.txt")).equals(Files.readString(oldData.resolve("users.txt"))));
            check("...and never copied over existing data", !ics.util.FileHelper.moveOldDataIfNeeded(target));
            Files.delete(oldData.resolve("users.txt"));
            Files.delete(oldData);
        }

        System.out.println("Edge cases from the break-it pass");
        Path edgeDir = Files.createTempDirectory("ics-edge");
        DataStore es = new DataStore(edgeDir);
        es.loadAll();
        new AuthService(es).ensureFirstAdmin();
        UserService eu = new UserService(es);
        expectFieldError("'1e5' is letters, not an amount", "Claimed amount", () -> ics.util.Validate.parseAmount("Claimed amount", "1e5"));
        expectFieldError("'Infinity' is not an amount", "Claimed amount", () -> ics.util.Validate.parseAmount("Claimed amount", "Infinity"));
        check("'1,234.50' and '\u20B1500' still work", ics.util.Validate.parseAmount("A", "1,234.50") == 1234.5
                && ics.util.Validate.parseAmount("A", "\u20B1500") == 500);
        expectFieldError("a username with a space is refused", "Username",
                () -> eu.createAccount(User.Role.USER, "maria santos", "Passw0rd1", "Passw0rd1", "Maria", "1", ""));
        expectFieldError("a username with < > is refused", "Username",
                () -> eu.createAccount(User.Role.USER, "<html>", "Passw0rd1", "Passw0rd1", "Maria", "1", ""));
        expectFieldError("a full name starting with <html> is refused", "Full name",
                () -> eu.createAccount(User.Role.USER, "maria", "Passw0rd1", "Passw0rd1", "<html><h1>BIG", "1", ""));
        expectFieldError("a 61-character full name is refused", "Full name",
                () -> eu.createAccount(User.Role.USER, "maria", "Passw0rd1", "Passw0rd1", "A".repeat(61), "1", ""));
        check("accents and apostrophes in names are fine", eu.createAccount(User.Role.USER, "maria.s", "Passw0rd1", "Passw0rd1",
                "Mar\u00eda O'Brien-Santos", "0917 123 4567", "Brgy. Sto. Ni\u00f1o").getFullName().startsWith("Mar"));
        User oldStyle = User.create(User.Role.USER, null, "old name with spaces", AuthService.hash("x"), "Old", "1", "", 0, false);
        es.users().add(oldStyle);
        DataStore esAgain = new DataStore(edgeDir);
        esAgain.loadAll();
        check("records saved before the new rules still load", esAgain.findUserByUsername("old name with spaces") != null);
        Files.createDirectory(edgeDir.resolve("users.txt.tmp"));        // makes the next save fail
        int before = es.users().getAll().size();
        expect("a save that fails reports it", ics.exception.DataStorageException.class,
                () -> eu.createAccount(User.Role.USER, "nobody", "Passw0rd1", "Passw0rd1", "No Body", "1", ""));
        check("...and the list is put back as it was", es.users().getAll().size() == before && es.findUserByUsername("nobody") == null);

        System.out.println("Fix list, section A (policies)");
        Path aDir = Files.createTempDirectory("ics-fix-a");
        DataStore fa = new DataStore(aDir);
        fa.loadAll();
        AuthService faAuth = new AuthService(fa);
        faAuth.ensureFirstAdmin();
        User faAdmin = faAuth.login("admin", "Admin123");
        UserService faUsers = new UserService(fa);
        User aAna = faUsers.createAccount(User.Role.USER, "ana", "Passw0rd1", "Passw0rd1", "Ana Reyes", "0917 1", "");
        User aBen = faUsers.createAccount(User.Role.USER, "ben", "Passw0rd1", "Passw0rd1", "Ben Cruz", "0917 2", "");
        PolicyService fp = new PolicyService(fa, faAuth);
        Policy car = issue(fp, new PolicyForm(Policy.Type.AUTO, aAna.getId(), "2026-10-16", "2027-10-16", "10000", "300000", "", "", "800000", "5XC1JX"));
        InvalidInputException t2 = expectFieldError("T2: a different holder, same plate, overlapping dates is blocked", "Plate number",
                () -> issue(fp, new PolicyForm(Policy.Type.AUTO, aBen.getId(), "2027-01-01", "2027-12-31", "10000", "300000", "", "", "800000", "5XC1JX")));
        check("A6: the message names the plate and the policy", t2 != null
                && t2.getMessage().equals("Plate 5XC1JX is already insured under " + car.getId() + " for these dates."));
        check("T3: the same holder with a different plate is allowed",
                issue(fp, new PolicyForm(Policy.Type.AUTO, aAna.getId(), "2026-10-16", "2027-10-16", "5000", "200000", "", "", "500000", "ABC 123")) != null);
        check("T4: the same plate starting after the old policy ends is allowed",
                issue(fp, new PolicyForm(Policy.Type.AUTO, aBen.getId(), "2027-10-17", "2028-10-16", "10000", "300000", "", "", "800000", "5XC1JX")) != null);
        expectFieldError("T6: the plate typed '5xc1jx ' (lower case, a space) is still blocked", "Plate number",
                () -> issue(fp, new PolicyForm(Policy.Type.AUTO, aBen.getId(), "2027-01-01", "2027-06-30", "10000", "300000", "", "", "800000", "5xc1jx ")));
        fp.cancel(car.getId(), faAdmin, "Admin123");
        check("T5: after cancelling the old policy, the same plate and dates are allowed",
                issue(fp, new PolicyForm(Policy.Type.AUTO, aBen.getId(), "2026-10-16", "2027-10-16", "10000", "300000", "", "", "800000", "5xc1jx")) != null);
        Policy plateCheck = fa.policies().getAll().get(fa.policies().getAll().size() - 1);
        check("B4 (needed by A1): plates are saved trimmed and in capitals", ((AutoPolicy) plateCheck).getPlateNo().equals("5XC1JX"));
        Policy aHealth = issue(fp, new PolicyForm(Policy.Type.HEALTH, aAna.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", ""));
        expectFieldError("A2: the same holder cannot have two Health policies at once", "Start date",
                () -> issue(fp, new PolicyForm(Policy.Type.HEALTH, aAna.getId(), "2027-01-01", "2027-12-31", "5000", "100000", "80", "30", "", "")));
        check("A2: a different holder can have a Health policy for the same dates",
                issue(fp, new PolicyForm(Policy.Type.HEALTH, aBen.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", "")) != null);
        check("T7 / A4: updating a policy without changing anything is allowed",
                update(fp, aHealth.getId(), new PolicyForm(Policy.Type.HEALTH, aAna.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", "")) != null);
        expectFieldError("T8 / A9: an Admin cannot hold a policy", "Policy holder",
                () -> issue(fp, new PolicyForm(Policy.Type.HEALTH, faAdmin.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30", "", "")));
        InvalidInputException t11 = expectFieldError("T11 / A5: a 2-year policy is blocked", "End date",
                () -> issue(fp, new PolicyForm(Policy.Type.HEALTH, aBen.getId(), "2030-01-01", "2032-01-01", "5000", "100000", "80", "30", "", "")));
        check("A5: the message says the latest end date", t11 != null && t11.getMessage().contains("2031-01-01"));
        check("A5: exactly one year is allowed",
                issue(fp, new PolicyForm(Policy.Type.HEALTH, aBen.getId(), "2030-01-01", "2031-01-01", "5000", "100000", "80", "30", "", "")) != null);
        expectFieldError("an Auto limit higher than the vehicle value is refused", "Coverage limit",
                () -> issue(fp, new PolicyForm(Policy.Type.AUTO, aBen.getId(), "2029-01-01", "2029-12-31", "10000", "900000", "", "", "800000", "NEW 1")));
        expectFieldError("A7: a bad vehicle value is reported before any overlap", "Vehicle value",
                () -> issue(fp, new PolicyForm(Policy.Type.AUTO, aBen.getId(), "2026-10-16", "2027-10-16", "10000", "300000", "", "", "abc", "5XC1JX")));
        expectFieldError("A7: a bad reimbursement % is reported before any overlap", "Reimbursement (%)",
                () -> issue(fp, new PolicyForm(Policy.Type.HEALTH, aAna.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "150", "30", "", "")));
        Policy future = issue(fp, new PolicyForm(Policy.Type.HEALTH, aAna.getId(), "2027-10-17", "2028-10-16", "5000", "100000", "80", "0", "", ""));
        ClaimService fc = new ClaimService(fa, faAuth, java.time.Clock.fixed(java.time.Instant.parse("2026-11-10T00:00:00Z"), java.time.ZoneOffset.UTC));
        check("A8: a policy that starts later is Upcoming", future.getStatus(fc.today()) == Policy.Status.UPCOMING
                && Policy.Status.UPCOMING.getLabel().equals("Upcoming"));
        int claimsBefore = fa.claims().getAll().size();
        expectFieldError("T12 / A8: a claim on an Upcoming policy is blocked", "Policy",
                () -> fc.file(aAna, future.getId(), "2026-11-05", "Too early", "1000"));
        expectFieldError("A8: the warning before submitting says so too", "Policy",
                () -> fc.precheck(aAna, future.getId(), "2026-11-05"));
        check("A8: the blocked claim saved nothing", fa.claims().getAll().size() == claimsBefore);

        System.out.println("Fix list, section B (policies)");
        Path bDir = Files.createTempDirectory("ics-fix-b");
        DataStore fb = new DataStore(bDir);
        fb.loadAll();
        AuthService fbAuth = new AuthService(fb);
        fbAuth.ensureFirstAdmin();
        User fbAdmin = fbAuth.login("admin", "Admin123");
        User bHolder = new UserService(fb).createAccount(User.Role.USER, "carla", "Passw0rd1", "Passw0rd1", "Carla Diaz", "0917 3", "");
        PolicyService bp = new PolicyService(fb, fbAuth);
        expectFieldError("T1: deductible 'abc' gives a message, no crash", "Deductible",
                () -> issue(bp, new PolicyForm(Policy.Type.HEALTH, bHolder.getId(), "2026-10-16", "2027-10-16", "abc", "100000", "80", "30", "", "")));
        InvalidInputException neg = expectFieldError("T1: deductible '-500' gives a message, no crash", "Deductible",
                () -> issue(bp, new PolicyForm(Policy.Type.HEALTH, bHolder.getId(), "2026-10-16", "2027-10-16", "-500", "100000", "80", "30", "", "")));
        check("T1: the message says it cannot be negative", neg != null && neg.getMessage().equals("Deductible cannot be negative."));
        expectFieldError("B1: a blank coverage limit gives a message", "Coverage limit",
                () -> issue(bp, new PolicyForm(Policy.Type.HEALTH, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", " ", "80", "30", "", "")));
        expectFieldError("B1: a negative vehicle value gives a message", "Vehicle value",
                () -> issue(bp, new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "", "", "-800000", "B1 TEST")));
        InvalidInputException pct = expectFieldError("B3: reimbursement 0 is refused", "Reimbursement (%)",
                () -> issue(bp, new PolicyForm(Policy.Type.HEALTH, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "0", "30", "", "")));
        check("B3: the message reads 'between 1 and 100'", pct != null && pct.getMessage().equals("Reimbursement (%) must be between 1 and 100."));
        expectFieldError("B3: a waiting period of -5 days is refused", "Waiting period (days)",
                () -> issue(bp, new PolicyForm(Policy.Type.HEALTH, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "-5", "", "")));
        expectFieldError("B3: a waiting period of 30.5 days is refused", "Waiting period (days)",
                () -> issue(bp, new PolicyForm(Policy.Type.HEALTH, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "30.5", "", "")));
        check("B3: a waiting period of 0 is fine",
                issue(bp, new PolicyForm(Policy.Type.HEALTH, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "80", "0", "", "")) != null);
        Policy old = issue(bp, new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2024-01-01", "2024-12-31", "5000", "100000", "", "", "500000", "OLD 1"));
        check("B5: a policy whose end date has passed is Expired by itself", old.getStatus(LocalDate.now()) == Policy.Status.EXPIRED);
        RuleException expiredEdit = expect("B6: an expired policy cannot be updated", RuleException.class,
                () -> update(bp, old.getId(), new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2024-01-01", "2024-12-31", "5000", "90000", "", "", "500000", "OLD 1")));
        check("B6: the message says why", expiredEdit != null && expiredEdit.getMessage().equals("An expired policy cannot be edited."));
        Policy p1b = issue(bp, new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "", "", "500000", "T9 A"));
        Policy p2b = issue(bp, new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "", "", "500000", "T9 B"));
        Policy p3b = issue(bp, new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "", "", "500000", "T9 C"));
        bp.delete(p1b.getId(), fbAdmin, "Admin123");
        Policy p4b = issue(bp, new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "100000", "", "", "500000", "T9 D"));
        check("T9 / B8: after deleting the first of three, the next one gets a new number, not a duplicate",
                !p4b.getId().equals(p2b.getId()) && !p4b.getId().equals(p3b.getId())
                        && Integer.parseInt(p4b.getId().substring(4)) == Integer.parseInt(p3b.getId().substring(4)) + 1);
        bp.cancel(p2b.getId(), fbAdmin, "Admin123");
        expect("B6: a cancelled policy cannot be updated", RuleException.class,
                () -> update(bp, p2b.getId(), new PolicyForm(Policy.Type.AUTO, bHolder.getId(), "2026-10-16", "2027-10-16", "5000", "90000", "", "", "500000", "T9 B")));
        ClaimService bc = new ClaimService(fb, fbAuth, java.time.Clock.fixed(java.time.Instant.parse("2026-11-10T00:00:00Z"), java.time.ZoneOffset.UTC));
        bc.file(bHolder, p3b.getId(), "2026-11-05", "Scratch", "20000");
        RuleException withClaims = expect("T10 / B7: a policy that has claims cannot be deleted", RuleException.class,
                () -> bp.delete(p3b.getId(), fbAdmin, "Admin123"));
        check("B7: the message is the fix list's wording", withClaims != null
                && withClaims.getMessage().equals("This policy has claims. Cancel it instead."));
        check("B7: the policy is still there", fb.policies().findById(p3b.getId()) != null);

        System.out.println("Fix list, sections D, E and F");
        Path dDir = Files.createTempDirectory("ics-fix-d");
        DataStore fd = new DataStore(dDir);
        fd.loadAll();
        check("D1: all four files are created at startup", Files.exists(dDir.resolve("users.txt")) && Files.exists(dDir.resolve("policies.txt"))
                && Files.exists(dDir.resolve("claims.txt")) && Files.exists(dDir.resolve("payouts.txt")));
        AuthService fdAuth = new AuthService(fd);
        fdAuth.ensureFirstAdmin();
        User dHolder = new UserService(fd).createAccount(User.Role.USER, "dina", "Passw0rd1", "Passw0rd1", "Dina, the tester", "0917 4", "Blk 1, Lot 2 | Sto. Tomas");
        PolicyService dp = new PolicyService(fd, fdAuth);
        Policy luxCar = issue(dp, new PolicyForm(Policy.Type.AUTO, dHolder.getId(), "2026-10-16", "2027-10-16", "10000", "12000000", "", "", "15000000", "LUX 1"));
        String policyLine = Files.readString(dDir.resolve("policies.txt"));
        check("D3: numbers are saved plainly, even 12 million (no 1.2E7, no peso sign, no commas)",
                policyLine.contains("|12000000.0|") && policyLine.contains("|15000000.0|") && !policyLine.contains("E7") && !policyLine.contains("\u20B1"));
        ClaimService dc = new ClaimService(fd, fdAuth, Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC));
        Claim commaClaim = dc.file(dHolder, luxCar.getId(), "2026-11-05", "Front bumper, left door, and mirror | all scratched", "50000");
        DataStore fdAgain = new DataStore(dDir);
        fdAgain.loadAll();
        check("T18 / D2: a description with commas and | is saved and reloaded exactly",
                fdAgain.claims().findById(commaClaim.getId()).getDescription().equals("Front bumper, left door, and mirror | all scratched"));
        check("D2: commas and | in a name and an address survive too",
                fdAgain.users().findById(dHolder.getId()).getFullName().equals("Dina, the tester")
                        && fdAgain.users().findById(dHolder.getId()).getAddress().equals("Blk 1, Lot 2 | Sto. Tomas"));
        check("D4: a clean load has no warnings", fdAgain.loadWarnings().isEmpty());
        Files.writeString(dDir.resolve("claims.txt"), Files.readString(dDir.resolve("claims.txt")) + "CLM-0002|broken line\n"
                + Files.readString(dDir.resolve("claims.txt")));
        DataStore fdBroken = new DataStore(dDir);
        fdBroken.loadAll();
        check("D4: the good claims still load, the broken and duplicate lines are skipped",
                fdBroken.claims().getAll().size() == 1 && fdBroken.loadWarnings().size() == 2
                        && fdBroken.loadWarnings().get(1).contains("appears twice"));

        Policy cheap = issue(dp, new PolicyForm(Policy.Type.HEALTH, dHolder.getId(), "2026-10-16", "2027-10-16", "9999", "100000", "80", "30", "", ""));
        Policy discounted = new HealthPolicy(null, dHolder.getId(), LocalDate.of(2026, 10, 16), LocalDate.of(2027, 10, 16), 10_000, 100_000, 0, false, 0.8, 30, 0);
        check("E2: a 9,999 deductible pays the full 4% (4,000)", cheap.calculatePremium() == 4_000.0 && !cheap.hasDeductibleDiscount());
        check("E2: a 10,000 deductible gets 10% off (3,600)", discounted.calculatePremium() == 3_600.0 && discounted.hasDeductibleDiscount());
        check("E2: the live preview shows the discount", dp.previewPremium(Policy.Type.AUTO, "", "800000", "10,000") == 21_600.0
                && dp.previewPremium(Policy.Type.AUTO, "", "800000", "abc") == 24_000.0);
        ClaimService late = new ClaimService(fd, fdAuth, Clock.fixed(Instant.parse("2027-02-14T00:00:00Z"), ZoneOffset.UTC));
        check("E3: 90 days after the incident is still on time", late.precheck(dHolder, luxCar.getId(), "2026-11-16") == null);
        check("E3: 91 days after the incident is late", late.precheck(dHolder, luxCar.getId(), "2026-11-15") == Claim.Reason.LATE_FILING);

        System.out.println("Fix list, section C (claims)");
        Policy cHealth = new HealthPolicy(null, "x", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 5_000, 100_000, 0, false, 0.8, 0, 0);
        check("C7: the one-line breakdown matches the fix list's example",
                cHealth.payoutFormula(25_000, "P").equals("P25,000.00 - P5,000.00 = P20,000.00 x 80% = P16,000.00"));
        Policy cAuto = new AutoPolicy(null, "x", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 10_000, 100_000, 95_000, false, 800_000, "C7 1");
        check("C7: when the limit left is smaller, the breakdown says so",
                cAuto.payoutFormula(50_000, "P").equals("P50,000.00 - P10,000.00 = P40,000.00, capped at the P5,000.00 limit left")
                        && cAuto.calculatePayout(50_000) == 5_000.0);
        check("T14: below the deductible the breakdown shows P0", cAuto.payoutFormula(8_000, "P").startsWith("P8,000.00 - P8,000.00 = P0.00"));
        Path cDir = Files.createTempDirectory("ics-fix-c");
        DataStore fc2 = new DataStore(cDir);
        fc2.loadAll();
        AuthService fcAuth = new AuthService(fc2);
        fcAuth.ensureFirstAdmin();
        User cUser = new UserService(fc2).createAccount(User.Role.USER, "eli", "Passw0rd1", "Passw0rd1", "Eli Ramos", "0917 5", "");
        PolicyService cPol = new PolicyService(fc2, fcAuth);
        Policy cOld = issue(cPol, new PolicyForm(Policy.Type.AUTO, cUser.getId(), "2026-01-01", "2026-10-31", "1000", "100000", "", "", "500000", "EXP 1"));
        ClaimService cs2 = new ClaimService(fc2, fcAuth, Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC));
        check("C1 (your choice): a policy that just expired still takes a claim for an accident while it was active",
                cOld.getStatus(cs2.today()) == Policy.Status.EXPIRED && cs2.file(cUser, cOld.getId(), "2026-10-30", "Hit a post", "20000").getStatus() == Claim.Status.UNDER_REVIEW);
        check("T16: but not for an accident after it ended", cs2.file(cUser, cOld.getId(), "2026-11-05", "After the end", "20000").getReason() == Claim.Reason.OUTSIDE_COVERAGE_PERIOD);
        cPol.cancel(cOld.getId(), fc2.users().getAll().get(0), "Admin123");
        check("T16: a cancelled policy is blocked", cs2.precheck(cUser, cOld.getId(), "2026-10-29") == Claim.Reason.POLICY_INACTIVE);
        expectFieldError("C3: an amount of 0 is refused", "Claimed amount", () -> cs2.file(cUser, cOld.getId(), "2026-10-29", "Zero", "0"));
        expectFieldError("C2: an incident in the future is refused", "Incident date", () -> cs2.file(cUser, cOld.getId(), "2026-12-01", "Future", "100"));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
