package com.messlens.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.text.Normalizer;
import java.util.Base64;

/**
 * Cryptographic helpers.
 *
 * Two different jobs, two different primitives (NIST SP 800-63B, section 5.1.1.2):
 *  - PASSWORDS use a deliberately slow, salted key-derivation function (PBKDF2-HMAC-SHA256).
 *    A fast hash such as SHA-256 lets an attacker who steals the database test billions of guesses per second.
 *  - EVIDENCE PHOTOS use plain SHA-256: speed is a feature there (integrity / duplicate detection).
 *
 * Stored password format:  pbkdf2$<iterations>$<hex hash>   (salt lives in its own column).
 * Old accounts that still hold a bare 64-character SHA-256 hex string are verified the old way
 * and silently upgraded to PBKDF2 the next time that person signs in.
 */
public class SecurityUtil {

    /** OWASP 2023 minimum for PBKDF2-HMAC-SHA256. Overridable for tests: -Dmesslens.pbkdf2.iterations=1000 */
    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final String PREFIX = "pbkdf2$";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static int iterations() {
        String v = System.getProperty("messlens.pbkdf2.iterations");
        if (v != null) {
            try { return Math.max(1000, Integer.parseInt(v.trim())); } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_ITERATIONS;
    }

    /** Cryptographically strong 16-byte random salt, Base64 encoded. */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Hashes a password with PBKDF2-HMAC-SHA256 using the current iteration count. */
    public static String hashPassword(String password, String salt) {
        return hashPassword(password, salt, iterations());
    }

    public static String hashPassword(String password, String salt, int iterations) {
        return PREFIX + iterations + "$" + bytesToHex(pbkdf2(password, salt, iterations));
    }

    /** Verifies a password against the stored value (current or legacy format) in constant time. */
    public static boolean verifyPassword(String password, String salt, String stored) {
        if (password == null || salt == null || stored == null) return false;
        if (stored.startsWith(PREFIX)) {
            String[] parts = stored.split("\\$");
            if (parts.length != 3) return false;
            int iter;
            try { iter = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return false; }
            byte[] expected = hexToBytes(parts[2]);
            if (expected == null) return false;
            return MessageDigest.isEqual(pbkdf2(password, salt, iter), expected);
        }
        // legacy: salted SHA-256 hex
        byte[] expected = hexToBytes(stored);
        if (expected == null) return false;
        return MessageDigest.isEqual(legacySha256(password, salt), expected);
    }

    /** True when the stored value is in the old fast-hash format or uses fewer iterations than today's setting. */
    public static boolean needsRehash(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return true;
        String[] parts = stored.split("\\$");
        if (parts.length != 3) return true;
        try { return Integer.parseInt(parts[1]) < iterations(); } catch (NumberFormatException e) { return true; }
    }

    /** Old scheme, kept only so existing accounts can still sign in once and be upgraded (and for benchmarks). */
    public static String legacyHashPassword(String password, String salt) {
        return bytesToHex(legacySha256(password, salt));
    }

    private static byte[] legacySha256(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return md.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static byte[] pbkdf2(String password, String salt, int iterations) {
        try {
            // NIST 800-63B: normalise Unicode (NFKC) before hashing so the same visible password always matches
            String normalised = Normalizer.normalize(password, Normalizer.Form.NFKC);
            byte[] saltBytes = decodeSalt(salt);
            PBEKeySpec spec = new PBEKeySpec(normalised.toCharArray(), saltBytes, iterations, 256);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 not available", e);
        }
    }

    private static byte[] decodeSalt(String salt) {
        try { return Base64.getDecoder().decode(salt); }
        catch (IllegalArgumentException e) { return salt.getBytes(StandardCharsets.UTF_8); }
    }

    // ------------------------------------------------------------------ evidence hashing (SHA-256)

    /** SHA-256 of an image file on disk. */
    public static String calculateFileHash(Path filePath) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        return bytesToHex(md.digest());
    }

    /** SHA-256 of raw bytes. */
    public static String calculateBytesHash(byte[] data) {
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static String sha256Hex(String text) {
        return calculateBytesHash(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = hex[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = hex[bytes[i] & 0xF];
        }
        return new String(out);
    }

    public static byte[] hexToBytes(String s) {
        if (s == null || s.length() % 2 != 0) return null;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
