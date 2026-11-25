package com.messlens.model;

/** One student's star rating (1-5) for one meal on one day. */
public class MealRating {
    private final int id;
    private final int userId;
    private final int hostelId;
    private final String ratingDate;
    private final String meal;
    private final int stars;
    private final String note;
    private final String createdAt;
    private final String studentName;

    public MealRating(int id, int userId, int hostelId, String ratingDate, String meal, int stars, String note, String createdAt, String studentName) {
        this.id = id;
        this.userId = userId;
        this.hostelId = hostelId;
        this.ratingDate = ratingDate;
        this.meal = meal;
        this.stars = stars;
        this.note = note;
        this.createdAt = createdAt;
        this.studentName = studentName;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public int getHostelId() { return hostelId; }
    public String getRatingDate() { return ratingDate; }
    public String getMeal() { return meal; }
    public int getStars() { return stars; }
    public String getNote() { return note; }
    public String getCreatedAt() { return createdAt; }
    public String getStudentName() { return studentName; }
}
