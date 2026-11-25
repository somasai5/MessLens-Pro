# MessLens: research basis

MessLens is a photo-evidence workflow for hostel messes (kitchen staff photograph each cooking stage, a mess head approves, a warden
reviews the decision, students see what was verified). This document says which published work each security feature comes from,
where it is in the code, and how it was tested. Measured numbers are in [EVALUATION.md](EVALUATION.md).

| # | Source | What the project takes from it | Code | Evidence it works |
|---|---|---|---|---|
| 1 | Kuhn, Coyne, Weil, *Adding Attributes to Role-Based Access Control*, IEEE Computer 2010 | Five clean roles; attributes (hostel, ownership, photo state, time) narrow what each role may do | `security/AccessDecisionService`, `Roles`, `Resource` | E1 role count, E2 820,800-case differential test, `PolicyDifferentialTest` |
| 2 | NIST SP 800-162, *Guide to Attribute Based Access Control* | Request -> Policy Enforcement Point -> Policy Decision Point -> permit/deny, using subject, resource and environment attributes | `AccessDecisionService.check / require`, every method of `UploadService`, `ApprovalService`, `AuthService`, `IntegrityService` | E3 latency, `SecurityFlowTest` |
| 3 | Clark and Wilson, *A Comparison of Commercial and Military Computer Security Policies*, 1987 | Data changes only through well-formed transactions; separation of duties; every change logged; integrity verification procedures | `ApprovalService` (worker -> head -> warden), rule `D-SOD`, transactional audit in the DAOs, `IntegrityService` | `SecurityFlowTest` (review chain, separation of duties, photo re-hash) |
| 4 | NIST SP 800-63B, *Digital Identity Guidelines: Authentication and Lifecycle Management* (+ PBKDF2, RFC 8018) | Slow salted password hashing, length-based rules with a blocklist, rate limiting, session timeouts | `util/SecurityUtil`, `security/PasswordPolicy`, `LoginThrottle`, `SessionManager`, `AppNavigator.watchSession` | E5, E6, `SecurityFlowTest` |
| 5 | Schneier and Kelsey, *Secure Audit Logs to Support Computer Forensics*, ACM TISSEC 1999 | Hash-chained, MAC-sealed log with an evolving key, so past entries cannot be quietly rewritten | `audit/AuditLog` | E4 (250/250 tampering trials detected), `AuditChainTest` |

---

## 1. Attributes added to RBAC (Kuhn, Coyne, Weil)

**Idea.** Pure role-based access control multiplies roles whenever the scope matters (`Ganga-Mess-Head-Lunch`), and pure attribute-based
control is flexible but hard to administer. The hybrid keeps a few roles and lets attributes decide the rest.

**What we built.** Five roles: `STUDENT`, `WORKER` (kitchen staff), `HEAD` (mess head), `WARDEN`, `ADMIN`. Seventeen rules narrow them with
attributes. Examples: *a head may approve only when `photo.hostel == head.hostel` and the photo is `PENDING`*; *a warden may overturn
only within 7 days of the head's decision and only once*; *a mess head may create kitchen staff only for their own mess*.

**Measured.** For H hostels, pure RBAC would need `3H + 2H + 2` roles (22 today, 502 at 100 hostels); the hybrid needs 5 at any size, and a
new hostel costs one new attribute value instead of five new roles (E1). The deny-overrides rule engine agrees with a separately written
oracle on all 820,800 combinations tested (E2).

The full policy:

```
D-TEMP-PASSWORD  DENY   An account still using a temporary password can do nothing until it is changed.
D-SOD            DENY   Separation of duties: nobody may review, confirm or overturn a photo they uploaded themselves.
P-UPLOAD         PERMIT Kitchen staff may upload photos for their own mess only.
P-WITHDRAW       PERMIT Kitchen staff may withdraw their own photo while it is still pending.
P-OWN-LIST       PERMIT Kitchen staff may list their own submissions.
P-QUEUE          PERMIT A mess head sees the pending queue of their own mess.
P-REVIEW         PERMIT A mess head approves or rejects pending photos of their own mess.
P-RECORDS        PERMIT Mess head and warden see and export every record of their own mess.
P-CONFIRM        PERMIT A warden countersigns a decided photo of their own mess.
P-OVERTURN       PERMIT A warden may overturn a decision of their own mess, with a reason, within the review window.
P-VERIFIED       PERMIT Students may look at approved photos of any mess.
P-METRICS        PERMIT Every signed-in role may see a mess's pass-rate numbers.
P-STAFF-ADMIN    PERMIT The administrator creates and manages mess heads, wardens and kitchen staff.
P-STAFF-HEAD     PERMIT A mess head creates and manages kitchen staff of their own mess only.
P-STAFF-LIST     PERMIT Administrator and mess head may list staff accounts.
P-ADMIN          PERMIT The administrator console.
P-AUDIT          PERMIT Warden and administrator read and verify the audit log and the stored photos.
```

**Differences from the paper.** The paper surveys three ways to combine roles and attributes (dynamic roles, attribute-centric, role-centric).
This implementation is *role-centric*: the role is a precondition, attributes restrict it. Policies are written in Java, not in a policy
language such as XACML.

## 2. ABAC architecture (NIST SP 800-162)

**Idea.** An access request is intercepted by a Policy Enforcement Point (PEP), which asks a Policy Decision Point (PDP) to evaluate it
against policy using subject, resource and environment attributes, then enforces the answer.

**What we built.**

```
screen click ──> service method ──(user, action, resource)──> AccessDecisionService.require()  ──> PERMIT / DENY
                      (PEP)                                         (PDP: deny-overrides, default deny)
```

- **Subject attributes:** id, role, hostel, must-change-password.
- **Resource attributes:** type, hostel, owner (uploader), status, decision time, countersigned, target role.
- **Environment attributes:** current time (the 7-day overturn window).
- Every protected service method calls the PDP first. Writes throw `AccessDeniedException`; reads return an empty result, so a screen
  can never show data the policy does not allow. UI code uses `check()` only to hide or grey out buttons; the service layer re-checks.
- Every **denial is written to the audit log** (`ACCESS_DENIED`, with the rule that blocked it).

**Found while building.** Before this change, `ApprovalService.reviewUpload` did not check that the reviewer belonged to the photo's
hostel; only the screen limited the list. The PEP now enforces it in the service layer (tested in `SecurityFlowTest`).

## 3. Integrity before secrecy (Clark and Wilson)

**Idea.** In business systems the main goal is that data changes only in correct, accountable ways: constrained data items are modified only
by well-formed transactions, duties are separated, every change is logged, and integrity can be verified afterwards.

| Clark-Wilson concept | In MessLens |
|---|---|
| Constrained data items | uploads, approvals, warden reviews, accounts |
| Well-formed transactions | `submitUpload`, `withdrawUpload`, `reviewUpload`, `countersign`: validate, change state, write audit entry **in one database transaction** |
| Allowed state changes | `PENDING -> APPROVED/REJECTED` (head), `APPROVED <-> REJECTED` once (warden, with reason, within 7 days); no edits, no deletes after review |
| Separation of duty | rule `D-SOD`: the uploader can never review the photo; head and warden are different roles; one countersign per decision |
| Audit | `AuditLog` entry inside the same transaction (`UploadDao.insert`, `ApprovalDao.recordDecisionAtomic`, `recordCountersignAtomic`) |
| Integrity verification procedures | `IntegrityService.verifyEvidence` (re-hash every stored photo against the SHA-256 recorded at upload) and `verifyAuditLog` |

**Review chain:** kitchen staff photo (`PENDING`) -> mess head decision (`APPROVED`/`REJECTED`) -> warden confirms or overturns.
Students see a photo once the head approves it; if the warden overturns it, it disappears from the student view and the pass-rate numbers change.

**Differences from the paper.** Clark-Wilson also asks for authenticated users and "certification" of procedures by a security officer;
here authentication is section 4, and certification is the code review plus the tests.

## 4. Authentication (NIST SP 800-63B)

| Guideline | Implementation |
|---|---|
| Store memorised secrets with a salted, deliberately slow hash | PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt, constant-time comparison (`SecurityUtil`) |
| Photo hashing is a different job | Evidence photos still use plain SHA-256 (fast is fine for integrity and duplicate detection) |
| Length over composition rules | Minimum 8 characters, up to 128, spaces and any Unicode allowed, **no** "one capital, one symbol" rules, no forced periodic change; Unicode NFKC normalisation before hashing |
| Block common and breached passwords | 533-entry blocklist plus "common word + digits" and "contains your username / registration number / e-mail name" checks (`PasswordPolicy`) |
| Rate limiting | 4 free failures, then exponential locks 1, 2, 4, 8, 16, 30 minutes; stored in the database, applies to unknown usernames too (`LoginThrottle`); max 56 guesses per account per day |
| Session timeouts | 15 minutes idle, 8 hours absolute, both configurable (`SessionManager`, polled by `AppNavigator`) |
| Do not reveal which accounts exist | one generic error message; a dummy hash is computed for unknown usernames |
| Temporary passwords | random 10-character, shown once, must be changed at first sign-in; until then the account is denied everything (`D-TEMP-PASSWORD`) |
| Upgrade old hashes | accounts that still hold a bare SHA-256 hash sign in once and are re-stored as PBKDF2 automatically |

**Honest note.** The task text suggested bcrypt or Argon2. Java has no built-in implementation of either, and this project has no
extra dependencies, so PBKDF2 (a standard NIST-approved KDF that is part of the JDK) was used. 800-63B names PBKDF2 explicitly. Argon2id is
the stronger, memory-hard choice; moving to it means adding one library and changing `SecurityUtil.hashPassword/verifyPassword`. The stored
format `pbkdf2$<iterations>$<hash>` already carries the algorithm and cost, so old and new hashes can live side by side.

## 5. Secure audit logs (Schneier and Kelsey)

**Idea.** An audit log is trustworthy only if someone who later takes over the machine cannot quietly edit the past. Each entry's
authentication code covers the previous entry, and the key evolves after each entry so earlier keys are gone.

**What we built.**

```
entry_hash(i) = HMAC-SHA256( A_i , entry_hash(i-1) || fields of entry i )
A_(i+1)       = SHA-256( A_i )           <- the old key is overwritten (forward security)
```

- Table `audit_log` has `prev_hash` and `entry_hash` per row; the *current* key lives in `audit/audit_state.key`.
- The first key is written once to `audit/audit_verifier.key`. **Copy this file to an offline place** (for example with the warden).
  With it, *Verify audit log* recomputes every entry from the beginning.
- The key state also remembers how many entries exist and the last hash, so deleting the newest entries is caught.
- Entries are written inside the same database transaction as the change they describe; the key advances only after commit, and a crash
  between the two is repaired on the next start (`AuditChainTest`).
- Logged: sign-in success, failure and lock-out, sign-out, session expiry, password changes, registration, staff creation / reset /
  removal, every upload (with the photo's SHA-256), withdrawal, decision, warden review, access denials, integrity checks.
  Passwords and hashes are never written.

**Measured.** 250 of 250 tampering trials were detected: edit, delete middle, delete newest, empty the log, swap, forged append,
SHA-256 chain recomputation without the key, and an insider holding the *current* key (E4).

**Differences from the paper.** The paper also covers a trusted-machine "commitment" protocol, public-key signatures for third-party
verification and log-entry encryption. Only the forward-secure MAC chain is implemented (the part the paper calls the core). A verifier
with the offline key can check the log; there is no public-key verification.

---

## Threat model and limits

| Threat | Handled by | Not handled |
|---|---|---|
| Student or kitchen staff using another role's screen | PEP in the service layer, not just hidden buttons | - |
| Head of mess A touching mess B | `same hostel` attribute | - |
| Someone approving their own photo | `D-SOD` | collusion between two real people |
| Stolen database file | PBKDF2; audit MACs | plaintext photos in `evidence/` are not encrypted |
| Online password guessing | blocklist, lock-out, generic errors | distributed guessing across many usernames is slowed per account, not globally |
| Editing / deleting past log entries | MAC chain, key evolution, key-state count | attacker holding the database **and** the offline verifier key; deleting database, key state and verifier key together (then the app refuses to extend the log) |
| Edited photo file | `verifyEvidence` re-hash | a photo swapped *before* upload (the app cannot know what the camera saw) |
| Shared machine, forgotten sign-out | idle and absolute timeouts | - |
| Operating-system administrator on the app machine | forward security protects *past* entries only | future entries after takeover can be forged |

Other limits: single-process desktop app (the audit lock is in-process; a multi-server deployment would need a log service);
policies are code, not a policy language; the blocklist is small and offline; the whole 800-63B guideline (account recovery,
multi-factor) is not covered, only memorised-secret and session handling.

## Reproducing the results

The tests are plain `main` programs (no test framework needed) and use a throw-away database and key folder, never your real data.

```
mvn test-compile
mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.messlens.security.PolicyDifferentialTest -Dexec.classpathScope=test
mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.messlens.security.AuditChainTest        -Dexec.classpathScope=test
mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.messlens.security.SecurityFlowTest      -Dexec.classpathScope=test
mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.messlens.security.SecurityEvaluation    -Dexec.classpathScope=test -Dexec.args="docs/EVALUATION.md"
```

## Questions you may be asked

- **Why is SHA-256 wrong for passwords but right for photos?** For photos you want speed and a fixed fingerprint; for passwords speed helps the attacker. A stolen database can be tested at millions of guesses per second with SHA-256 versus single digits with PBKDF2 at this cost (E5).
- **What stops a mess head from approving a photo of another mess?** The service layer asks the PDP; rule `P-REVIEW` requires `photo.hostel == head.hostel`. Hiding the button is not the protection.
- **Why a warden on top of the head?** Clark-Wilson separation of duty: the person whose mess is being judged is not the only person who can decide. The warden's review is logged, can happen once, and has a time limit.
- **Can an administrator edit the audit log?** They can change database rows, but verification then fails (E4), and they cannot recompute past entries because the old keys no longer exist on the server.
- **What if the key files are lost?** The app refuses to extend the existing log rather than silently continue an unauthenticated one.
