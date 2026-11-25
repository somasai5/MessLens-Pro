package com.messlens.model;

/**
 * Represents an evidence photo upload made by a mess worker.
 * Contains meal information, dish name, stage, image path, image SHA-256 hash, and status.
 */
public class Upload {
    private int id;
    private int hostelId;
    private int workerId;
    private String meal;      // Breakfast, Lunch, Dinner
    private String dishName;
    private String stage;     // Ingredients, Cooking, Serving
    private String imagePath;
    private String imageHash;
    private String status;    // PENDING, APPROVED, REJECTED
    private String uploadedAt;

    // Supplementary display fields for UI tables and cards
    private String hostelName;
    private String workerUsername;
    private String approvedAt;
    private String remark;
    private String wardenAction; // CONFIRMED, OVERTURNED or null (not yet countersigned)

    private String qualityCheck;   // quality checklist answers, "key=value;key=value" (null for older photos)
    public String getQualityCheck() { return qualityCheck; }
    public void setQualityCheck(String q) { this.qualityCheck = q; }

    private String wardenRemark;   // the warden's written reason (required when overturning)
    private String wardenAt;       // when the warden reviewed it

    public String getWardenAction() { return wardenAction; }
    public void setWardenAction(String a) { this.wardenAction = a; }
    public String getWardenRemark() { return wardenRemark; }
    public void setWardenRemark(String r) { this.wardenRemark = r; }
    public String getWardenAt() { return wardenAt; }
    public void setWardenAt(String t) { this.wardenAt = t; }

    /** True when the warden reversed the mess head's decision. */
    public boolean isOverturned() { return "OVERTURNED".equalsIgnoreCase(wardenAction); }

    /**
     * What the mess head originally decided. The upload status holds the FINAL result, so when the warden
     * overturned it the head's decision was the opposite.
     */
    public String headDecision() {
        if (status == null) return null;
        String s = status.toUpperCase();
        if (!isOverturned()) return s;
        return "APPROVED".equals(s) ? "REJECTED" : "APPROVED";
    }

    public Upload() {}

    public Upload(int id, int hostelId, int workerId, String meal, String dishName,
                  String stage, String imagePath, String imageHash, String status, String uploadedAt) {
        this.id = id;
        this.hostelId = hostelId;
        this.workerId = workerId;
        this.meal = meal;
        this.dishName = dishName;
        this.stage = stage;
        this.imagePath = imagePath;
        this.imageHash = imageHash;
        this.status = status;
        this.uploadedAt = uploadedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getHostelId() {
        return hostelId;
    }

    public void setHostelId(int hostelId) {
        this.hostelId = hostelId;
    }

    public int getWorkerId() {
        return workerId;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public String getMeal() {
        return meal;
    }

    public void setMeal(String meal) {
        this.meal = meal;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getImageHash() {
        return imageHash;
    }

    public void setImageHash(String imageHash) {
        this.imageHash = imageHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getHostelName() {
        return hostelName;
    }

    public void setHostelName(String hostelName) {
        this.hostelName = hostelName;
    }

    public String getWorkerUsername() {
        return workerUsername;
    }

    public void setWorkerUsername(String workerUsername) {
        this.workerUsername = workerUsername;
    }

    public String getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(String approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

