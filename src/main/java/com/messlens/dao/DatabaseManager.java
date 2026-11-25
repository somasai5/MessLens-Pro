package com.messlens.dao;

import com.messlens.util.Config;
import com.messlens.util.SecurityUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database access for MessLens. Two engines are supported, chosen by DB_TYPE in config.properties:
 *   mysql  - one shared MySQL server that every computer connects to (default, used for the project)
 *   sqlite - a single local file, handy for a quick test without installing anything
 */
public class DatabaseManager {

    /** Opens a connection to the configured database (MySQL server or local SQLite file). */
    public static Connection getConnection() throws SQLException {
        if (Config.isMySql()) {
            return DriverManager.getConnection(Config.getMysqlUrl(), Config.getMysqlUser(), Config.getMysqlPassword());
        }
        Connection conn = DriverManager.getConnection(Config.getSqliteUrl());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        } catch (SQLException ignored) {}
        return conn;
    }

    /** The "current local time" SQL expression for the active database. */
    public static String nowExpression(Connection conn) {
        return Config.isMySql() ? "NOW()" : "datetime('now', 'localtime')";
    }

    /** Auto-numbered primary key column, written for the active database. */
    private static String idColumn() {
        return Config.isMySql() ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    /**
     * Initializes the database schema and seeds initial records on first run.
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection()) {
            createTables(conn);
            createSecurityTables(conn);
            migrateUsersTable(conn);
            seedHostels(conn);
            seedUsers(conn);
            seedAdmin(conn);
            seedDemoWarden(conn);
            createFeatureTables(conn);
            addColumnIfMissing(conn, "complaints", "image_path", "VARCHAR(500)");   // optional photo with a complaint
            addColumnIfMissing(conn, "uploads", "quality_check", "TEXT");          // quality checklist answers saved with the photo
            seedMenu(conn);
            linkDemoStudent(conn);
            System.out.println("Database successfully initialized (" + (Config.isMySql() ? "MySQL" : "SQLite") + ").");
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            if (Config.isMySql()) {
                System.err.println("Is MySQL running? Check MYSQL_URL, MYSQL_USER and MYSQL_PASSWORD in config.properties "
                        + "(or set DB_TYPE=sqlite to use a local file instead).");
            }
            e.printStackTrace();
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Tables are written so they work on both MySQL and SQLite
            stmt.execute("CREATE TABLE IF NOT EXISTS hostels (" +
                    "id " + idColumn() + ", " +
                    "name VARCHAR(100) UNIQUE NOT NULL" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id " + idColumn() + ", " +
                    "username VARCHAR(100) UNIQUE NOT NULL, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "salt VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(30) NOT NULL, " +
                    "hostel_id INTEGER, " +
                    "FOREIGN KEY(hostel_id) REFERENCES hostels(id)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS uploads (" +
                    "id " + idColumn() + ", " +
                    "hostel_id INTEGER NOT NULL, " +
                    "worker_id INTEGER NOT NULL, " +
                    "meal VARCHAR(30) NOT NULL, " +
                    "dish_name VARCHAR(150) NOT NULL, " +
                    "stage VARCHAR(30) NOT NULL, " +
                    "image_path VARCHAR(500) NOT NULL, " +
                    "image_hash VARCHAR(128) UNIQUE NOT NULL, " +
                    "status VARCHAR(20) NOT NULL, " +
                    "uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY(hostel_id) REFERENCES hostels(id), " +
                    "FOREIGN KEY(worker_id) REFERENCES users(id)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS approvals (" +
                    "id " + idColumn() + ", " +
                    "upload_id INTEGER NOT NULL, " +
                    "reviewer_id INTEGER NOT NULL, " +
                    "decision VARCHAR(20) NOT NULL, " +
                    "remark TEXT, " +
                    "decided_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY(upload_id) REFERENCES uploads(id), " +
                    "FOREIGN KEY(reviewer_id) REFERENCES users(id)" +
                    ");");
        }
    }

    /** Audit log, sign-in throttle and the warden's second review. */
    private static void createSecurityTables(Connection conn) throws SQLException {
        String idCol = idColumn();
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS audit_log (" +
                    "seq BIGINT PRIMARY KEY, " +
                    "ts VARCHAR(40) NOT NULL, " +
                    "actor_id INTEGER, " +
                    "actor_username VARCHAR(100), " +
                    "actor_role VARCHAR(50), " +
                    "action VARCHAR(60) NOT NULL, " +
                    "resource_type VARCHAR(40), " +
                    "resource_id INTEGER, " +
                    "hostel_id INTEGER, " +
                    "outcome VARCHAR(16) NOT NULL, " +
                    "detail TEXT, " +
                    "prev_hash VARCHAR(64) NOT NULL, " +
                    "entry_hash VARCHAR(64) NOT NULL" +
                    ");");
            st.execute("CREATE TABLE IF NOT EXISTS login_throttle (" +
                    "username_lc VARCHAR(100) PRIMARY KEY, " +
                    "failures INTEGER NOT NULL, " +
                    "locked_until BIGINT NOT NULL" +
                    ");");
            st.execute("CREATE TABLE IF NOT EXISTS warden_reviews (" +
                    "id " + idCol + ", " +
                    "upload_id INTEGER NOT NULL UNIQUE, " +
                    "warden_id INTEGER NOT NULL, " +
                    "action VARCHAR(20) NOT NULL, " +
                    "remark TEXT, " +
                    "decided_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY(upload_id) REFERENCES uploads(id), " +
                    "FOREIGN KEY(warden_id) REFERENCES users(id)" +
                    ");");
        }
    }

    /** One demo warden for Ganga so the second-review step can be tried straight away. */
    private static void seedDemoWarden(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'WARDEN'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                Integer ganga = null;
                try (PreparedStatement h = conn.prepareStatement("SELECT id FROM hostels WHERE name = 'Ganga'");
                     ResultSet hr = h.executeQuery()) {
                    if (hr.next()) ganga = hr.getInt(1);
                }
                if (ganga != null) {
                    insertUser(conn, "warden1", "warden1234", "WARDEN", ganga);
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE users SET full_name = 'Ganga Warden' WHERE username = 'warden1'")) {
                        up.executeUpdate();
                    }
                    System.out.println("Seeded demo warden: warden1 (Ganga)");
                }
            }
        }
    }

    /** Adds the account columns introduced with registration (safe to run on every start). */
    private static void migrateUsersTable(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "users", "full_name", "VARCHAR(150)");
        addColumnIfMissing(conn, "users", "email", "VARCHAR(150)");
        addColumnIfMissing(conn, "users", "must_change_password", "INTEGER DEFAULT 0");
        addColumnIfMissing(conn, "hostels", "mess_type", "VARCHAR(20) DEFAULT 'SOUTH'");   // which weekly menu the hostel serves
    }

    private static void addColumnIfMissing(Connection conn, String table, String column, String type) throws SQLException {
        boolean exists = false;
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            if (rs.next()) exists = true;
        }
        if (!exists) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            }
            System.out.println("Added column " + table + "." + column);
        }
    }

    /** Weekly menu, meal ratings and complaints. */
    private static void createFeatureTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS mess_menu (" +
                    "id " + idColumn() + ", " +
                    "mess_type VARCHAR(20) NOT NULL, " +
                    "day_no INTEGER NOT NULL, " +
                    "meal VARCHAR(20) NOT NULL, " +
                    "items TEXT NOT NULL, " +
                    "updated_by INTEGER, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (mess_type, day_no, meal)" +
                    ");");
            st.execute("CREATE TABLE IF NOT EXISTS meal_ratings (" +
                    "id " + idColumn() + ", " +
                    "user_id INTEGER NOT NULL, " +
                    "hostel_id INTEGER NOT NULL, " +
                    "rating_date VARCHAR(10) NOT NULL, " +
                    "meal VARCHAR(20) NOT NULL, " +
                    "stars INTEGER NOT NULL, " +
                    "note TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (user_id, rating_date, meal), " +
                    "FOREIGN KEY(user_id) REFERENCES users(id), " +
                    "FOREIGN KEY(hostel_id) REFERENCES hostels(id)" +
                    ");");
            st.execute("CREATE TABLE IF NOT EXISTS complaints (" +
                    "id " + idColumn() + ", " +
                    "student_id INTEGER NOT NULL, " +
                    "hostel_id INTEGER NOT NULL, " +
                    "category VARCHAR(40) NOT NULL, " +
                    "meal VARCHAR(20), " +
                    "description TEXT NOT NULL, " +
                    "status VARCHAR(20) NOT NULL, " +
                    "reply TEXT, " +
                    "replied_by INTEGER, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "replied_at TIMESTAMP NULL, " +
                    "FOREIGN KEY(student_id) REFERENCES users(id), " +
                    "FOREIGN KEY(hostel_id) REFERENCES hostels(id)" +
                    ");");
        }
    }

    /** The demo student belongs to Ganga, like a student who registered and chose that hostel (needed for ratings and complaints). */
    private static void linkDemoStudent(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE users SET hostel_id = (SELECT id FROM hostels WHERE name = 'Ganga') "
                    + "WHERE username = 'student1' AND hostel_id IS NULL");
        }
    }

    /** Fills the weekly menu on first run: South, North and International all start with the official menu. */
    private static void seedMenu(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM mess_menu")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }
        String[] types = {"SOUTH", "NORTH", "INTERNATIONAL"};
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO mess_menu (mess_type, day_no, meal, items) VALUES (?, ?, ?, ?)")) {
            for (String type : types) {
                for (String[] row : MenuSeed.ROWS) {
                    ps.setString(1, type);
                    ps.setInt(2, Integer.parseInt(row[0]));
                    ps.setString(3, row[1]);
                    ps.setString(4, row[2]);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
        System.out.println("Seeded the weekly menu (South, North, International).");
    }

    /** Every mess/hostel must exist; missing ones are added (existing ids never change). */
    private static void seedHostels(Connection conn) throws SQLException {
        String[] names = {"Ganga", "Vedavati", "Annapurna", "Teesta"};
        for (String name : names) {
            boolean found;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM hostels WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    found = rs.next();
                }
            }
            if (!found) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hostels (name) VALUES (?)")) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
                System.out.println("Seeded hostel/mess: " + name);
            }
        }
    }

    /** Creates the first administrator once. Its password must be changed at first sign-in. */
    private static void seedAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                insertUser(conn, "admin", "admin123", "ADMIN", null);
                try (PreparedStatement up = conn.prepareStatement(
                        "UPDATE users SET must_change_password = 1, full_name = 'Administrator' WHERE username = 'admin'")) {
                    up.executeUpdate();
                }
                System.out.println("Seeded administrator: admin (must change password at first sign-in)");
            }
        }
    }

    private static void seedUsers(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM users";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                int gangaId = 1;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM hostels WHERE name = ?")) {
                    ps.setString(1, "Ganga");
                    ResultSet hRs = ps.executeQuery();
                    if (hRs.next()) {
                        gangaId = hRs.getInt("id");
                    }
                }

                insertUser(conn, "worker1", "worker123", "WORKER", gangaId);
                insertUser(conn, "head1", "head123", "HEAD", gangaId);
                insertUser(conn, "student1", "student123", "STUDENT", gangaId);

                System.out.println("Seeded users: worker1, head1, student1");
            }
        }
    }

    private static void insertUser(Connection conn, String username, String plainPassword, String role, Integer hostelId) throws SQLException {
        String salt = SecurityUtil.generateSalt();
        String hash = SecurityUtil.hashPassword(plainPassword, salt);

        String sql = "INSERT INTO users (username, password_hash, salt, role, hostel_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, role);
            if (hostelId != null) {
                ps.setInt(5, hostelId);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }
}
