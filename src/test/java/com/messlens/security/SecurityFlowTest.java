package com.messlens.security;

import com.messlens.audit.AuditEntry;
import com.messlens.audit.AuditLog;
import com.messlens.dao.DatabaseManager;
import com.messlens.dao.HostelDao;
import com.messlens.dao.UserDao;
import com.messlens.exception.AccessDeniedException;
import com.messlens.exception.AuthenticationException;
import com.messlens.exception.ValidationException;
import com.messlens.model.Hostel;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.ApprovalService;
import com.messlens.service.AuthService;
import com.messlens.service.IntegrityService;
import com.messlens.service.UploadService;
import com.messlens.util.SecurityUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/** End-to-end checks of authentication, authorisation, the review chain, integrity checks and the audit trail. */
public class SecurityFlowTest {

    static int imgSeed = 1;

    public static void main(String[] args) throws Exception {
        TestKit.freshEnvironment();
        AuthService auth = new AuthService();
        UserDao users = new UserDao();
        UploadService uploads = new UploadService();
        ApprovalService approvals = new ApprovalService();
        IntegrityService integrity = new IntegrityService();
        int ganga = hostel("Ganga"), annapurna = hostel("Annapurna");

        // ------------------------------------------------------------------ passwords
        TestKit.section("NIST 800-63B: password storage and policy");
        User seeded = users.findByUsername("worker1");
        TestKit.check("stored password uses PBKDF2 with iteration count", seeded.getPasswordHash().startsWith("pbkdf2$2000$"));
        TestKit.check("stored value is not the plaintext", !seeded.getPasswordHash().contains("worker123"));
        TestKit.check("same password + different salt gives different hash",
                !SecurityUtil.hashPassword("abc defg hij", "c2FsdDE=").equals(SecurityUtil.hashPassword("abc defg hij", "c2FsdDI=")));

        String salt = SecurityUtil.generateSalt();
        users.insert(newUser("legacy1", SecurityUtil.legacyHashPassword("oldpass99", salt), salt, "STUDENT", null));
        TestKit.check("old SHA-256 account still signs in", auth.login("legacy1", "oldpass99") != null);
        TestKit.check("... and was upgraded to PBKDF2 on that sign-in", users.findByUsername("legacy1").getPasswordHash().startsWith("pbkdf2$"));
        TestKit.check("... and still signs in afterwards", auth.login("legacy1", "oldpass99") != null);

        TestKit.check("5 characters is too short", PasswordPolicy.check("abc12") != null);
        TestKit.check("common-looking password is accepted (rule removed)", PasswordPolicy.check("password123") == null);
        TestKit.check("long passphrase with spaces accepted", PasswordPolicy.check("correct horse battery staple") == null);

        TestKit.section("NIST 800-63B: rate limiting of wrong passwords");
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        LoginThrottle.setClock(clock::get);
        String lastMsg = "";
        for (int i = 1; i <= 4; i++) lastMsg = tryLogin(auth, "student1", "wrong-" + i);
        TestKit.check("first 4 wrong passwords: plain error, no lock", lastMsg.startsWith("Wrong"));
        lastMsg = tryLogin(auth, "student1", "wrong-5");
        TestKit.check("5th wrong password locks the account (" + lastMsg + ")", lastMsg.startsWith("Too many"));
        TestKit.check("correct password is refused while locked", tryLogin(auth, "student1", "student123").startsWith("Too many"));
        clock.addAndGet(61_000);
        TestKit.check("after the lock expires the correct password works", tryLogin(auth, "student1", "student123").equals("OK"));
        for (int i = 0; i < 5; i++) tryLogin(auth, "no-such-user", "x" + i);
        TestKit.check("unknown usernames are throttled too (no account enumeration)", tryLogin(auth, "no-such-user", "x").startsWith("Too many"));
        TestKit.check("lock grows exponentially and is capped",
                LoginThrottle.lockMillis(5) == 60_000 && LoginThrottle.lockMillis(6) == 120_000
                        && LoginThrottle.lockMillis(7) == 240_000 && LoginThrottle.lockMillis(99) == 30 * 60_000L);
        LoginThrottle.setClock(null);

        TestKit.section("NIST 800-63B: session timeouts");
        AtomicLong sc = new AtomicLong(0);
        SessionManager.setClock(sc::get);
        SessionManager.start(seeded);
        TestKit.check("fresh session is valid", SessionManager.expiredReason() == null);
        sc.addAndGet(14 * 60_000L);
        SessionManager.touch();
        sc.addAndGet(14 * 60_000L);
        TestKit.check("activity keeps the session alive past the idle limit", SessionManager.expiredReason() == null);
        sc.addAndGet(16 * 60_000L);
        TestKit.check("15+ idle minutes ends the session", "idle".equals(SessionManager.expiredReason()));
        SessionManager.start(seeded);
        for (int i = 0; i < 9; i++) { sc.addAndGet(55 * 60_000L); SessionManager.touch(); }
        TestKit.check("8 hours ends the session even when active", "absolute".equals(SessionManager.expiredReason()));
        SessionManager.setClock(null);
        SessionManager.end();

        // ------------------------------------------------------------------ accounts
        TestKit.section("Accounts: registration and staff creation go through the policy");
        TestKit.check("student cannot register with a common password",
                fails(() -> auth.registerStudent("Saranya K", "AP23110010123", "saranya_k@srmap.edu.in", annapurna, "password123", "password123")));
        User student = auth.registerStudent("Saranya K", "AP23110010123", "saranya_k@srmap.edu.in", annapurna,
                "lantern bridge monsoon", "lantern bridge monsoon");
        TestKit.check("student registers with a passphrase", student != null && student.getRole().equals("STUDENT"));

        User admin = auth.login("admin", "admin123");
        TestKit.check("seeded admin must change the default password", admin.isMustChangePassword());
        TestKit.check("temporary-password account is denied everything (D-TEMP-PASSWORD)",
                !AccessDecisionService.INSTANCE.check(admin, Action.VIEW_ADMIN_DASHBOARD, Resource.of("x")).permit);
        auth.changePassword("admin123", "slate harbour violet 58");
        admin = users.findByUsername("admin");
        TestKit.check("after the change the admin console is allowed",
                AccessDecisionService.INSTANCE.check(admin, Action.VIEW_ADMIN_DASHBOARD, Resource.of("x")).permit);

        String tHead = auth.createStaff(admin, "HEAD", "Ravi Head", "annapurna.head", annapurna);
        String tWarden = auth.createStaff(admin, "WARDEN", "Anu Warden", "annapurna.warden", annapurna);
        auth.login("annapurna.head", tHead);
        auth.changePassword(tHead, "paper kite orchard 31");
        final User headB = users.findByUsername("annapurna.head");
        auth.login("annapurna.warden", tWarden);
        auth.changePassword(tWarden, "copper mango tide 44");
        final User wardenB = users.findByUsername("annapurna.warden");
        User head1 = auth.login("head1", "head123");
        User worker = auth.login("worker1", "worker123");
        User warden1 = auth.login("warden1", "warden1234");
        TestKit.check("head cannot create a head", denied(() -> auth.createStaff(head1, "HEAD", "X Y", "x.y.z", ganga)));
        TestKit.check("head cannot create staff for another mess: hostel is forced to their own",
                users.findByUsername("x.y.z") == null);
        TestKit.check("head creates kitchen staff for own mess", auth.createStaff(head1, "WORKER", "Cook Two", "ganga.cook2", annapurna) != null
                && users.findByUsername("ganga.cook2").getHostelId() == ganga);
        TestKit.check("student cannot create staff", denied(() -> auth.createStaff(users.findByUsername("student1"), "WORKER", "Cook Q", "cook.q", ganga)));
        TestKit.check("head cannot reset a warden's password", denied(() -> auth.resetStaffPassword(head1, users.findByUsername("warden1"))));

        // ------------------------------------------------------------------ review chain
        TestKit.section("Review chain: worker -> head -> warden (Clark-Wilson)");
        Upload up = uploads.submitUpload(worker, ganga, "Lunch", "Rice", "Ingredients", img());
        TestKit.check("worker upload is PENDING", "PENDING".equals(up.getStatus()) && up.getWorkerId() == worker.getId());
        TestKit.check("worker cannot upload into another mess", denied(() -> uploads.submitUpload(worker, annapurna, "Lunch", "Dal", "Cooking", img())));
        TestKit.check("worker cannot approve", denied(() -> approvals.reviewUpload(worker, up.getId(), "APPROVED", "")));
        TestKit.check("head of ANOTHER mess cannot approve", denied(() -> approvals.reviewUpload(headB, up.getId(), "APPROVED", "")));
        TestKit.check("warden cannot do the head's job", denied(() -> approvals.reviewUpload(warden1, up.getId(), "APPROVED", "")));
        TestKit.check("rejecting without a remark is refused", fails(() -> approvals.reviewUpload(head1, up.getId(), "REJECTED", " ")));
        TestKit.check("head of the same mess approves", approvals.reviewUpload(head1, up.getId(), "APPROVED", ""));
        TestKit.check("a decided photo cannot be decided again", denied(() -> approvals.reviewUpload(head1, up.getId(), "REJECTED", "changed my mind")));

        TestKit.check("worker sees no mess records", uploads.getAllUploads(worker, ganga).isEmpty());
        TestKit.check("head of another mess sees no records of this mess", uploads.getAllUploads(headB, ganga).isEmpty());
        TestKit.check("head sees own mess records", uploads.getAllUploads(head1, ganga).size() == 1);
        User studentDemo = auth.login("student1", "student123");
        TestKit.check("student sees the approved photo", uploads.getApprovedTimeline(studentDemo, ganga, null, "ALL").size() == 1);
        TestKit.check("student cannot read the pending queue", uploads.getPendingUploads(studentDemo, ganga).isEmpty());
        TestKit.check("everyone may read pass-rate numbers", uploads.getHostelMetrics(studentDemo, ganga).getApprovedCount() == 1);

        // separation of duties: a worker who is later promoted to head cannot approve his own photo
        Upload own = uploads.submitUpload(worker, ganga, "Lunch", "Dal", "Cooking", img());
        exec("UPDATE users SET role = 'HEAD' WHERE username = 'worker1'");
        User promoted = users.findByUsername("worker1");
        AccessDeniedException sod = null;
        try { approvals.reviewUpload(promoted, own.getId(), "APPROVED", ""); } catch (AccessDeniedException e) { sod = e; }
        TestKit.check("uploader cannot approve their own photo even after promotion (rule " + (sod == null ? "none" : sod.getRuleId()) + ")",
                sod != null && "D-SOD".equals(sod.getRuleId()));
        exec("UPDATE users SET role = 'WORKER' WHERE username = 'worker1'");

        TestKit.section("Warden: second review with time window");
        TestKit.check("overturn needs a reason", fails(() -> approvals.countersign(warden1, up.getId(), "OVERTURN", "")));
        TestKit.check("warden of ANOTHER mess cannot countersign", denied(() -> approvals.countersign(wardenB, up.getId(), "CONFIRM", "")));
        TestKit.check("head cannot countersign", denied(() -> approvals.countersign(head1, up.getId(), "CONFIRM", "")));
        TestKit.check("warden overturns an approval with a reason", approvals.countersign(warden1, up.getId(), "OVERTURN", "Photo is too blurry to show the rice"));
        TestKit.check("student no longer sees the overturned photo", uploads.getApprovedTimeline(studentDemo, ganga, null, "ALL").isEmpty());
        TestKit.check("a decision can be countersigned only once", denied(() -> approvals.countersign(warden1, up.getId(), "CONFIRM", "")));

        Upload u2 = uploads.submitUpload(worker, ganga, "Dinner", "Roti", "Serving", img());
        approvals.reviewUpload(head1, u2.getId(), "REJECTED", "Plate not visible");
        TestKit.check("warden confirms a rejection", approvals.countersign(warden1, u2.getId(), "CONFIRM", ""));
        Upload u3 = uploads.submitUpload(worker, ganga, "Dinner", "Curry", "Serving", img());
        approvals.reviewUpload(head1, u3.getId(), "APPROVED", "");
        exec("UPDATE approvals SET decided_at = datetime('now','localtime','-10 days') WHERE upload_id = " + u3.getId());
        TestKit.check("overturn is refused once the 7-day window has passed", denied(() -> approvals.countersign(warden1, u3.getId(), "OVERTURN", "late")));
        TestKit.check("confirming is still possible after the window", approvals.countersign(warden1, u3.getId(), "CONFIRM", ""));

        // ------------------------------------------------------------------ integrity + audit
        TestKit.section("Integrity verification procedures and the audit trail");
        IntegrityService.EvidenceReport er = integrity.verifyEvidence(warden1);
        TestKit.check("all stored photos match the SHA-256 recorded at upload (" + er.intact + "/" + er.checked + ")", er.ok() && er.checked >= 3);
        try (FileOutputStream f = new FileOutputStream(new File(up.getImagePath()), true)) { f.write(7); }
        er = integrity.verifyEvidence(admin);
        TestKit.check("a photo changed on disk is detected", !er.ok() && er.problems.size() == 1);
        TestKit.check("student cannot run the checks", denied(() -> integrity.verifyAuditLog(studentDemo)));
        TestKit.check("audit log verifies after all of the above", integrity.verifyAuditLog(warden1).ok);

        List<AuditEntry> log = AuditLog.recent(1000);
        for (String a : new String[]{"LOGIN_SUCCESS", "LOGIN_FAILED", "LOGIN_LOCKED", "STAFF_CREATED", "STUDENT_REGISTERED", "PASSWORD_CHANGED",
                "UPLOAD_SUBMITTED", "DECISION_APPROVED", "DECISION_REJECTED", "WARDEN_OVERTURNED", "WARDEN_CONFIRMED", "ACCESS_DENIED", "EVIDENCE_VERIFIED"}) {
            TestKit.check("audit log contains " + a, log.stream().anyMatch(e -> a.equals(e.action)));
        }
        TestKit.check("upload entry carries the photo's SHA-256", log.stream().anyMatch(e -> "UPLOAD_SUBMITTED".equals(e.action) && e.detail.contains("sha256=" + up.getImageHash())));
        TestKit.check("no password or hash ever appears in the log", log.stream().noneMatch(e ->
                (e.detail != null && (e.detail.contains("pbkdf2") || e.detail.contains("worker123") || e.detail.contains(tHead)))));
        TestKit.summary("SecurityFlowTest");
    }

    // ---- helpers ---------------------------------------------------------------------------

    static byte[] img() { byte[] b = new byte[3000]; new Random(imgSeed++ * 7919L).nextBytes(b); return b; }

    static int hostel(String name) {
        for (Hostel h : new HostelDao().findAll()) if (h.getName().equals(name)) return h.getId();
        throw new IllegalStateException(name);
    }

    static User newUser(String name, String hash, String salt, String role, Integer hostel) {
        return new User(0, name, hash, salt, role, hostel);
    }

    static void exec(String sql) throws Exception {
        try (Connection c = DatabaseManager.getConnection(); Statement s = c.createStatement()) { s.execute(sql); }
    }

    static String tryLogin(AuthService a, String u, String p) {
        try { a.login(u, p); return "OK"; }
        catch (AuthenticationException | ValidationException e) { return e.getMessage(); }
    }

    static boolean denied(Callable<?> c) {
        try { c.call(); return false; }
        catch (AccessDeniedException e) { return true; }
        catch (Exception e) { return false; }
    }

    static boolean fails(Callable<?> c) {
        try { c.call(); return false; } catch (Exception e) { return true; }
    }
}
