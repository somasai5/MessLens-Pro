package com.messlens.model;

/**
 * Represents an authenticated user in the MessLens system.
 * Roles include ADMIN, HEAD, WORKER and STUDENT.
 */
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String salt;
    private String role;
    private Integer hostelId; // Nullable for general students
    private String fullName;
    private String email;
    private boolean mustChangePassword;

    public User() {}

    public User(int id, String username, String passwordHash, String salt, String role, Integer hostelId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.hostelId = hostelId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getHostelId() {
        return hostelId;
    }

    public void setHostelId(Integer hostelId) {
        this.hostelId = hostelId;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }

    /** Name to show in the UI: full name when known, otherwise the username. */
    public String displayName() {
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}

