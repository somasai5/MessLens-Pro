package com.messlens.security;

import com.messlens.audit.AuditEvent;
import com.messlens.audit.AuditLog;
import com.messlens.audit.VerifyResult;
import com.messlens.dao.DatabaseManager;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Random;

/** Tamper-evidence of the audit log: honest logs verify, and every kind of tampering is detected. */
public class AuditChainTest {

    static final int ENTRIES = 60;
    static final int TRIALS = 25;

    public static void main(String[] args) throws Exception {
        TestKit.section("Audit log: honest operation");
        build(ENTRIES);
        VerifyResult v = AuditLog.verify();
        TestKit.check("untouched log verifies (" + v.message + ")", v.ok && v.macChecked && v.entries == ENTRIES + 0);

        // restart: a new process re-reads key state and continues the same chain
        AuditLog.reset();
        AuditLog.log(AuditEvent.by(null, "AFTER_RESTART"));
        v = AuditLog.verify();
        TestKit.check("chain continues correctly after a restart", v.ok && v.entries == ENTRIES + 1);

        // rollback: an aborted transaction must not burn a sequence number
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            AuditLog.Staged st = AuditLog.append(c, AuditEvent.by(null, "WILL_ROLL_BACK"));
            c.rollback();
            st.abort();
        }
        AuditLog.log(AuditEvent.by(null, "AFTER_ROLLBACK"));
        v = AuditLog.verify();
        TestKit.check("rolled-back entry leaves no gap", v.ok && v.entries == ENTRIES + 2);

        // crash between 'row written' and 'key advanced': recovery on next start
        try (Connection c = DatabaseManager.getConnection()) {
            AuditLog.Staged st = AuditLog.append(c, AuditEvent.by(null, "CRASH_BEFORE_KEY_ADVANCE"));
            // simulate a crash: row is in the database, key state was never advanced, JVM dies (lock freed by abort)
            st.abort();
        }
        AuditLog.reset();
        AuditLog.log(AuditEvent.by(null, "AFTER_CRASH_RECOVERY"));
        v = AuditLog.verify();
        TestKit.check("recovers from a crash between row write and key advance", v.ok);

        TestKit.section("Audit log: attacks (" + TRIALS + " trials each)");
        for (var e : attacks().entrySet()) attack(e.getKey(), e.getValue());

        TestKit.summary("AuditChainTest");
    }

    /** The attacker models, in the order they are reported. */
    static java.util.LinkedHashMap<String, Tamper> attacks() {
        java.util.LinkedHashMap<String, Tamper> m = new java.util.LinkedHashMap<>();
        m.put("edit one field of a random entry", (stmt, r, n) ->
                stmt.execute("UPDATE audit_log SET detail = 'forged' WHERE seq = " + (1 + r.nextInt(n))));
        m.put("delete a random entry from the middle", (stmt, r, n) ->
                stmt.execute("DELETE FROM audit_log WHERE seq = " + (2 + r.nextInt(n - 2))));
        m.put("delete the newest entry", (stmt, r, n) ->
                stmt.execute("DELETE FROM audit_log WHERE seq = " + n));
        m.put("delete the newest 5 entries", (stmt, r, n) ->
                stmt.execute("DELETE FROM audit_log WHERE seq > " + (n - 5)));
        m.put("empty the whole log", (stmt, r, n) -> stmt.execute("DELETE FROM audit_log"));
        m.put("swap the content of two entries", (stmt, r, n) -> {
            int a = 1 + r.nextInt(n - 1), b = a + 1;
            stmt.execute("UPDATE audit_log SET action = 'X' WHERE seq = " + a);
            stmt.execute("UPDATE audit_log SET action = 'LOGIN_SUCCESS' WHERE seq = " + b);
        });
        m.put("append a forged entry with a made-up hash", (stmt, r, n) ->
                stmt.execute("INSERT INTO audit_log (seq, ts, action, outcome, detail, prev_hash, entry_hash) VALUES ("
                        + (n + 1) + ", '2026-01-01T00:00:00Z', 'FORGED', 'OK', 'x', '" + "a".repeat(64) + "', '" + "b".repeat(64) + "')"));
        m.put("edit an entry and recompute every later hash with plain SHA-256 (no key)", (stmt, r, n) -> {
            int k = 1 + r.nextInt(n);
            stmt.execute("UPDATE audit_log SET detail = 'forged' WHERE seq = " + k);
            recomputeWithSha256(stmt, k, n);
        });
        m.put("insider who holds the CURRENT key re-MACs the edited entry", (stmt, r, n) -> {
            int k = 1 + r.nextInt(n - 1);
            stmt.execute("UPDATE audit_log SET detail = 'forged' WHERE seq = " + k);
            recomputeWithCurrentKey(stmt, k, n);
        });
        m.put("delete the key state file after deleting the newest entry", (stmt, r, n) -> {
            stmt.execute("DELETE FROM audit_log WHERE seq = " + n);
            new File("audit_test/audit_state.key").delete();
        });
        return m;
    }

    static int detected(Tamper t, int trials) throws Exception {
        Random r = new Random(42);
        int detected = 0;
        for (int i = 0; i < trials; i++) {
            build(ENTRIES);
            try (Connection c = DatabaseManager.getConnection(); Statement s = c.createStatement()) {
                t.apply(s, r, ENTRIES);
            }
            if (!AuditLog.verify().ok) detected++;
        }
        return detected;
    }

    interface Tamper { void apply(Statement s, Random r, int n) throws Exception; }

    static void build(int n) throws Exception {
        TestKit.freshEnvironment();
        for (int i = 1; i <= n; i++) {
            AuditLog.log(AuditEvent.anonymous("user" + i, i % 3 == 0 ? "LOGIN_FAILED" : "LOGIN_SUCCESS").detail("event " + i));
        }
    }

    static void attack(String name, Tamper t) throws Exception {
        int detected = detected(t, TRIALS);
        TestKit.check(name + "  -> detected " + detected + "/" + TRIALS, detected == TRIALS);
    }

    // ---- attacker models ---------------------------------------------------------------------

    static void recomputeWithSha256(Statement s, int from, int n) throws Exception {
        var rs = s.executeQuery("SELECT seq, ts, actor_id, actor_username, actor_role, action, resource_type, resource_id, hostel_id, outcome, detail FROM audit_log WHERE seq >= " + from + " ORDER BY seq");
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        while (rs.next()) {
            Object[] o = new Object[11];
            for (int i = 0; i < 11; i++) o[i] = rs.getObject(i + 1);
            rows.add(o);
        }
        rs.close();
        String prev = from == 1 ? "0".repeat(64) : null;
        if (prev == null) {
            var p = s.executeQuery("SELECT entry_hash FROM audit_log WHERE seq = " + (from - 1));
            p.next(); prev = p.getString(1); p.close();
        }
        for (Object[] o : rows) {
            String canon = String.join("\u001f", str(o[0]), str(o[1]), str(o[2]), str(o[3]), str(o[4]), str(o[5]),
                    str(o[6]), str(o[7]), str(o[8]), str(o[9]), str(o[10]));
            String h = com.messlens.util.SecurityUtil.sha256Hex(prev + "\n" + canon);
            s.execute("UPDATE audit_log SET prev_hash = '" + prev + "', entry_hash = '" + h + "' WHERE seq = " + o[0]);
            prev = h;
        }
    }

    static void recomputeWithCurrentKey(Statement s, int k, int n) throws Exception {
        // the attacker read audit_state.key (current key A_n) and tries to repair the chain with it
        String keyHex = null;
        for (String line : Files.readAllLines(new File("audit_test/audit_state.key").toPath())) {
            if (line.startsWith("key=")) keyHex = line.substring(4).trim();
        }
        byte[] key = com.messlens.util.SecurityUtil.hexToBytes(keyHex);
        var rs = s.executeQuery("SELECT seq, ts, actor_id, actor_username, actor_role, action, resource_type, resource_id, hostel_id, outcome, detail, prev_hash FROM audit_log WHERE seq = " + k);
        rs.next();
        Object[] o = new Object[12];
        for (int i = 0; i < 12; i++) o[i] = rs.getObject(i + 1);
        rs.close();
        String canon = String.join("\u001f", str(o[0]), str(o[1]), str(o[2]), str(o[3]), str(o[4]), str(o[5]),
                str(o[6]), str(o[7]), str(o[8]), str(o[9]), str(o[10]));
        javax.crypto.Mac m = javax.crypto.Mac.getInstance("HmacSHA256");
        m.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        String h = com.messlens.util.SecurityUtil.bytesToHex(m.doFinal((o[11] + "\n" + canon).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        s.execute("UPDATE audit_log SET entry_hash = '" + h + "' WHERE seq = " + k);
    }

    static String str(Object o) { return o == null ? "" : o.toString(); }
}
