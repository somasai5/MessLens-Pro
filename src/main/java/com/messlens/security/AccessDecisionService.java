package com.messlens.security;

import com.messlens.audit.AuditEvent;
import com.messlens.audit.AuditLog;
import com.messlens.exception.AccessDeniedException;
import com.messlens.model.User;
import com.messlens.util.Config;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Policy Decision Point (PDP) of the access-control architecture in NIST SP 800-162.
 *
 *   screen / service  --(subject, action, resource)-->  PEP  --> PDP (this class)  --> PERMIT | DENY
 *
 * The Policy Enforcement Point is {@link #require}: every service method that reads or changes protected
 * data calls it first and stops when the answer is DENY. UI code may call {@link #check} to hide buttons
 * and menu entries, but the service layer re-checks, so a hidden button is never the only protection.
 *
 * Model (Kuhn, Coyne, Weil: "Adding Attributes to Role-Based Access Control", IEEE Computer 2010):
 * a handful of ROLES give the coarse permission, and ATTRIBUTES (hostel, ownership, photo state, time)
 * narrow it. Five roles and the rules below replace the dozens of per-hostel, per-shift roles that pure
 * RBAC would need.
 *
 * Evaluation: deny-overrides, default deny. A request is permitted only when at least one PERMIT rule
 * matches and no DENY rule matches.
 */
public final class AccessDecisionService {

    // ------------------------------------------------------------------ rule machinery

    /** One attribute condition. */
    interface Cond { boolean test(User s, Resource r, Instant now); }

    static final class Rule {
        final String id;
        final String description;
        final boolean deny;
        final Set<Action> actions;
        final Set<String> roles;   // empty = any role
        final List<Cond> conds;

        Rule(String id, String description, boolean deny, Set<Action> actions, Set<String> roles, List<Cond> conds) {
            this.id = id; this.description = description; this.deny = deny;
            this.actions = actions; this.roles = roles; this.conds = conds;
        }

        boolean applies(User s, Action a, Resource r, Instant now) {
            if (!actions.contains(a)) return false;
            if (!roles.isEmpty() && (s.getRole() == null || !roles.contains(s.getRole().toUpperCase()))) return false;
            for (Cond c : conds) if (!c.test(s, r, now)) return false;
            return true;
        }
    }

    // ------------------------------------------------------------------ attribute conditions

    private static final Cond SAME_HOSTEL = (s, r, n) ->
            s.getHostelId() != null && r.hostelId != null && s.getHostelId().equals(r.hostelId);
    private static final Cond IS_OWNER = (s, r, n) -> r.ownerId != null && r.ownerId == s.getId();
    private static final Cond NOT_COUNTERSIGNED = (s, r, n) -> !r.countersigned;
    private static final Cond MUST_CHANGE_PASSWORD = (s, r, n) -> s.isMustChangePassword();

    private static Cond status(String... allowed) {
        Set<String> set = new HashSet<>(Arrays.asList(allowed));
        return (s, r, n) -> r.status != null && set.contains(r.status.toUpperCase());
    }

    private static Cond targetRole(String... allowed) {
        Set<String> set = new HashSet<>(Arrays.asList(allowed));
        return (s, r, n) -> r.targetRole != null && set.contains(r.targetRole.toUpperCase());
    }

    private static Cond decidedWithinDays(int days) {
        return (s, r, n) -> r.decidedAt != null && !r.decidedAt.isAfter(n)
                && Duration.between(r.decidedAt, n).compareTo(Duration.ofDays(days)) <= 0;
    }

    // ------------------------------------------------------------------ the policy

    private final List<Rule> rules = new ArrayList<>();

    private AccessDecisionService() {
        int window = Config.getOverturnWindowDays();

        // --- global denials -------------------------------------------------------------
        deny("D-TEMP-PASSWORD", "An account still using a temporary password can do nothing until it is changed.",
                EnumSet.allOf(Action.class), MUST_CHANGE_PASSWORD);
        deny("D-SOD", "Separation of duties: nobody may review, confirm or overturn a photo they uploaded themselves.",
                EnumSet.of(Action.APPROVE_PHOTO, Action.REJECT_PHOTO, Action.CONFIRM_DECISION, Action.OVERTURN_DECISION), IS_OWNER);

        // --- kitchen staff (WORKER) -----------------------------------------------------
        permit("P-UPLOAD", "Kitchen staff may upload photos for their own mess only.",
                EnumSet.of(Action.UPLOAD_PHOTO), Roles.WORKER, SAME_HOSTEL);
        permit("P-WITHDRAW", "Kitchen staff may withdraw their own photo while it is still pending.",
                EnumSet.of(Action.WITHDRAW_PHOTO), Roles.WORKER, IS_OWNER, status("PENDING"));
        permit("P-OWN-LIST", "Kitchen staff may list their own submissions.",
                EnumSet.of(Action.VIEW_OWN_SUBMISSIONS), Roles.WORKER, IS_OWNER);

        // --- mess head (HEAD) -----------------------------------------------------------
        permit("P-QUEUE", "A mess head sees the pending queue of their own mess.",
                EnumSet.of(Action.VIEW_REVIEW_QUEUE), Roles.HEAD, SAME_HOSTEL);
        permit("P-REVIEW", "A mess head approves or rejects pending photos of their own mess.",
                EnumSet.of(Action.APPROVE_PHOTO, Action.REJECT_PHOTO), Roles.HEAD, SAME_HOSTEL, status("PENDING"));

        // --- records: head and warden of the same mess ----------------------------------
        permit("P-RECORDS", "Mess head and warden see and export every record of their own mess.",
                EnumSet.of(Action.VIEW_HOSTEL_RECORDS, Action.EXPORT_RECORDS), new String[]{Roles.HEAD, Roles.WARDEN}, SAME_HOSTEL);

        // --- warden (second reviewer) ---------------------------------------------------
        permit("P-CONFIRM", "A warden countersigns a decided photo of their own mess.",
                EnumSet.of(Action.CONFIRM_DECISION), Roles.WARDEN, SAME_HOSTEL, status("APPROVED", "REJECTED"), NOT_COUNTERSIGNED);
        permit("P-OVERTURN", "A warden may overturn a decision of their own mess, with a reason, within the review window.",
                EnumSet.of(Action.OVERTURN_DECISION), Roles.WARDEN, SAME_HOSTEL, status("APPROVED", "REJECTED"),
                NOT_COUNTERSIGNED, decidedWithinDays(window));

        // --- students --------------------------------------------------------------------
        permit("P-VERIFIED", "Students may look at approved photos of any mess.",
                EnumSet.of(Action.VIEW_VERIFIED_PHOTOS), Roles.STUDENT);
        permit("P-METRICS", "Every signed-in role may see a mess's pass-rate numbers.",
                EnumSet.of(Action.VIEW_HOSTEL_METRICS), Roles.ALL);

        // --- staff management ------------------------------------------------------------
        permit("P-STAFF-ADMIN", "The administrator creates and manages mess heads, wardens and kitchen staff.",
                EnumSet.of(Action.CREATE_STAFF, Action.MANAGE_STAFF), Roles.ADMIN, targetRole(Roles.HEAD, Roles.WARDEN, Roles.WORKER));
        permit("P-STAFF-HEAD", "A mess head creates and manages kitchen staff of their own mess only.",
                EnumSet.of(Action.CREATE_STAFF, Action.MANAGE_STAFF), Roles.HEAD, SAME_HOSTEL, targetRole(Roles.WORKER));
        permit("P-STAFF-LIST", "Administrator and mess head may list staff accounts.",
                EnumSet.of(Action.VIEW_STAFF), new String[]{Roles.ADMIN, Roles.HEAD});

        // --- oversight -------------------------------------------------------------------
        permit("P-ADMIN", "The administrator console.",
                EnumSet.of(Action.VIEW_ADMIN_DASHBOARD), Roles.ADMIN);
        permit("P-AUDIT", "Warden and administrator read and verify the audit log and the stored photos.",
                EnumSet.of(Action.VIEW_AUDIT_LOG, Action.VERIFY_AUDIT_LOG, Action.VERIFY_EVIDENCE),
                new String[]{Roles.WARDEN, Roles.ADMIN});
    }

    private void permit(String id, String desc, Set<Action> actions, String role, Cond... conds) {
        permit(id, desc, actions, new String[]{role}, conds);
    }

    private void permit(String id, String desc, Set<Action> actions, String[] roles, Cond... conds) {
        rules.add(new Rule(id, desc, false, actions, new HashSet<>(Arrays.asList(roles)), Arrays.asList(conds)));
    }

    private void deny(String id, String desc, Set<Action> actions, Cond... conds) {
        rules.add(new Rule(id, desc, true, actions, Collections.emptySet(), Arrays.asList(conds)));
    }

    // ------------------------------------------------------------------ evaluation (PDP)

    public Decision check(User subject, Action action, Resource resource) {
        return check(subject, action, resource, Instant.now());
    }

    /** @param now the environment attribute "current time" (injectable for tests). */
    public Decision check(User subject, Action action, Resource resource, Instant now) {
        if (subject == null || subject.getRole() == null) return new Decision(false, "D-NO-SUBJECT", "not signed in");
        Resource res = resource == null ? Resource.of("none") : resource;
        for (Rule r : rules) {
            if (r.deny && r.applies(subject, action, res, now)) return new Decision(false, r.id, r.description);
        }
        for (Rule r : rules) {
            if (!r.deny && r.applies(subject, action, res, now)) return new Decision(true, r.id, r.description);
        }
        return new Decision(false, "D-DEFAULT", "No rule permits this role to " + action + " in this situation (default deny).");
    }

    // ------------------------------------------------------------------ enforcement (PEP)

    /** Throws {@link AccessDeniedException} (and records the denial in the audit log) unless the PDP says PERMIT. */
    public void require(User subject, Action action, Resource resource) throws AccessDeniedException {
        Decision d = check(subject, action, resource);
        if (d.permit) return;
        try {
            AuditLog.log(AuditEvent.by(subject, "ACCESS_DENIED")
                    .resource(resource == null ? null : resource.type, resource == null ? null : resource.id)
                    .hostel(resource == null ? null : resource.hostelId)
                    .outcome("DENY")
                    .detail(action + " blocked by " + d.ruleId));
        } catch (RuntimeException ignored) {
            // a denial must still be a denial even if the log is unavailable
        }
        throw new AccessDeniedException("Not allowed: " + d.reason, d.ruleId);
    }

    // ------------------------------------------------------------------ introspection (docs, evaluation)

    public int ruleCount() { return rules.size(); }

    /** Human-readable policy listing: "id  [PERMIT|DENY]  description". */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Rule r : rules) out.add(String.format("%-16s %-6s %s", r.id, r.deny ? "DENY" : "PERMIT", r.description));
        return out;
    }

    /** Declared last on purpose: the attribute conditions above must be initialised before the policy is built. */
    public static final AccessDecisionService INSTANCE = new AccessDecisionService();
}
