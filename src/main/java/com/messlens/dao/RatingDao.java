package com.messlens.dao;

import com.messlens.model.MealRating;
import com.messlens.model.RatingSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Data access for meal ratings (one rating per student, per meal, per day). */
public class RatingDao {

    /** Saves a rating; rating the same meal again on the same day replaces the old one. */
    public void save(int userId, int hostelId, String date, String meal, int stars, String note) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            String update = "UPDATE meal_ratings SET stars = ?, note = ?, created_at = " + DatabaseManager.nowExpression(conn)
                    + " WHERE user_id = ? AND rating_date = ? AND meal = ?";
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setInt(1, stars);
                ps.setString(2, note);
                ps.setInt(3, userId);
                ps.setString(4, date);
                ps.setString(5, meal);
                if (ps.executeUpdate() > 0) return;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO meal_ratings (user_id, hostel_id, rating_date, meal, stars, note) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, userId);
                ps.setInt(2, hostelId);
                ps.setString(3, date);
                ps.setString(4, meal);
                ps.setInt(5, stars);
                ps.setString(6, note);
                ps.executeUpdate();
            }
        }
    }

    /** What this student has already rated on a day, by meal name. */
    public Map<String, MealRating> findForUserOnDate(int userId, String date) {
        Map<String, MealRating> map = new HashMap<>();
        String sql = "SELECT id, user_id, hostel_id, rating_date, meal, stars, note, created_at FROM meal_ratings "
                + "WHERE user_id = ? AND rating_date = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString("meal"), read(rs, null));
            }
        } catch (SQLException e) {
            System.err.println("Error loading ratings: " + e.getMessage());
        }
        return map;
    }

    /** Average stars and count per meal for a hostel, from a date onwards (GROUP BY meal). */
    public List<RatingSummary> summaryForHostel(int hostelId, String sinceDate) {
        List<RatingSummary> list = new ArrayList<>();
        String sql = "SELECT meal, AVG(stars) AS avg_stars, COUNT(*) AS cnt FROM meal_ratings "
                + "WHERE hostel_id = ? AND rating_date >= ? GROUP BY meal";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hostelId);
            ps.setString(2, sinceDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new RatingSummary(rs.getString("meal"), rs.getDouble("avg_stars"), rs.getInt("cnt")));
            }
        } catch (SQLException e) {
            System.err.println("Error loading rating summary: " + e.getMessage());
        }
        return list;
    }

    /** Latest ratings that have a written comment, newest first (JOIN with users for the name). */
    public List<MealRating> recentWithNotes(int hostelId, int limit) {
        List<MealRating> list = new ArrayList<>();
        String sql = "SELECT r.id, r.user_id, r.hostel_id, r.rating_date, r.meal, r.stars, r.note, r.created_at, "
                + "u.username, u.full_name FROM meal_ratings r JOIN users u ON u.id = r.user_id "
                + "WHERE r.hostel_id = ? AND r.note IS NOT NULL AND r.note <> '' ORDER BY r.created_at DESC, r.id DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hostelId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String full = rs.getString("full_name");
                    list.add(read(rs, full != null && !full.isBlank() ? full : rs.getString("username")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading comments: " + e.getMessage());
        }
        return list;
    }

    private static MealRating read(ResultSet rs, String name) throws SQLException {
        return new MealRating(rs.getInt("id"), rs.getInt("user_id"), rs.getInt("hostel_id"), rs.getString("rating_date"),
                rs.getString("meal"), rs.getInt("stars"), rs.getString("note"), rs.getString("created_at"), name);
    }
}
