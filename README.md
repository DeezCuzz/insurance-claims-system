# Insurance Claims System (ICS)

A stand-alone **Java Swing** desktop application for managing insurance **policies** and **claims**
inheritance, polymorphism, abstraction) and stores all data in **ArrayLists and txt files — no database** (rubric V.1).

> Group: 1 · Members: Tejada, Ceejay H. Angeles, Danrae R. Tayao, Renz Gabriel S. · Section: ________

## Contents
1. [What the system does](#1-what-the-system-does)
2. [How to run it](#2-how-to-run-it)
3. [Architecture](#3-architecture)
4. [UML class diagrams](#4-uml-class-diagrams)
5. [Business rules and computations](#5-business-rules-and-computations)
6. [Data storage (txt files)](#6-data-storage-txt-files)
7. [Testing](#7-testing)
8. [Known limitations and future improvements](#8-known-limitations-and-future-improvements)

## 1. What the system does

There are two kinds of account. Only the **Admin** creates accounts (there is no public sign-up).

| Role | Can do |
|---|---|
| **Admin** | Create, edit, deactivate, unblock and delete accounts · issue, edit, cancel and delete Health and Auto policies · review claims: approve (releases the payout and prints a slip), deny with a reason, or ask the User for more information |
| **User** (policyholder) | See their own policies · apply for a claim (with a live payout estimate and automatic checks) · follow their claims, reply to the Admin, and print their payout slip |
| **Everyone** | Log in (3 wrong passwords block the account and close the program; only an Admin can unblock) · change their own password |

## 2. How to run it

Requirements: **JDK 17 or newer** (NetBeans' bundled JDK is fine).

```
cd src
javac -d ../out $(find . -name "*.java")
java -cp ../out ics.Main
```
In NetBeans: open the project and run `ics.Main` (F6). On the **first run** the program creates the Admin account
**`admin` / `Admin123`**; change it immediately under **Change Password**.

Data is saved in **`<home folder>/ICS-data`** (for example `C:\Users\<name>\ICS-data`).

## 3. Architecture

Three layers, each talking only to the one below it, plus the model shared by all:

| Layer | Package | Job |
|---|---|---|
| Screens | `ics.gui` | Show data and collect input. Never decide anything. |
| Rules | `ics.service` | Every business rule: login, accounts, policies, claims, payouts. |
| Storage | `ics.repository` | Keep records in ArrayLists while running and in txt files on disk. |
| Data | `ics.model` | The records themselves: User, Policy, Claim, Payout. |
| Errors | `ics.exception` | One exception class per kind of problem. |
| Helpers | `ics.util` | Input checks (`Validate`) and the file format (`FileHelper`). |

**OOP in the code**

| Principle | Where |
|---|---|
| Inheritance | `Admin` and `Applicant` extend `User`; `HealthPolicy` and `AutoPolicy` extend `Policy`; every record extends `Entity`; every screen extends `BasePanel` |
| Polymorphism | `calculatePremium()` and `calculatePayout()` differ in `HealthPolicy` and `AutoPolicy`; `canManageUsers()` differs in `Admin` and `Applicant` (the sidebar is built from it) |
| Encapsulation | All fields are `private`; a claim's status changes only through `changeStatus()`, which allows only legal moves (`Claim.Status.canMoveTo()`) |
| Abstraction | `Entity`, `User` and `Policy` are abstract; screens call `policy.calculatePayout()` without knowing the formula |

## 4. UML class diagrams

The diagrams below are generated from the compiled code, so every class, field and method matches the source.
GitHub draws them automatically; image copies are in [`docs/uml`](docs/uml).

**Notation:** `+` public · `-` private · `#` protected · `~` package · *italic* = abstract · underlined = static ·
hollow triangle = inheritance (extends) · filled diamond = composition · hollow diamond = aggregation ·
solid arrow = association (with multiplicity) · dotted arrow = dependency (uses).
Enumerations shown on their own are nested in the class that uses them: `Role` is `User.Role`, `PolicyType` and
`PolicyStatus` are `Policy.Type` and `Policy.Status`, `ClaimStatus` and `ClaimReason` are `Claim.Status` and
`Claim.Reason`, `ClaimFilter` is `ClaimService.Filter`, `LineReader` is `Repository.LineReader`.
Private helper methods are left out to keep the diagrams readable.

### 4.1 Model: the records and how they relate
A User owns policies (`ownerId`), a policy has claims (`policyId`), and an approved claim has one payout (`claimId`).
Records refer to each other by **ID** (like foreign keys), because they are stored in separate txt files.

```mermaid
classDiagram
    direction TB
    class Entity {
        <<abstract>>
        -String id
        +getId() String
        +setId(String) void
        +toFileLine()* String
    }
    class User {
        <<abstract>>
        +int MAX_FAILED_ATTEMPTS$
        -String username
        -String passwordHash
        -String fullName
        -String contact
        -String address
        -int failedAttempts
        -boolean blocked
        +block() void
        +canApplyForClaims()* boolean
        +canManageUsers()* boolean
        +canReviewClaims()* boolean
        +create(Role, String, String, String, String, String, String, int, boolean)$ User
        +fromFields(String[])$ User
        +getAddress() String
        +getContact() String
        +getFailedAttempts() int
        +getFullName() String
        +getPasswordHash() String
        +getRole()* Role
        +getUsername() String
        +isBlocked() boolean
        +recordFailedAttempt() void
        +resetFailedAttempts() void
        +setAddress(String) void
        +setContact(String) void
        +setFullName(String) void
        +setPasswordHash(String) void
        +setUsername(String) void
        +toFileLine() String
        +unblock() void
        +verifyPassword(String) boolean
    }
    class Admin {
        +canApplyForClaims() boolean
        +canManageUsers() boolean
        +canReviewClaims() boolean
        +getRole() Role
    }
    class Applicant {
        +canApplyForClaims() boolean
        +canManageUsers() boolean
        +canReviewClaims() boolean
        +getRole() Role
    }
    class Policy {
        <<abstract>>
        +double DISCOUNT_DEDUCTIBLE$
        +double DISCOUNT_RATE$
        -String ownerId
        -LocalDate startDate
        -LocalDate endDate
        -double deductible
        -double coverageLimit
        -double limitUsed
        -boolean cancelled
        #amount(String, double)$ String
        +applyPayout(double, double) void
        +calculatePayout(double)* double
        +calculatePremium()* double
        +cancel() void
        +explainPayout(double)* List~String~
        #extraFields()* Object[]
        +fromFields(String[])$ Policy
        +getCoverageLimit() double
        +getDeductible() double
        +getEndDate() LocalDate
        +getLimitUsed() double
        +getOwnerId() String
        +getRemainingLimit() double
        +getStartDate() LocalDate
        +getStatus(LocalDate) PolicyStatus
        +getType()* PolicyType
        +hasDeductibleDiscount() boolean
        +isCancelled() boolean
        +payoutFormula(double, String)* String
        #php(double)$ String
        #roundToCents(double)$ double
        +toFileLine() String
        #withDeductibleDiscount(double) double
    }
    class HealthPolicy {
        +double PREMIUM_RATE$
        -double reimbursementRate
        -int waitingPeriodDays
        -double deductibleMet
        +applyPayout(double, double) void
        +calculatePayout(double) double
        +calculatePremium() double
        +explainPayout(double) List~String~
        #extraFields() Object[]
        +getDeductibleMet() double
        +getReimbursementRate() double
        +getRemainingDeductible() double
        +getType() PolicyType
        +getWaitingPeriodDays() int
        +getWaitingPeriodEnd() LocalDate
        +isInWaitingPeriod(LocalDate) boolean
        +payoutFormula(double, String) String
    }
    class AutoPolicy {
        +double PREMIUM_RATE$
        -double vehicleValue
        -String plateNo
        +calculatePayout(double) double
        +calculatePremium() double
        +explainPayout(double) List~String~
        #extraFields() Object[]
        +getPlateNo() String
        +getType() PolicyType
        +getVehicleValue() double
        +payoutFormula(double, String) String
    }
    class Claim {
        +int MAX_NOTE_LENGTH$
        -String policyId
        -LocalDate incidentDate
        -LocalDate filedDate
        -String description
        -double claimedAmount
        -double reserveAmount
        -double approvedAmount
        -double paidAmount
        -ClaimStatus status
        -ClaimReason reason
        -String handledBy
        -String reviewerNote
        -String applicantNote
        +approve(double, String) void
        +changeStatus(ClaimStatus) void
        +deny(ClaimReason, String) void
        +fromFields(String[])$ Claim
        +getApplicantNote() String
        +getApprovedAmount() double
        +getClaimedAmount() double
        +getDescription() String
        +getFiledDate() LocalDate
        +getHandledBy() String
        +getIncidentDate() LocalDate
        +getPaidAmount() double
        +getPolicyId() String
        +getReason() ClaimReason
        +getReserveAmount() double
        +getReviewerNote() String
        +getStatus() ClaimStatus
        +reject(ClaimReason) void
        +setApplicantNote(String) void
        +setHandledBy(String) void
        +setReserveAmount(double) void
        +setReviewerNote(String) void
        +toFileLine() String
    }
    class Payout {
        -String claimId
        -double amount
        -LocalDate paidDate
        -String paidBy
        -String breakdown
        +fromFields(String[])$ Payout
        +getAmount() double
        +getBreakdown() String
        +getClaimId() String
        +getPaidBy() String
        +getPaidDate() LocalDate
        +toFileLine() String
    }
    class Role {
        <<enumeration>>
        ADMIN
        USER
    }
    class PolicyType {
        <<enumeration>>
        HEALTH
        AUTO
    }
    class PolicyStatus {
        <<enumeration>>
        UPCOMING
        ACTIVE
        EXPIRED
        CANCELLED
    }
    class ClaimStatus {
        <<enumeration>>
        FILED
        REJECTED
        UNDER_REVIEW
        INFO_REQUESTED
        APPROVED
        DENIED
        PAID
        CLOSED
    }
    class ClaimReason {
        <<enumeration>>
        POLICY_INACTIVE
        OUTSIDE_COVERAGE_PERIOD
        WAITING_PERIOD
        LATE_FILING
        DUPLICATE_CLAIM
        LIMIT_EXHAUSTED
        EXCLUDED_CAUSE
        SUSPECTED_FRAUD
        INSUFFICIENT_DOCUMENTS
    }
    Entity <|-- User
    Entity <|-- Policy
    Entity <|-- Claim
    Entity <|-- Payout
    User <|-- Admin
    User <|-- Applicant
    Policy <|-- HealthPolicy
    Policy <|-- AutoPolicy
    User "1" <-- "0..*" Policy : ownerId (owns)
    Policy "1" <-- "0..*" Claim : policyId (claimed on)
    Claim "1" <-- "0..1" Payout : claimId (pays)
    User "1" <-- "0..*" Claim : handledBy (Admin who decided)
    User "1" <-- "0..*" Payout : paidBy (Admin who released)
    User ..> Role
    Policy ..> PolicyType
    Policy ..> PolicyStatus
    Claim --> ClaimStatus : status
    Claim --> ClaimReason : reason
```

### 4.2 Storage: one generic repository for every record
`DataStore` is made of four `Repository<T>` objects, one per txt file. `T extends Entity`, so the same code stores
users, policies, claims and payouts; each record type supplies a `LineReader` to rebuild itself from a line.

```mermaid
classDiagram
    direction TB
    class Repository~T~ {
        -ArrayList~T~ items
        -Path file
        -String prefix
        -LineReader~T~ reader
        -List~String~ warnings
        +add(T) void
        +delete(String) void
        +findById(String) T
        +getAll() List~T~
        +getWarnings() List~String~
        +load() void
        +nextId() String
        +save() void
        +update(T) void
    }
    class LineReader~T~ {
        <<interface>>
        +read(String[])* T
    }
    class DataStore {
        -Repository~User~ users
        -Repository~Policy~ policies
        -Repository~Claim~ claims
        -Repository~Payout~ payouts
        +claims() Repository~Claim~
        +claimsOn(String) List~Claim~
        +findUserByUsername(String) User
        +loadAll() void
        +loadWarnings() List~String~
        +payoutFor(String) Payout
        +payouts() Repository~Payout~
        +policies() Repository~Policy~
        +policiesOf(String) List~Policy~
        +users() Repository~User~
    }
    class FileHelper {
        +String FOLDER_NAME$
        -String[] FILE_NAMES$
        +dataFolder()$ Path
        +join(Object[])$ String
        +moveOldDataIfNeeded(Path)$ boolean
        +split(String)$ String[]
    }
    class Validate {
        +between(String, double, double, double)$ double
        +nonNegative(String, double)$ double
        +notBlank(String, String)$ String
        +notNull(String, T)$ T
        +parseAmount(String, String)$ double
        +parseDate(String, String)$ LocalDate
        +parseWholeNumber(String, String)$ int
        +plainText(String, String, int)$ String
        +positive(String, double)$ double
        +strongPassword(String, String)$ String
        +username(String, String)$ String
    }
    class Entity {
        <<abstract>>
    }
    DataStore *-- "4" Repository : users, policies, claims, payouts
    Repository o-- "0..*" Entity : items (T extends Entity)
    Repository --> LineReader : reader
    Repository ..> FileHelper : join / split lines
```

### 4.3 Services: the business rules

```mermaid
classDiagram
    direction TB
    class AuthService {
        +String DEFAULT_ADMIN_USERNAME$
        +String DEFAULT_ADMIN_PASSWORD$
        -DataStore store
        +changePassword(User, String, String, String) void
        +confirmPassword(User, String) void
        +ensureFirstAdmin() boolean
        +hash(String)$ String
        +isUsingDefaultPassword(User) boolean
        +login(String, String) User
    }
    class UserService {
        -DataStore store
        -AuthService auth
        +countUsers() int
        +createAccount(Role, String, String, String, String, String, String) User
        +deactivate(String, User) void
        +delete(String, User, String) void
        +search(String) List~User~
        +unblock(String) void
        +updateProfile(String, String, String, String) void
    }
    class PolicyService {
        -DataStore store
        -AuthService auth
        +cancel(String, User, String) void
        +countPolicies() int
        +delete(String, User, String) void
        +holderName(String) String
        +issue(PolicyType, String, String, String, String, String, String, String, String, String) Policy
        +policyHolders() List~User~
        +previewPremium(PolicyType, String, String, String) double
        +search(String) List~Policy~
        +update(String, String, String, String, String, String, String, String, String) Policy
    }
    class ClaimService {
        +int FILING_WINDOW_DAYS$
        -int MAX_DESCRIPTION_LENGTH$
        -DataStore store
        -AuthService auth
        -Clock clock
        +approve(String, User, String, String) Payout
        +claimsOf(User) List~Claim~
        +claimsOnPolicy(User, String) List~Claim~
        +close(String, User) void
        +countAwaitingReply(User) int
        +countClaims() int
        +countNeedingAction() int
        +countPayouts() int
        +deny(String, User, ClaimReason, String) void
        +estimate(User, String, String) double
        +file(User, String, String, String, String) Claim
        +getClaim(String) Claim
        +holderName(Claim) String
        +holderOf(Claim) User
        +payoutOf(Claim) Payout
        +payoutSlip(String) List~String~
        +payoutSlipFor(User, String) List~String~
        +policiesOf(User) List~Policy~
        +policyOf(Claim) Policy
        +precheck(User, String, String) ClaimReason
        +requestInfo(String, User, String) void
        +respond(User, String, String) void
        +resumeReview(String, User) void
        +search(String, ClaimFilter) List~Claim~
        +today() LocalDate
        +userName(String) String
    }
    class ClaimFilter {
        <<enumeration>>
        Needs action
        All claims
        Under review
        Info requested
        Approved
        Paid (older claims)
        Denied
        Rejected
        Closed
    }
    class DataStore
    class Validate
    UserService --> AuthService : auth
    PolicyService --> AuthService : auth
    ClaimService --> AuthService : auth
    AuthService --> DataStore : store
    UserService --> DataStore : store
    PolicyService --> DataStore : store
    ClaimService --> DataStore : store
    ClaimService ..> ClaimFilter
    UserService ..> Validate
    PolicyService ..> Validate
    ClaimService ..> Validate
```

### 4.4 Exceptions: one parent, five kinds of problem
All are checked exceptions (`extends Exception`), so Java forces every caller to handle them.
`InvalidInputException` remembers the field name, which lets the screen put the cursor in the wrong field.

```mermaid
classDiagram
    direction TB
    class Exception {
        <<Java built-in>>
    }
    class ICSException {
    }
    class InvalidInputException {
        -String fieldName
        +getFieldName() String
    }
    class RuleException {
    }
    class AccessDeniedException {
    }
    class LoginException {
        -int triesLeft
        -boolean blocked
        -boolean justBlocked
        +blocked(boolean)$ LoginException
        +getTriesLeft() int
        +isBlocked() boolean
        +isJustBlocked() boolean
        +wrongPassword(int)$ LoginException
    }
    class DataStorageException {
    }
    Exception <|-- ICSException
    ICSException <|-- InvalidInputException
    ICSException <|-- RuleException
    ICSException <|-- AccessDeniedException
    ICSException <|-- LoginException
    ICSException <|-- DataStorageException
```

### 4.5 GUI: the window and the screens
`ShellPanel` builds the sidebar and screens from what the logged-in account can do (polymorphism), and shows one
screen at a time with a `CardLayout`.

```mermaid
classDiagram
    direction TB
    class JFrame {
        <<Swing>>
    }
    class JPanel {
        <<Swing>>
    }
    class Main {
        -DataStore store
        -AuthService auth
        +main(String[])$ void
        +showLogin() void
        +showShell(User) void
    }
    class BasePanel {
        <<abstract>>
        -int MAX_PASSWORD_TRIES$
        -JPanel body
        #JLabel statusLabel
        -String protectedMessage
        #addField(JPanel, int, JLabel, JComponent)$ void
        #askAdminPassword(String, String) String
        #confirm(String) boolean
        #getBody() JPanel
        #getProtectedMessage() String
        #onKey(String, Runnable) void
        +onShow() void
        #runProtected(String, ProtectedAction) Outcome
        #setStatus(String, boolean) void
    }
    class ShellPanel {
        -CardLayout cards
        -JPanel content
        -Map~String, BasePanel~ pages
        -List~Widgets.NavButton~ navButtons
        +show(String) void
    }
    class LoginPanel
    class Theme {
        <<utility>>
    }
    class Widgets {
        <<utility>>
    }
    class Dialogs {
        <<utility>>
    }
    class DashboardPanel
    class UserPanel
    class PolicyPanel
    class ReviewPanel
    class ApplyPanel
    class MyPoliciesPanel
    class MyClaimsPanel
    class ChangePasswordPanel
    JFrame <|-- Main
    JPanel <|-- BasePanel
    JPanel <|-- ShellPanel
    JPanel <|-- LoginPanel
    BasePanel <|-- DashboardPanel
    BasePanel <|-- UserPanel
    BasePanel <|-- PolicyPanel
    BasePanel <|-- ReviewPanel
    BasePanel <|-- ApplyPanel
    BasePanel <|-- MyPoliciesPanel
    BasePanel <|-- MyClaimsPanel
    BasePanel <|-- ChangePasswordPanel
    Main --> LoginPanel : showLogin()
    Main --> ShellPanel : showShell()
    ShellPanel *-- "1..*" BasePanel : pages (by role)
    BasePanel ..> Dialogs : password box
    BasePanel ..> Theme : colours and fonts
    ShellPanel ..> Widgets : sidebar buttons
```

## 5. Business rules and computations

**Premium**
- Health: **4% of the coverage limit**. Auto: **3% of the vehicle value**.
- **10% off** either one when the deductible is ₱10,000 or more.
- *Example:* car worth ₱800,000 with a ₱10,000 deductible: 800,000 × 0.03 = 24,000 − 10% = **₱21,600**.

**Payout** (never more than the coverage limit left)
- Auto: `min(repair cost − deductible, limit left)`. The deductible applies to **every** claim.
  *Example:* 120,000 − 10,000 = **₱110,000**.
- Health: `min((bill − deductible still unpaid) × reimbursement %, limit left)`. The deductible is paid **once** per policy.
  *Example:* (50,000 − 5,000) × 80% = **₱36,000**.

**Policy rules**
- Holder must be a User account.
- At most 1 year long.
- Deductible lower than the limit.
- Auto: limit not above the vehicle value.
- No double insurance: Auto checked by **plate number**, Health by **holder**; cancelled policies are ignored.
- A policy with claims cannot be deleted (cancel it instead).
- Status is computed from the dates: Upcoming, Active, Expired, Cancelled.

**Automatic claim checks** (a failed check saves the claim as **Rejected** with its reason):
1. The policy is not cancelled.
2. The incident is inside the coverage dates.
3. It is past the Health waiting period.
4. It is filed within 90 days.
5. The same incident was not already claimed.
6. The coverage limit is not used up.

No claims are accepted on an Upcoming policy.

**Claim life cycle**
```
FILED ─▶ UNDER_REVIEW ─▶ APPROVED (payout released, slip printed) ─▶ CLOSED
   │          │  └──────▶ DENIED (reason required) ─────────────────▶ CLOSED
   │          └─▶ INFO_REQUESTED ─▶ (User replies) ─▶ UNDER_REVIEW
   └─▶ REJECTED (automatic check failed)
```

**Security**
- Passwords stored as SHA-256 hashes.
- 3 wrong passwords block the account (the count is saved immediately) and close the program.
- The Admin re-types the password before deleting, cancelling or approving money.

## 6. Data storage (txt files)

Four files in `ICS-data`, one record per line, fields separated by `|` (a typed `|` is saved as `\p`):

```
users.txt     id | role | username | passwordHash | fullName | contact | address | failedAttempts | blocked
policies.txt  id | type | ownerId | start | end | deductible | coverageLimit | limitUsed | cancelled | extras...
              HEALTH extras: reimbursementRate | waitingPeriodDays | deductibleMet
              AUTO extras:   vehicleValue | plateNo
claims.txt    id | policyId | incidentDate | filedDate | description | claimed | reserve | approved | paid
              | status | reason | handledBy | reviewerNote | applicantNote
payouts.txt   id | claimId | amount | paidDate | paidBy | breakdown
```
- **IDs** are auto-numbered (`USR-0001`, `POL-0001`, `CLM-0001`, `PAY-0001`): highest number + 1.
- **Saving** writes a temporary file first, then swaps it in.
- **Damaged lines** are skipped with a warning, and a `.bak` copy is kept.


## 8. Known limitations and future improvements
- Passwords are SHA-256 without a salt (a real system would add a salt or use bcrypt).
- Money is a `double` rounded to centavos (banks use `BigDecimal`).
- Approving writes three files one after another, not as one transaction.
- Only the latest note and reply are kept per claim.
- Future work: message history, premium receipts, a separate Staff role.
