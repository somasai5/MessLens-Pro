package com.messlens.security;

/**
 * Password rules. The "common password", "digits added at the end", "repeated characters" and
 * "do not use your name" checks were removed on request: any password of at least 6 characters is accepted.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {}

    public static int blocklistSize() { return 0; }

    /** @return a human-readable reason the password is not acceptable, or null when it is fine. */
    public static String check(String password, String... contextWords) {
        if (password == null || password.isEmpty()) return "Enter a password.";
        int len = password.codePointCount(0, password.length());
        if (len < MIN_LENGTH) return "Use at least " + MIN_LENGTH + " characters.";
        if (len > MAX_LENGTH) return "Password is too long (maximum " + MAX_LENGTH + " characters).";
        return null;
    }
}
