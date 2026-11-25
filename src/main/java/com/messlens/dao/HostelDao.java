package com.messlens.dao;

import com.messlens.model.Hostel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Hostel entities.
 */
public class HostelDao {

    public List<Hostel> findAll() {
        List<Hostel> hostels = new ArrayList<>();
        String sql = "SELECT id, name FROM hostels ORDER BY name ASC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                hostels.add(new Hostel(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all hostels: " + e.getMessage());
        }
        return hostels;
    }

    public Hostel findById(int id) {
        String sql = "SELECT id, name FROM hostels WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Hostel(rs.getInt("id"), rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching hostel by id: " + e.getMessage());
        }
        return null;
    }
}

