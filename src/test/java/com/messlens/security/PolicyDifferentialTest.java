package com.messlens.security;

import com.messlens.model.User;

import java.time.Duration;
import java.time.Instant;

/**
 * Differential test of the Policy Decision Point: the rule engine is compared with an independently written
 * oracle (plain nested logic, written from the policy text) for every combination of role, action and attribute values.
 * Any difference is a bug in one of the two.
 */
public class PolicyDifferentialTest {

    static final Integer[] SUBJECT_HOSTEL = {1, null};
    static final Integer[] RESOURCE_HOSTEL = {1, 2, null};
    static final String[] OWNER = {"SELF", "OTHER", "NONE"};
    static final String[] STATUS = {"PENDING", "APPROVED", "REJECTED", null};
    static final String[] TARGET = {null, "WORKER", "HEAD", "WARDEN", "ADMIN", "STUDENT"};
    static final long[] AGE_DAYS = {Long.MIN_VALUE, -1, 1, 7, 8};   // MIN = no decision time

    public static void main(String[] args) {
        TestKit.section("Policy engine vs independent oracle");
        long[] r = sweep();
        long cases = r[0], permits = r[1], mismatches = r[2];
        System.out.println("  combinations checked: " + cases + "  (permitted: " + permits + ", denied: " + (cases - permits) + ")");
        TestKit.check("engine agrees with oracle on every combination (mismatches = " + mismatches + ")", mismatches == 0);
        AccessDecisionService pdp = AccessDecisionService.INSTANCE;

        // a few named scenarios, readable by a human
        User head = new User(1, "head1", "h", "s", Roles.HEAD, 1);
        User worker = new User(2, "worker1", "h", "s", Roles.WORKER, 1);
        TestKit.check("head of mess 1 cannot approve a photo of mess 2",
                !pdp.check(head, Action.APPROVE_PHOTO, Resource.of("upload").hostelId(2).owner(2).status("PENDING")).permit);
        TestKit.check("head of mess 1 can approve a pending photo of mess 1",
                pdp.check(head, Action.APPROVE_PHOTO, Resource.of("upload").hostelId(1).owner(2).status("PENDING")).permit);
        TestKit.check("nobody reviews their own photo (D-SOD), even a head",
                pdp.check(head, Action.APPROVE_PHOTO, Resource.of("upload").hostelId(1).owner(1).status("PENDING")).ruleId.equals("D-SOD"));
        TestKit.check("kitchen staff cannot approve", !pdp.check(worker, Action.APPROVE_PHOTO, Resource.of("upload").hostelId(1).owner(5).status("PENDING")).permit);
        TestKit.check("unknown subject is denied", !pdp.check(null, Action.VIEW_HOSTEL_METRICS, null).permit);
        System.out.println("  policy rules: " + pdp.ruleCount() + " rules for " + Roles.ALL.length + " roles");
        TestKit.summary("PolicyDifferentialTest");
    }

    /** @return {combinations, permitted, mismatches} */
    static long[] sweep() {
        AccessDecisionService pdp = AccessDecisionService.INSTANCE;
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        long cases = 0, permits = 0, mismatches = 0;
        for (String role : Roles.ALL)
        for (Integer sh : SUBJECT_HOSTEL)
        for (boolean mustChange : new boolean[]{false, true}) {
            User s = new User(10, "subject", "h", "s", role, sh);
            s.setMustChangePassword(mustChange);
            for (Action action : Action.values())
            for (Integer rh : RESOURCE_HOSTEL)
            for (String owner : OWNER)
            for (String status : STATUS)
            for (boolean countersigned : new boolean[]{false, true})
            for (String target : TARGET)
            for (long age : AGE_DAYS) {
                Resource r = Resource.of("x").hostelId(rh).status(status).countersigned(countersigned).targetRole(target);
                if (owner.equals("SELF")) r.owner(10); else if (owner.equals("OTHER")) r.owner(99);
                if (age != Long.MIN_VALUE) r.decidedAt(now.minus(Duration.ofDays(age)));

                boolean got = pdp.check(s, action, r, now).permit;
                boolean want = oracle(role, sh, mustChange, action, rh, owner, status, countersigned, target, age);
                cases++;
                if (got) permits++;
                if (got != want) {
                    mismatches++;
                    if (mismatches == 1) System.out.println("  first mismatch: " + role + " " + action + " subjHostel=" + sh + " resHostel=" + rh
                            + " owner=" + owner + " status=" + status + " cs=" + countersigned + " target=" + target + " age=" + age
                            + " mustChange=" + mustChange + " engine=" + got + " oracle=" + want);
                }
            }
        }
        return new long[]{cases, permits, mismatches};
    }

    /** Independent re-statement of the policy, written as plain logic and NOT sharing code with the engine. */
    static boolean oracle(String role, Integer sh, boolean mustChange, Action a, Integer rh, String owner,
                          String status, boolean cs, String target, long ageDays) {
        if (mustChange) return false;
        boolean same = sh != null && rh != null && sh.equals(rh);
        boolean self = owner.equals("SELF");
        boolean decided = "APPROVED".equals(status) || "REJECTED".equals(status);
        boolean admin = role.equals("ADMIN"), head = role.equals("HEAD"), warden = role.equals("WARDEN"),
                worker = role.equals("WORKER"), student = role.equals("STUDENT");
        switch (a) {
            case UPLOAD_PHOTO: return worker && same;
            case WITHDRAW_PHOTO: return worker && self && "PENDING".equals(status);
            case VIEW_OWN_SUBMISSIONS: return worker && self;
            case VIEW_REVIEW_QUEUE: return head && same;
            case APPROVE_PHOTO:
            case REJECT_PHOTO: return head && same && "PENDING".equals(status) && !self;
            case VIEW_HOSTEL_RECORDS:
            case EXPORT_RECORDS: return (head || warden) && same;
            case VIEW_VERIFIED_PHOTOS: return student;
            case VIEW_HOSTEL_METRICS: return true;
            case CONFIRM_DECISION: return warden && same && decided && !cs && !self;
            case OVERTURN_DECISION: return warden && same && decided && !cs && !self && ageDays != Long.MIN_VALUE && ageDays >= 0 && ageDays <= 7;
            case CREATE_STAFF:
            case MANAGE_STAFF:
                if (admin) return "HEAD".equals(target) || "WARDEN".equals(target) || "WORKER".equals(target);
                if (head) return same && "WORKER".equals(target);
                return false;
            case VIEW_STAFF: return admin || head;
            case VIEW_ADMIN_DASHBOARD: return admin;
            case VIEW_AUDIT_LOG:
            case VERIFY_AUDIT_LOG:
            case VERIFY_EVIDENCE: return admin || warden;
            default: return false;
        }
    }
}
