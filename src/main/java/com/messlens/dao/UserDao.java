package com.messlens.dao;

import com.messlens.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for User entities.
 * Uses PreparedStatements for all operations to prevent SQL injection.
 */
public class UserDao {

    private static final String COLS =
            "id, username, password_hash, salt, role, hostel_id, full_name, email, must_change_password";

    /** Case-insensitive: registration numbers can be typed in any case. */
    public User findByUsername(String username) {
        return one("SELECT " + COLS + " FROM users WHERE LOWER(username) = LOWER(?)", username);
    }

    public User findByEmail(String email) {
        return one("SELECT " + COLS + " FROM users WHERE LOWER(email) = LOWER(?)", email);
    }

    public User findById(int id) {
        String sql = "SELECT " + COLS + " FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user by id: " + e.getMessage());
        }
        return null;
    }

    private User one(String sql, String value) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user: " + e.getMessage());
        }
        return null;
    }

    /** Inserts a new account. Returns false when the insert failed (for example a duplicate username). */
    public boolean insert(User u) {
        String sql = "INSERT INTO users (username, password_hash, salt, role, hostel_id, full_name, email, must_change_password) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getPasswordHash());
            ps.setString(3, u.getSalt());
            ps.setString(4, u.getRole());
            if (u.getHostelId() != null) ps.setInt(5, u.getHostelId());
            else ps.setNull(5, java.sql.Types.INTEGER);
            ps.setString(6, u.getFullName());
            ps.setString(7, u.getEmail());
            ps.setInt(8, u.isMustChangePassword() ? 1 : 0);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
        }
        return false;
    }

    /** Users of the given roles, optionally limited to one hostel (null = all). */
    public List<User> findByRoles(Integer hostelId, String... roles) {
        List<User> list = new ArrayList<>();
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < roles.length; i++) in.append(i == 0 ? "?" : ",?");
        String sql = "SELECT " + COLS + " FROM users WHERE role IN (" + in + ")"
                + (hostelId != null ? " AND hostel_id = ?" : "") + " ORDER BY role, username";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String r : roles) ps.setString(i++, r);
            if (hostelId != null) ps.setInt(i, hostelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error listing users: " + e.getMessage());
        }
        return list;
    }

    public int countByRole(String role) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?")) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error counting users: " + e.getMessage());
        }
        return 0;
    }

    /** Normal password change: clears the "must change" flag. */
    public boolean updatePassword(int userId, String newHash, String newSalt) {
        return setPassword(userId, newHash, newSalt, false);
    }

    /** Re-stores the same password with a stronger hash (flag untouched). Used to upgrade old SHA-256 accounts. */
    public boolean rehashPassword(int userId, String newHash, String newSalt) {
        String sql = "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, newSalt);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error upgrading password hash: " + e.getMessage());
        }
        return false;
    }

    /** Sets a temporary password that the person must replace at next sign-in. */
    public boolean setTemporaryPassword(int userId, String newHash, String newSalt) {
        return setPassword(userId, newHash, newSalt, true);
    }

    private boolean setPassword(int userId, String newHash, String newSalt, boolean mustChange) {
        String sql = "UPDATE users SET password_hash = ?, salt = ?, must_change_password = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, newSalt);
            ps.setInt(3, mustChange ? 1 : 0);
            ps.setInt(4, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating password: " + e.getMessage());
        }
        return false;
    }

    /** True when the account has photos or decisions attached (removing it would erase that history). */
    public boolean hasActivity(int userId) {
        String sql = "SELECT (SELECT COUNT(*) FROM uploads WHERE worker_id = ?) + (SELECT COUNT(*) FROM approvals WHERE reviewer_id = ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking activity: " + e.getMessage());
        }
        return true; // when unsure, do not allow deletion
    }

    public boolean delete(int userId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
        }
        return false;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User(rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("salt"), rs.getString("role"),
                rs.getObject("hostel_id") != null ? rs.getInt("hostel_id") : null);
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setMustChangePassword(rs.getInt("must_change_password") == 1);
        return u;
    }
}
