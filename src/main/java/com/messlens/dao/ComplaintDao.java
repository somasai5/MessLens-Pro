package com.messlens.dao;

import com.messlens.model.Complaint;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data access for student complaints and the mess head's replies. */
public class ComplaintDao {

    private static final String SELECT = "SELECT c.id, c.student_id, c.hostel_id, u.username, u.full_name, c.category, c.meal, "
            + "c.description, c.status, c.reply, c.created_at, c.replied_at, c.image_path FROM complaints c JOIN users u ON u.id = c.student_id ";

    public void insert(int studentId, int hostelId, String category, String meal, String description) throws SQLException {
        insert(studentId, hostelId, category, meal, description, null);
    }

    public void insert(int studentId, int hostelId, String category, String meal, String description, String imagePath) throws SQLException {
        String sql = "INSERT INTO complaints (student_id, hostel_id, category, meal, description, status, image_path) VALUES (?, ?, ?, ?, ?, 'OPEN', ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, hostelId);
            ps.setString(3, category);
            ps.setString(4, meal);
            ps.setString(5, description);
            ps.setString(6, imagePath);
            ps.executeUpdate();
        }
    }

    public List<Complaint> findByStudent(int studentId) {
        return query(SELECT + "WHERE c.student_id = ? ORDER BY c.created_at DESC, c.id DESC", studentId);
    }

    public List<Complaint> findByHostel(int hostelId) {
        return query(SELECT + "WHERE c.hostel_id = ? ORDER BY c.created_at DESC, c.id DESC", hostelId);
    }

    public Complaint findById(int id) {
        List<Complaint> list = query(SELECT + "WHERE c.id = ?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Writes the reply and marks the complaint RESOLVED. */
    public void reply(int id, String reply, int repliedBy) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "UPDATE complaints SET reply = ?, replied_by = ?, status = 'RESOLVED', replied_at = "
                    + DatabaseManager.nowExpression(conn) + " WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, reply);
                ps.setInt(2, repliedBy);
                ps.setInt(3, id);
                ps.executeUpdate();
            }
        }
    }

    /** The mess head has read it and needs no reply: OPEN becomes READ (a reply can still be added later). */
    public int markRead(int id) throws SQLException {
        String sql = "UPDATE complaints SET status = 'READ' WHERE id = ? AND status = 'OPEN'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    /** Deletes a complaint, but only the student's own and only while it is still OPEN. Returns how many rows were deleted. */
    public int deleteOpen(int id, int studentId) throws SQLException {
        String sql = "DELETE FROM complaints WHERE id = ? AND student_id = ? AND status = 'OPEN'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, studentId);
            return ps.executeUpdate();
        }
    }

    public int countOpen(int hostelId) {
        String sql = "SELECT COUNT(*) FROM complaints WHERE hostel_id = ? AND status = 'OPEN'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, hostelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error counting complaints: " + e.getMessage());
        }
        return 0;
    }

    private List<Complaint> query(String sql, int param) {
        List<Complaint> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String full = rs.getString("full_name");
                    String name = full != null && !full.isBlank() ? full : rs.getString("username");
                    list.add(new Complaint(rs.getInt("id"), rs.getInt("student_id"), rs.getInt("hostel_id"), name,
                            rs.getString("category"), rs.getString("meal"), rs.getString("description"),
                            rs.getString("status"), rs.getString("reply"), rs.getString("created_at"), rs.getString("replied_at"), rs.getString("image_path")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading complaints: " + e.getMessage());
        }
        return list;
    }
}
