package com.messlens.security;

/** The five roles. Everything finer-grained (which hostel, whose photo, what state) is decided by attributes. */
public final class Roles {
    public static final String STUDENT = "STUDENT";
    public static final String WORKER = "WORKER";
    public static final String HEAD = "HEAD";
    public static final String WARDEN = "WARDEN";
    public static final String ADMIN = "ADMIN";
    public static final String[] ALL = {STUDENT, WORKER, HEAD, WARDEN, ADMIN};

    private Roles() {}

    public static String label(String role) {
        if (role == null) return "";
        switch (role.toUpperCase()) {
            case WORKER: return "Kitchen staff";
            case HEAD: return "Mess head";
            case WARDEN: return "Warden";
            case ADMIN: return "Administrator";
            default: return "Student";
        }
    }
}
