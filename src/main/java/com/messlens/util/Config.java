package com.messlens.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Global configuration management for MessLens.
 * The app stores everything in one local SQLite file.
 */
public class Config {
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties properties = new Properties();

    private static boolean devMode = false;
    private static String evidenceDir = "evidence";
    private static int maxPhotoAgeMinutes = 0; // 0 = do not check the age of chosen photo files
    private static String sqliteUrl = "jdbc:sqlite:messlens.db";
    // Which database to use: "mysql" (shared server, default) or "sqlite" (one local file, no setup)
    private static String dbType = "mysql";
    private static String mysqlUrl = "jdbc:mysql://localhost:3306/messlens?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";
    private static String mysqlUser = "root";
    private static String mysqlPassword = "";
    private static String auditDir = "audit";
    private static boolean auditAutoReset = false;
    /** Project/demo setting: when the audit key file is missing but the table has rows, start a new audit chain instead of failing. */
    public static boolean isAuditAutoReset() { return auditAutoReset; }
    private static int sessionIdleMinutes = 15;
    private static int sessionMaxHours = 8;
    private static int overturnWindowDays = 7;

    static {
        loadConfig();
        ensureEvidenceDirExists();
    }

    /**
     * A relative folder name ("evidence", "audit") would land inside whichever project folder the app is started from,
     * so a fresh copy of the project would lose the audit key and the photos. Relative names are therefore kept in
     * ~/.messlens/<name>; an absolute path in config.properties is used exactly as written.
     */
    private static String stableDir(String configured, String name) {
        if (configured != null && !configured.isBlank() && new File(configured.trim()).isAbsolute()) return configured.trim();
        return new File(new File(System.getProperty("user.home"), ".messlens"), name).getPath();
    }

    private static void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
                devMode = Boolean.parseBoolean(properties.getProperty("DEV_MODE", "false"));
                evidenceDir = stableDir(properties.getProperty("EVIDENCE_DIR", "evidence"), "evidence");
                try { maxPhotoAgeMinutes = Integer.parseInt(properties.getProperty("MAX_PHOTO_AGE_MINUTES", "0").trim()); } catch (NumberFormatException ignored) {}
                sqliteUrl = properties.getProperty("SQLITE_URL", "jdbc:sqlite:messlens.db");
                dbType = properties.getProperty("DB_TYPE", "mysql").trim().toLowerCase();
                mysqlUrl = properties.getProperty("MYSQL_URL", mysqlUrl).trim();
                mysqlUser = properties.getProperty("MYSQL_USER", mysqlUser).trim();
                mysqlPassword = properties.getProperty("MYSQL_PASSWORD", mysqlPassword);
                auditDir = stableDir(properties.getProperty("AUDIT_DIR", "audit"), "audit");
                auditAutoReset = Boolean.parseBoolean(properties.getProperty("AUDIT_AUTO_RESET", "false").trim());
                sessionIdleMinutes = intProp("SESSION_IDLE_MINUTES", 15);
                sessionMaxHours = intProp("SESSION_MAX_HOURS", 8);
                overturnWindowDays = intProp("OVERTURN_WINDOW_DAYS", 7);
            } catch (IOException e) {
                System.err.println("Could not read config.properties, using defaults: " + e.getMessage());
            }
        } else {
            saveDefaults();
        }
    }

    private static int intProp(String key, int def) {
        try { return Integer.parseInt(properties.getProperty(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static void saveDefaults() {
        properties.setProperty("DEV_MODE", "false");
        properties.setProperty("EVIDENCE_DIR", "evidence");
        properties.setProperty("MAX_PHOTO_AGE_MINUTES", "0");
        properties.setProperty("SQLITE_URL", "jdbc:sqlite:messlens.db");
        properties.setProperty("DB_TYPE", dbType);
        properties.setProperty("MYSQL_URL", mysqlUrl);
        properties.setProperty("MYSQL_USER", mysqlUser);
        properties.setProperty("MYSQL_PASSWORD", mysqlPassword);
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "MessLens Configuration");
        } catch (IOException e) {
            System.err.println("Could not create default config.properties: " + e.getMessage());
        }
    }

    /** Folder holding the audit-log key state and the verifier key. */
    public static String getAuditDir() { return auditDir; }
    public static void setAuditDir(String dir) { auditDir = dir; }
    /** For automated tests only: use a throw-away SQLite file and key folder so the real data is never touched. */
    public static void overrideForTests(String sqliteFile, String auditFolder, String evidenceFolder) {
        sqliteUrl = "jdbc:sqlite:" + sqliteFile;
        dbType = "sqlite";   // tests always use their own throw-away SQLite file
        auditDir = auditFolder;
        evidenceDir = evidenceFolder;
    }

    public static int getSessionIdleMinutes() { return sessionIdleMinutes; }
    public static int getSessionMaxHours() { return sessionMaxHours; }
    public static int getOverturnWindowDays() { return overturnWindowDays; }

    public static boolean isDevMode() {
        return devMode;
    }

    public static void setDevMode(boolean enabled) {
        devMode = enabled;
        properties.setProperty("DEV_MODE", String.valueOf(enabled));
        saveCurrentProperties();
    }

    public static int getMaxPhotoAgeMinutes() {
        return maxPhotoAgeMinutes;
    }

    public static String getEvidenceDir() {
        return evidenceDir;
    }

    public static String getSqliteUrl() {
        return sqliteUrl;
    }

    public static boolean isMySql() { return "mysql".equals(dbType); }
    public static String getMysqlUrl() { return mysqlUrl; }
    public static String getMysqlUser() { return mysqlUser; }
    public static String getMysqlPassword() { return mysqlPassword; }

    private static void saveCurrentProperties() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "MessLens Configuration");
        } catch (IOException e) {
            System.err.println("Failed to update config.properties: " + e.getMessage());
        }
    }

    public static void ensureEvidenceDirExists() {
        File dir = new File(evidenceDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
