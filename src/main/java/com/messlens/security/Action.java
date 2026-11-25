package com.messlens.security;

/** Every operation a screen can ask for. The Policy Decision Point answers PERMIT or DENY for each. */
public enum Action {
    UPLOAD_PHOTO,
    WITHDRAW_PHOTO,
    VIEW_OWN_SUBMISSIONS,
    VIEW_REVIEW_QUEUE,
    APPROVE_PHOTO,
    REJECT_PHOTO,
    VIEW_HOSTEL_RECORDS,
    EXPORT_RECORDS,
    VIEW_VERIFIED_PHOTOS,
    VIEW_HOSTEL_METRICS,
    CONFIRM_DECISION,
    OVERTURN_DECISION,
    CREATE_STAFF,
    MANAGE_STAFF,
    VIEW_STAFF,
    VIEW_ADMIN_DASHBOARD,
    VIEW_AUDIT_LOG,
    VERIFY_AUDIT_LOG,
    VERIFY_EVIDENCE
}
