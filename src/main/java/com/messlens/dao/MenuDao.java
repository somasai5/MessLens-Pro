package com.messlens.dao;

import com.messlens.model.MenuEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data access for the weekly menu and for the mess type each hostel serves. */
public class MenuDao {

    /** All 28 meals (7 days x 4 meals) of one mess type. */
    public List<MenuEntry> findByMessType(String messType) {
        List<MenuEntry> list = new ArrayList<>();
        String sql = "SELECT mess_type, day_no, meal, items FROM mess_menu WHERE mess_type = ? ORDER BY day_no, id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MenuEntry(rs.getString("mess_type"), rs.getInt("day_no"), rs.getString("meal"), rs.getString("items")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading menu: " + e.getMessage());
        }
        return list;
    }

    /** Changes the items of one meal (updates the row, or adds it if it does not exist yet). */
    public void upsert(String messType, int dayNo, String meal, String items, int userId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            String update = "UPDATE mess_menu SET items = ?, updated_by = ?, updated_at = " + DatabaseManager.nowExpression(conn)
                    + " WHERE mess_type = ? AND day_no = ? AND meal = ?";
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, items);
                ps.setInt(2, userId);
                ps.setString(3, messType);
                ps.setInt(4, dayNo);
                ps.setString(5, meal);
                if (ps.executeUpdate() > 0) return;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO mess_menu (mess_type, day_no, meal, items, updated_by) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, messType);
                ps.setInt(2, dayNo);
                ps.setString(3, meal);
                ps.setString(4, items);
                ps.setInt(5, userId);
                ps.executeUpdate();
            }
        }
    }

    /** The mess type (SOUTH / NORTH / INTERNATIONAL) a hostel serves. */
    public String getHostelMessType(int hostelId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT mess_type FROM hostels WHERE id = ?")) {
            ps.setInt(1, hostelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) return rs.getString(1);
            }
        } catch (SQLException e) {
            System.err.println("Error reading mess type: " + e.getMessage());
        }
        return "SOUTH";
    }

    public void setHostelMessType(int hostelId, String messType) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE hostels SET mess_type = ? WHERE id = ?")) {
            ps.setString(1, messType);
            ps.setInt(2, hostelId);
            ps.executeUpdate();
        }
    }
}
