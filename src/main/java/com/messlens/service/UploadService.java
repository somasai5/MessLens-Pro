package com.messlens.service;

import com.messlens.audit.AuditEvent;
import com.messlens.audit.AuditLog;
import com.messlens.dao.UploadDao;
import com.messlens.exception.DuplicateEvidenceException;
import com.messlens.exception.MessLensException;
import com.messlens.exception.ValidationException;
import com.messlens.model.MessMetrics;
import com.messlens.model.QualityChecklist;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.security.AccessDecisionService;
import com.messlens.security.Action;
import com.messlens.security.Decision;
import com.messlens.security.Resource;
import com.messlens.util.Config;
import com.messlens.util.SecurityUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Evidence uploads, file persistence into ./evidence/, SHA-256 duplicate detection and record retrieval.
 *
 * Every method takes the signed-in user and asks the Policy Decision Point first (NIST SP 800-162).
 * Writes throw AccessDeniedException; reads return nothing when denied, so a screen can never show data
 * the policy does not allow. State-changing methods write their audit entry in the same transaction.
 */
public class UploadService {

    private final UploadDao uploadDao;
    private final AccessDecisionService pdp = AccessDecisionService.INSTANCE;

    public UploadService() {
        this.uploadDao = new UploadDao();
    }

    public UploadService(UploadDao uploadDao) {
        this.uploadDao = uploadDao;
    }

    /**
     * Submits a new evidence upload from worker capture: hash, duplicate check, save file, insert row (PENDING) + audit entry.
     */
    public Upload submitUpload(User actor, int hostelId, String meal, String dishName,
                               String stage, byte[] imageBytes) throws MessLensException, IOException {
        return submitUpload(actor, hostelId, meal, dishName, stage, imageBytes, null);
    }

    /** Same, with the quality checklist answers ("key=value;..." from {@link QualityChecklist#encode}); null = none recorded. */
    public Upload submitUpload(User actor, int hostelId, String meal, String dishName,
                               String stage, byte[] imageBytes, String qualityCheck) throws MessLensException, IOException {
        pdp.require(actor, Action.UPLOAD_PHOTO, Resource.of("upload").hostelId(hostelId).owner(actor == null ? null : actor.getId()));

        if (dishName == null || dishName.trim().isEmpty()) throw new ValidationException("Dish name is required.");
        if (meal == null || meal.trim().isEmpty()) throw new ValidationException("Meal type is required.");
        if (stage == null || stage.trim().isEmpty()) throw new ValidationException("Stage is required.");
        if (imageBytes == null || imageBytes.length == 0) throw new ValidationException("Captured image is missing or empty.");
        if (qualityCheck != null) {
            String bad = QualityChecklist.validate(stage.trim(), QualityChecklist.decode(qualityCheck));
            if (bad != null) throw new ValidationException(bad);
        }

        String hash = SecurityUtil.calculateBytesHash(imageBytes);
        if (uploadDao.existsByImageHash(hash)) {
            throw new DuplicateEvidenceException("Duplicate photo detected: this exact image has already been uploaded as evidence.", hash);
        }

        Config.ensureEvidenceDirExists();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = String.format("evidence_%s_%s.jpg", timeStamp, UUID.randomUUID().toString().substring(0, 8));
        File targetFile = new File(Config.getEvidenceDir(), filename);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(imageBytes);
            fos.flush();
        } catch (IOException e) {
            throw new IOException("Failed to save image to evidence folder: " + e.getMessage(), e);
        }

        Upload upload = new Upload();
        upload.setHostelId(hostelId);
        upload.setWorkerId(actor.getId());
        upload.setMeal(meal.trim());
        upload.setDishName(dishName.trim());
        upload.setStage(stage.trim());
        upload.setImagePath(targetFile.getPath());
        upload.setImageHash(hash);
        upload.setStatus("PENDING");
        upload.setQualityCheck(qualityCheck);

        boolean inserted = uploadDao.insert(upload, id -> AuditEvent.by(actor, "UPLOAD_SUBMITTED")
                .resource("upload", id).hostel(hostelId)
                .detail(upload.getMeal() + " / " + upload.getDishName() + " / " + upload.getStage() + " sha256=" + hash));
        if (!inserted) {
            targetFile.delete();
            String why = uploadDao.getLastError();
            throw new MessLensException("Failed to insert upload record into database."
                    + (why == null || why.isBlank() ? "" : " Reason: " + why));
        }
        return upload;
    }

    /** Lets kitchen staff take back their own mistaken submission while it is still PENDING. */
    public boolean withdrawUpload(User actor, int uploadId) throws MessLensException {
        Upload up = uploadDao.findById(uploadId);
        if (up == null) return false;
        pdp.require(actor, Action.WITHDRAW_PHOTO,
                Resource.of("upload").id(uploadId).hostelId(up.getHostelId()).owner(up.getWorkerId()).status(up.getStatus()));
        String path = uploadDao.deletePendingByWorker(uploadId, actor.getId(), id -> AuditEvent.by(actor, "UPLOAD_WITHDRAWN")
                .resource("upload", id).hostel(up.getHostelId())
                .detail(up.getDishName() + " / " + up.getStage() + " sha256=" + up.getImageHash()));
        if (path == null) return false;
        java.io.File found = com.messlens.util.PathUtil.resolve(path);
        if (found != null) found.delete();
        return true;
    }

    // ------------------------------------------------------------------ reads (deny = empty result)

    private boolean mayRead(User actor, Action action, Resource resource) {
        Decision d = pdp.check(actor, action, resource);
        if (!d.permit) {
            AuditLog.logQuietly(AuditEvent.by(actor, "ACCESS_DENIED").resource(resource.type, resource.id)
                    .hostel(resource.hostelId).outcome("DENY").detail(action + " blocked by " + d.ruleId));
        }
        return d.permit;
    }

    /** Every record of a mess (mess head or warden of that mess). */
    public List<Upload> getAllUploads(User actor, int hostelId) {
        if (!mayRead(actor, Action.VIEW_HOSTEL_RECORDS, Resource.hostel(hostelId))) return new ArrayList<>();
        return uploadDao.findAllByHostelId(hostelId);
    }

    /** The signed-in kitchen staff member's own submissions. */
    public List<Upload> getWorkerUploads(User actor) {
        if (actor == null || !mayRead(actor, Action.VIEW_OWN_SUBMISSIONS, Resource.of("upload").owner(actor.getId()))) return new ArrayList<>();
        return uploadDao.findByWorkerId(actor.getId());
    }

    public List<Upload> getPendingUploads(User actor, int hostelId) {
        if (!mayRead(actor, Action.VIEW_REVIEW_QUEUE, Resource.hostel(hostelId))) return new ArrayList<>();
        return uploadDao.findPendingByHostelId(hostelId);
    }

    public List<Upload> getApprovedTimeline(User actor, int hostelId, String date, String meal) {
        if (!mayRead(actor, Action.VIEW_VERIFIED_PHOTOS, Resource.hostel(hostelId))) return new ArrayList<>();
        return uploadDao.findApprovedByFilters(hostelId, date, meal);
    }

    public MessMetrics getHostelMetrics(User actor, int hostelId) {
        if (!mayRead(actor, Action.VIEW_HOSTEL_METRICS, Resource.hostel(hostelId))) return new MessMetrics(0, 0, 0, 0, 0);
        return uploadDao.getMetricsForHostel(hostelId);
    }
}
