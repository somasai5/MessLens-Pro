package com.messlens.service;

import com.messlens.audit.AuditEvent;
import com.messlens.audit.AuditLog;
import com.messlens.audit.AuditEntry;
import com.messlens.audit.VerifyResult;
import com.messlens.dao.UploadDao;
import com.messlens.exception.AccessDeniedException;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.security.AccessDecisionService;
import com.messlens.security.Action;
import com.messlens.security.Resource;
import com.messlens.util.PathUtil;
import com.messlens.util.SecurityUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Integrity Verification Procedures (Clark-Wilson "IVPs"): ways to prove, after the fact, that stored data is still
 * what the well-formed transactions wrote.
 *  - evidence check: every stored photo is re-hashed and compared with the SHA-256 recorded when it was uploaded;
 *  - audit check: the whole audit chain is recomputed (see AuditLog).
 */
public class IntegrityService {

    private final AccessDecisionService pdp = AccessDecisionService.INSTANCE;
    private final UploadDao uploadDao = new UploadDao();

    public static final class EvidenceReport {
        public int checked;
        public int intact;
        public final List<String> problems = new ArrayList<>();
        public boolean ok() { return problems.isEmpty(); }
    }

    public EvidenceReport verifyEvidence(User actor) throws AccessDeniedException {
        pdp.require(actor, Action.VERIFY_EVIDENCE, Resource.of("evidence"));
        EvidenceReport r = new EvidenceReport();
        for (Upload u : uploadDao.findAllForIntegrity()) {
            r.checked++;
            // tolerant lookup: photos saved on Windows ("evidence\\x.jpg") are found on macOS/Linux too
            File f = PathUtil.resolve(u.getImagePath());
            if (f == null) {
                r.problems.add("#" + u.getId() + " " + u.getDishName() + " (" + u.getHostelName() + "): the photo file is missing.");
                continue;
            }
            try {
                String now = SecurityUtil.calculateFileHash(f.toPath());
                if (now.equalsIgnoreCase(u.getImageHash())) r.intact++;
                else r.problems.add("#" + u.getId() + " " + u.getDishName() + " (" + u.getHostelName() + "): the photo file was changed after upload.");
            } catch (Exception e) {
                r.problems.add("#" + u.getId() + ": could not read the photo file (" + e.getMessage() + ").");
            }
        }
        AuditLog.logQuietly(AuditEvent.by(actor, "EVIDENCE_VERIFIED").resource("evidence", null)
                .outcome(r.ok() ? "OK" : "FAIL").detail(r.intact + "/" + r.checked + " intact"));
        return r;
    }

    public VerifyResult verifyAuditLog(User actor) throws AccessDeniedException {
        pdp.require(actor, Action.VERIFY_AUDIT_LOG, Resource.of("audit"));
        VerifyResult v = AuditLog.verify();
        AuditLog.logQuietly(AuditEvent.by(actor, "AUDIT_VERIFIED").resource("audit", null)
                .outcome(v.ok ? "OK" : "FAIL").detail(v.message));
        return v;
    }

    public List<AuditEntry> recentAudit(User actor, int limit) {
        if (!pdp.check(actor, Action.VIEW_AUDIT_LOG, Resource.of("audit")).permit) return new ArrayList<>();
        return AuditLog.recent(limit);
    }
}
