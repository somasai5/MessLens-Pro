package com.messlens.model;

/**
 * Represents an approval or rejection decision logged by a Mess Head.
 */
public class Approval {
    private int id;
    private int uploadId;
    private int reviewerId;
    private String decision;   // APPROVED, REJECTED
    private String remark;
    private String decidedAt;

    public Approval() {}

    public Approval(int id, int uploadId, int reviewerId, String decision, String remark, String decidedAt) {
        this.id = id;
        this.uploadId = uploadId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.remark = remark;
        this.decidedAt = decidedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUploadId() {
        return uploadId;
    }

    public void setUploadId(int uploadId) {
        this.uploadId = uploadId;
    }

    public int getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(int reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(String decidedAt) {
        this.decidedAt = decidedAt;
    }
}

