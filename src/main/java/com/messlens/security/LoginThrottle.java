package com.messlens.security;

import com.messlens.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.LongSupplier;

/**
 * Online-guessing defence (NIST SP 800-63B 5.2.2 "rate limiting").
 *
 * The first FREE_ATTEMPTS wrong passwords for a username cost nothing; every further failure locks that
 * username for an exponentially growing time (1, 2, 4, 8, 16 minutes, capped at MAX_LOCK_MINUTES).
 * A correct sign-in resets the counter. State is kept in the database (table login_throttle), so closing
 * and reopening the app does not reset it, and unknown usernames are throttled too, so the lock does not
 * reveal which accounts exist.
 */
public final class LoginThrottle {

    public static final int FREE_ATTEMPTS = 4;
    public static final int MAX_LOCK_MINUTES = 30;

    private static LongSupplier clock = System::currentTimeMillis;

    private LoginThrottle() {}

    /** Test hook: replace the time source. */
    public static void setClock(LongSupplier c) { clock = c == null ? System::currentTimeMillis : c; }

    /** @return seconds the username is still locked, or 0 when attempts are allowed. */
    public static long secondsLocked(String username) {
        String key = key(username);
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT locked_until FROM login_throttle WHERE username_lc = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long left = rs.getLong(1) - clock.getAsLong();
                    return left > 0 ? (left + 999) / 1000 : 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Throttle lookup failed: " + e.getMessage());
        }
        return 0;
    }

    /** Records a wrong password; returns the lock (in seconds) now in force, or 0. */
    public static long recordFailure(String username) {
        String key = key(username);
        long now = clock.getAsLong();
        try (Connection conn = DatabaseManager.getConnection()) {
            int failures = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT failures FROM login_throttle WHERE username_lc = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) failures = rs.getInt(1);
                }
            }
            failures++;
            long lockMs = lockMillis(failures);
            long until = lockMs > 0 ? now + lockMs : 0;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM login_throttle WHERE username_lc = ?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO login_throttle (username_lc, failures, locked_until) VALUES (?, ?, ?)")) {
                ps.setString(1, key);
                ps.setInt(2, failures);
                ps.setLong(3, until);
                ps.executeUpdate();
            }
            return lockMs / 1000;
        } catch (SQLException e) {
            System.err.println("Throttle update failed: " + e.getMessage());
        }
        return 0;
    }

    public static void recordSuccess(String username) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM login_throttle WHERE username_lc = ?")) {
            ps.setString(1, key(username));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Throttle reset failed: " + e.getMessage());
        }
    }

    /** Lock length after the n-th consecutive failure (pure function, used by tests and the evaluation). */
    public static long lockMillis(int failures) {
        if (failures <= FREE_ATTEMPTS) return 0;
        long minutes = 1L << Math.min(failures - FREE_ATTEMPTS - 1, 10);
        return Math.min(minutes, MAX_LOCK_MINUTES) * 60_000L;
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
