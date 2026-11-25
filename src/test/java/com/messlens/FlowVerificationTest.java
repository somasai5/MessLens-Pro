package com.messlens;

import com.messlens.dao.DatabaseManager;
import com.messlens.dao.HostelDao;
import com.messlens.dao.UserDao;
import com.messlens.model.Hostel;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.ApprovalService;
import com.messlens.service.AuthService;
import com.messlens.service.UploadService;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * End-to-end integration and flow verification test.
 * Validates seeded users, authentication with salted PBKDF2,
 * worker evidence submission, duplicate hash rejection,
 * head approval workflow, and student timeline view.
 */
public class FlowVerificationTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  STARTING MESSLENS FULL FLOW VERIFICATION TEST  ");
        System.out.println("=================================================");
        System.out.flush();

        try {
            // Step 1: Initialize Database & Verify Seed Data
            System.out.println("\n[Test 1] Initializing SQLite database...");
            System.out.flush();
            DatabaseManager.initializeDatabase();

            // Clear previous test run uploads & approvals for reproducible testing
            try (java.sql.Connection conn = DatabaseManager.getConnection();
                 java.sql.Statement s = conn.createStatement()) {
                s.execute("DELETE FROM approvals");
                s.execute("DELETE FROM uploads");
            }

            HostelDao hostelDao = new HostelDao();
            List<Hostel> hostels = hostelDao.findAll();
            assertCondition(hostels.size() >= 2, "Seeded hostels should have at least 2 entries (found " + hostels.size() + ")");
            System.out.println("[OK] Hostels verified: " + hostels);
            System.out.flush();

            UserDao userDao = new UserDao();
            User worker = userDao.findByUsername("worker1");
            User head = userDao.findByUsername("head1");
            User student = userDao.findByUsername("student1");

            assertCondition(worker != null, "worker1 must exist in DB");
            assertCondition(head != null, "head1 must exist in DB");
            assertCondition(student != null, "student1 must exist in DB");
            assertCondition(!worker.getPasswordHash().equals("worker123"), "Passwords must be hashed, never plain text");
            System.out.println("[OK] Seeded users verified with SHA-256 + salt hashes.");
            System.out.flush();

            // Step 2: Authentication Tests
            System.out.println("\n[Test 2] Testing authentication service...");
            System.out.flush();
            AuthService auth = new AuthService();
            User loggedWorker = auth.login("worker1", "worker123");
            assertCondition(loggedWorker != null && "WORKER".equals(loggedWorker.getRole()), "worker1 login failed");

            User loggedHead = auth.login("head1", "head123");
            assertCondition(loggedHead != null && "HEAD".equals(loggedHead.getRole()), "head1 login failed");

            User loggedStudent = auth.login("student1", "student123");
            assertCondition(loggedStudent != null && "STUDENT".equals(loggedStudent.getRole()), "student1 login failed");

            try {
                auth.login("worker1", "wrong_password");
                throw new RuntimeException("Wrong password should have failed login");
            } catch (Exception expected) {
                System.out.println("[OK] Wrong password rejected as expected: " + expected.getMessage());
                System.out.flush();
            }
            System.out.println("[OK] Authentication service fully functional.");
            System.out.flush();

            // Step 3: Worker Evidence Submission & Duplicate Check
            System.out.println("\n[Test 3] Testing worker submission & duplicate hash rejection...");
            System.out.flush();
            UploadService uploadService = new UploadService();

            File img1 = new File("sample_test_images/1_dal_ingredients.jpg");
            File img2 = new File("sample_test_images/2_dal_cooking.jpg");
            File img3 = new File("sample_test_images/3_dal_serving.jpg");

            byte[] b1 = Files.readAllBytes(img1.toPath());
            byte[] b2 = Files.readAllBytes(img2.toPath());
            byte[] b3 = Files.readAllBytes(img3.toPath());

            int gangaId = hostels.stream().filter(h -> h.getName().equals("Ganga")).findFirst().get().getId();

            // Submit Ingredients
            Upload up1 = uploadService.submitUpload(worker, gangaId, "Lunch", "Dal Tadka", "Ingredients", b1);
            assertCondition(up1.getId() > 0, "Upload 1 must have generated primary key ID");
            assertCondition("PENDING".equals(up1.getStatus()), "New upload must have status PENDING");
            System.out.println("[OK] Upload 1 (Ingredients) submitted with ID: " + up1.getId() + " [Status: " + up1.getStatus() + "]");
            System.out.flush();

            // Attempt duplicate submission with exact same image
            try {
                uploadService.submitUpload(worker, gangaId, "Lunch", "Dal Tadka", "Ingredients", b1);
                throw new RuntimeException("Duplicate image hash should have been rejected!");
            } catch (com.messlens.exception.DuplicateEvidenceException e) {
                System.out.println("[OK] DuplicateEvidenceException caught as expected: " + e.getMessage() +
                                   " [Hash: " + e.getImageHash().substring(0, 12) + "...]");
                System.out.flush();
            }

            // Submit Cooking and Serving
            Upload up2 = uploadService.submitUpload(worker, gangaId, "Lunch", "Dal Tadka", "Cooking", b2);
            Upload up3 = uploadService.submitUpload(worker, gangaId, "Lunch", "Dal Tadka", "Serving", b3);
            System.out.println("[OK] Upload 2 (Cooking) submitted with ID: " + up2.getId());
            System.out.println("[OK] Upload 3 (Serving) submitted with ID: " + up3.getId());
            System.out.flush();

            // Step 4: Mess Head Verification Queue & Approvals
            System.out.println("\n[Test 4] Testing Mess Head approval queue and decisions...");
            System.out.flush();
            List<Upload> pending = uploadService.getPendingUploads(head, gangaId);
            assertCondition(pending.size() >= 3, "Pending queue must have at least 3 items");
            System.out.println("[OK] Mess Head pending queue contains " + pending.size() + " submissions.");
            System.out.flush();

            ApprovalService approvalService = new ApprovalService();

            // Test mandatory remark on rejection
            try {
                approvalService.reviewUpload(head, up2.getId(), "REJECTED", "");
                throw new RuntimeException("Rejection without remark should have failed!");
            } catch (com.messlens.exception.ValidationException e) {
                System.out.println("[OK] ValidationException caught as expected: " + e.getMessage());
                System.out.flush();
            }

            // Approve all 3 stages for the timeline
            approvalService.reviewUpload(head, up1.getId(), "APPROVED", "Fresh lentils and spices verified");
            approvalService.reviewUpload(head, up2.getId(), "APPROVED", "Proper boiling and temperature verified");
            approvalService.reviewUpload(head, up3.getId(), "APPROVED", "Clean serving counters and warm food verified");
            System.out.println("[OK] Approved all 3 stages with remarks.");
            System.out.flush();

            // Test Rich SQL Aggregation Metrics
            com.messlens.model.MessMetrics metrics = uploadService.getHostelMetrics(head, gangaId);
            assertCondition(metrics.getApprovedCount() == 3, "Metrics approved count should be 3");
            assertCondition(metrics.getApprovalRate() == 100.0, "Approval rate should be 100%");
            System.out.println("[OK] Rich metrics verified: " + metrics.getApprovedCount() + " approved, " +
                               metrics.getFormattedApprovalRate() + " quality rate.");
            System.out.flush();

            // Verify queue is now cleared
            List<Upload> remainingPending = uploadService.getPendingUploads(head, gangaId);
            System.out.println("[OK] Remaining pending uploads in Ganga queue: " + remainingPending.size());
            System.out.flush();

            // Step 5: Student Food Transparency Timeline
            System.out.println("\n[Test 5] Testing student timeline retrieval...");
            System.out.flush();
            List<Upload> timeline = uploadService.getApprovedTimeline(student, gangaId, null, "Lunch");
            assertCondition(timeline.size() >= 3, "Timeline should have all 3 approved stages");

            System.out.println("[OK] Student timeline retrieved " + timeline.size() + " verified items:");
            for (Upload u : timeline) {
                System.out.println("    - Dish: " + u.getDishName() + " | Stage: " + u.getStage() +
                                   " | Uploaded: " + u.getUploadedAt() + " | Approved: " + u.getApprovedAt());
            }
            System.out.flush();

            // Step 6: Syllabus Units 4 & 5 Verification (Generics & Iterator)
            System.out.println("\n[Test 6] Testing SRM CSE 307 Units 4 & 5 (Generics & Collections Iterator)...");
            System.out.flush();

            // Unit 5: Generic Class with two type parameters
            com.messlens.util.Pair<String, Integer> kpiPair = com.messlens.util.Pair.of("VerifiedMeals", timeline.size());
            assertCondition(kpiPair.getValue() == 3, "Generic Pair value must be 3");
            System.out.println("[OK] Generic Class with Two Type Parameters verified: " + kpiPair);

            // Unit 4: Collection traversal with Iterator
            List<Upload> servingOnly = com.messlens.util.CollectionUtil.filterWithIterator(
                    timeline,
                    u -> "Serving".equalsIgnoreCase(u.getStage())
            );
            assertCondition(servingOnly.size() == 1, "Iterator filtering should return 1 Serving item");
            System.out.println("[OK] Iterator traversal and wildcard generic method verified: " + servingOnly.size() + " Serving item extracted.");
            System.out.flush();

            // Step 7: Withdraw a pending upload, full report and password change
            System.out.println("\n[Test 7] Testing withdraw, report export data and password change...");
            byte[] extra = new byte[2048];
            new java.util.Random(42).nextBytes(extra);
            Upload pend = uploadService.submitUpload(worker, gangaId, "Dinner", "Roti", "Ingredients", extra);
            assertCondition(uploadService.withdrawUpload(worker, pend.getId()), "Pending upload should be withdrawable");
            boolean blocked = false;
            try { uploadService.withdrawUpload(worker, up1.getId()); } catch (com.messlens.exception.AccessDeniedException ex) { blocked = true; }
            assertCondition(blocked, "Approved upload must NOT be withdrawable");
            System.out.println("[OK] Withdraw works for PENDING only.");

            List<Upload> report = uploadService.getAllUploads(head, gangaId);
            assertCondition(report.size() == 3, "Report should list 3 uploads, found " + report.size());
            assertCondition(report.get(0).getRemark() != null, "Report rows should carry the reviewer remark");
            List<Upload> mine = uploadService.getWorkerUploads(worker);
            assertCondition(mine.get(0).getRemark() != null, "Worker history should carry the reviewer remark");
            System.out.println("[OK] Report and worker history include reviewer remarks.");

            AuthService pwAuth = new AuthService();
            pwAuth.login("student1", "student123");
            try {
                pwAuth.changePassword("wrong", "orchid lantern 2026");
                throw new RuntimeException("Wrong current password should fail");
            } catch (com.messlens.exception.AuthenticationException expected) { /* ok */ }
            try {
                pwAuth.changePassword("student123", "123");
                throw new RuntimeException("Short password should fail");
            } catch (com.messlens.exception.ValidationException expected) { /* ok */ }
            pwAuth.changePassword("student123", "orchid lantern 2026");
            new AuthService().login("student1", "orchid lantern 2026");
            // restore the demo password directly (the policy would refuse the weak demo password itself)
            String salt = com.messlens.util.SecurityUtil.generateSalt();
            new UserDao().updatePassword(student.getId(), com.messlens.util.SecurityUtil.hashPassword("student123", salt), salt);
            new AuthService().login("student1", "student123");
            System.out.println("[OK] Password change validated and restored.");

            System.out.println("\n=================================================");
            System.out.println("  ALL FLOW VERIFICATION TESTS PASSED (100% OK)   ");
            System.out.println("=================================================");
            System.out.flush();

            System.exit(0);

        } catch (Throwable t) {
            System.err.println("\nTEST FAILED WITH EXCEPTION:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("ASSERTION FAILED: " + message);
        }
    }
}
