package com.messlens.dao;

import com.messlens.audit.AuditEvent;
import com.messlens.audit.AuditLog;
import com.messlens.model.Approval;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Data Access Object for approvals and rejections recorded by Mess Heads.
 * Demonstrates explicit JDBC Transaction Management (setAutoCommit(false), commit, rollback)
 * as required by SRM CSE 307 Syllabus Unit 4 (JDBC Connectivity & Transactions).
 */
public class ApprovalDao {

    /**
     * Records an approval or rejection decision inside a single atomic ACID transaction.
     * Inserts the approval audit row and updates the upload status atomically.
     */
    public boolean recordDecisionAtomic(int uploadId, int reviewerId, String decision, String remark, AuditEvent audit) throws SQLException {
        String updateSql = "UPDATE uploads SET status = ? WHERE id = ? AND status = 'PENDING'";

        try (Connection conn = DatabaseManager.getConnection()) {
            // Syllabus Unit 4: Begin JDBC Transaction
            conn.setAutoCommit(false);
            String insertSql = "INSERT INTO approvals (upload_id, reviewer_id, decision, remark, decided_at) " +
                               "VALUES (?, ?, ?, ?, " + DatabaseManager.nowExpression(conn) + ")";

            AuditLog.Staged staged = null;
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql);
                 PreparedStatement psInsert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

                // 1. Update upload status
                psUpdate.setString(1, decision);
                psUpdate.setInt(2, uploadId);
                int updated = psUpdate.executeUpdate();
                if (updated == 0) {
                    throw new SQLException("Upload #" + uploadId + " is no longer in PENDING status.");
                }

                // 2. Insert approval audit record
                psInsert.setInt(1, uploadId);
                psInsert.setInt(2, reviewerId);
                psInsert.setString(3, decision);
                psInsert.setString(4, remark);
                psInsert.executeUpdate();

                // 3. Audit entry inside the SAME transaction (Clark-Wilson: no change without its log line)
                staged = AuditLog.append(conn, audit);

                // Commit transaction
                conn.commit();
                staged.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                // Rollback on any failure to maintain database consistency
                conn.rollback();
                if (staged != null) staged.abort();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Warden's second review: CONFIRM keeps the head's decision; OVERTURN flips it (APPROVED <-> REJECTED).
     * The review row, the status change and the audit entry commit together.
     */
    public boolean recordCountersignAtomic(int uploadId, int wardenId, String action, String remark,
                                           String expectedStatus, String newStatus, AuditEvent audit) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            AuditLog.Staged staged = null;
            try {
                if (!expectedStatus.equals(newStatus)) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE uploads SET status = ? WHERE id = ? AND status = ?")) {
                        ps.setString(1, newStatus);
                        ps.setInt(2, uploadId);
                        ps.setString(3, expectedStatus);
                        if (ps.executeUpdate() == 0) throw new SQLException("Upload #" + uploadId + " is no longer " + expectedStatus + ".");
                    }
                }
                String ins = "INSERT INTO warden_reviews (upload_id, warden_id, action, remark, decided_at) VALUES (?, ?, ?, ?, "
                        + DatabaseManager.nowExpression(conn) + ")";
                try (PreparedStatement ps = conn.prepareStatement(ins)) {
                    ps.setInt(1, uploadId);
                    ps.setInt(2, wardenId);
                    ps.setString(3, action);
                    ps.setString(4, remark);
                    ps.executeUpdate();
                }
                staged = AuditLog.append(conn, audit);
                conn.commit();
                staged.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                if (staged != null) staged.abort();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Legacy single-statement insert for standalone approval logging.
     */
    public boolean insert(Approval approval) {
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "INSERT INTO approvals (upload_id, reviewer_id, decision, remark, decided_at) " +
                         "VALUES (?, ?, ?, ?, " + DatabaseManager.nowExpression(conn) + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, approval.getUploadId());
            ps.setInt(2, approval.getReviewerId());
            ps.setString(3, approval.getDecision());
            ps.setString(4, approval.getRemark());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        approval.setId(rs.getInt(1));
                    }
                }
                return true;
            }
            }
        } catch (SQLException e) {
            System.err.println("Error inserting approval: " + e.getMessage());
        }
        return false;
    }
}
