package com.messlens.model;

/** A student's complaint to the mess head, and the reply once there is one. */
public class Complaint {
    private final int id;
    private final int studentId;
    private final int hostelId;
    private final String studentName;
    private final String category;
    private final String meal;
    private final String description;
    private final String status;
    private final String reply;
    private final String createdAt;
    private final String repliedAt;
    private final String imagePath;   // optional photo the student attached (null when none)

    public Complaint(int id, int studentId, int hostelId, String studentName, String category, String meal, String description, String status, String reply, String createdAt, String repliedAt) {
        this(id, studentId, hostelId, studentName, category, meal, description, status, reply, createdAt, repliedAt, null);
    }

    public Complaint(int id, int studentId, int hostelId, String studentName, String category, String meal, String description, String status, String reply, String createdAt, String repliedAt, String imagePath) {
        this.id = id;
        this.studentId = studentId;
        this.hostelId = hostelId;
        this.studentName = studentName;
        this.category = category;
        this.meal = meal;
        this.description = description;
        this.status = status;
        this.reply = reply;
        this.createdAt = createdAt;
        this.repliedAt = repliedAt;
        this.imagePath = imagePath;
    }

    public int getId() { return id; }
    public int getStudentId() { return studentId; }
    public int getHostelId() { return hostelId; }
    public String getStudentName() { return studentName; }
    public String getCategory() { return category; }
    public String getMeal() { return meal; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getReply() { return reply; }
    public String getCreatedAt() { return createdAt; }
    public String getRepliedAt() { return repliedAt; }
    public String getImagePath() { return imagePath; }
    public boolean hasImage() { return imagePath != null && !imagePath.isBlank(); }
}
