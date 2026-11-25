package com.messlens.service;

import com.messlens.audit.AuditEvent;
import com.messlens.dao.ApprovalDao;
import com.messlens.dao.UploadDao;
import com.messlens.exception.MessLensException;
import com.messlens.exception.ValidationException;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.security.AccessDecisionService;
import com.messlens.security.Action;
import com.messlens.security.Resource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The review chain: kitchen staff photo (PENDING) -> mess head decision (APPROVED / REJECTED) -> warden countersign.
 *
 * Clark and Wilson (1987): data changes only through these well-formed transactions; the uploader can
 * never review their own photo (separation of duties, rule D-SOD); every transaction writes its audit entry
 * in the same database transaction. Who may do what is decided by the Policy Decision Point, never by the screen.
 */
public class ApprovalService {

    private final ApprovalDao approvalDao;
    private final UploadDao uploadDao;
    private final AccessDecisionService pdp = AccessDecisionService.INSTANCE;

    public ApprovalService() {
        this.approvalDao = new ApprovalDao();
        this.uploadDao = new UploadDao();
    }

    public ApprovalService(ApprovalDao approvalDao, UploadDao uploadDao) {
        this.approvalDao = approvalDao;
        this.uploadDao = uploadDao;
    }

    /** Mess head: approve or reject a pending photo of their own mess. Rejection needs a remark. */
    public boolean reviewUpload(User actor, int uploadId, String decision, String remark) throws MessLensException {
        if (!"APPROVED".equalsIgnoreCase(decision) && !"REJECTED".equalsIgnoreCase(decision)) {
            throw new ValidationException("Decision must be either APPROVED or REJECTED.");
        }
        String normalized = decision.toUpperCase();

        Upload up = uploadDao.findById(uploadId);
        if (up == null) throw new ValidationException("That photo no longer exists.");

        pdp.require(actor, "APPROVED".equals(normalized) ? Action.APPROVE_PHOTO : Action.REJECT_PHOTO,
                Resource.of("upload").id(uploadId).hostelId(up.getHostelId()).owner(up.getWorkerId()).status(up.getStatus()));

        if ("REJECTED".equals(normalized) && (remark == null || remark.trim().isEmpty())) {
            throw new ValidationException("A remark is required when rejecting an evidence upload.");
        }
        String clean = remark != null ? remark.trim() : "";

        AuditEvent ev = AuditEvent.by(actor, "DECISION_" + normalized).resource("upload", uploadId).hostel(up.getHostelId())
                .detail(up.getDishName() + " / " + up.getStage() + " sha256=" + up.getImageHash()
                        + (clean.isEmpty() ? "" : " remark=" + clean));
        try {
            // Unit 4: JDBC transaction (approval row + status change + audit entry commit together)
            return approvalDao.recordDecisionAtomic(uploadId, actor.getId(), normalized, clean, ev);
        } catch (java.sql.SQLException e) {
            throw new MessLensException("Failed to record approval decision transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Warden: second review of a decided photo.
     * @param action "CONFIRM" keeps the mess head's decision, "OVERTURN" reverses it (reason required, within the review window)
     */
    public boolean countersign(User actor, int uploadId, String action, String remark) throws MessLensException {
        boolean overturn = "OVERTURN".equalsIgnoreCase(action);
        if (!overturn && !"CONFIRM".equalsIgnoreCase(action)) throw new ValidationException("Action must be CONFIRM or OVERTURN.");

        Upload up = uploadDao.findById(uploadId);
        if (up == null) throw new ValidationException("That photo no longer exists.");

        pdp.require(actor, overturn ? Action.OVERTURN_DECISION : Action.CONFIRM_DECISION,
                Resource.of("upload").id(uploadId).hostelId(up.getHostelId()).owner(up.getWorkerId())
                        .status(up.getStatus()).decidedAt(parse(up.getApprovedAt()))
                        .countersigned(up.getWardenAction() != null));

        String clean = remark == null ? "" : remark.trim();
        if (overturn && clean.isEmpty()) throw new ValidationException("Give a reason when you overturn a decision.");

        String from = up.getStatus().toUpperCase();
        String to = overturn ? ("APPROVED".equals(from) ? "REJECTED" : "APPROVED") : from;
        AuditEvent ev = AuditEvent.by(actor, overturn ? "WARDEN_OVERTURNED" : "WARDEN_CONFIRMED")
                .resource("upload", uploadId).hostel(up.getHostelId())
                .detail(up.getDishName() + " / " + up.getStage() + " " + from + (overturn ? " -> " + to : " confirmed")
                        + (clean.isEmpty() ? "" : " reason=" + clean));
        try {
            return approvalDao.recordCountersignAtomic(uploadId, actor.getId(), overturn ? "OVERTURNED" : "CONFIRMED",
                    clean, from, to, ev);
        } catch (java.sql.SQLException e) {
            throw new MessLensException("Could not record the warden review: " + e.getMessage(), e);
        }
    }

    /** Database local time "yyyy-MM-dd HH:mm:ss" to an Instant (null when unknown). */
    public static Instant parse(String dbTime) {
        if (dbTime == null || dbTime.isBlank()) return null;
        try {
            return LocalDateTime.parse(dbTime.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
