package com.messlens.security;

import com.messlens.model.User;
import com.messlens.util.Config;

import java.util.function.LongSupplier;

/**
 * Session lifetime rules (NIST SP 800-63B section 7):
 *  - IDLE timeout: the session ends after SESSION_IDLE_MINUTES without any mouse or keyboard activity;
 *  - ABSOLUTE timeout: the session ends SESSION_MAX_HOURS after sign-in, however active the person is.
 * Pure logic with an injectable clock; the UI calls touch() on input events and polls expiredReason().
 */
public final class SessionManager {

    private static LongSupplier clock = System::currentTimeMillis;
    private static User user;
    private static long startedAt;
    private static long lastActivity;

    private SessionManager() {}

    public static void setClock(LongSupplier c) { clock = c == null ? System::currentTimeMillis : c; }

    public static synchronized void start(User u) {
        user = u;
        startedAt = clock.getAsLong();
        lastActivity = startedAt;
    }

    public static synchronized void end() {
        user = null;
    }

    public static synchronized boolean isActive() { return user != null; }

    public static synchronized void touch() {
        if (user != null) lastActivity = clock.getAsLong();
    }

    /** @return null while the session is valid, otherwise a short reason ("idle" or "absolute"). */
    public static synchronized String expiredReason() {
        if (user == null) return null;
        long now = clock.getAsLong();
        if (now - lastActivity > Config.getSessionIdleMinutes() * 60_000L) return "idle";
        if (now - startedAt > Config.getSessionMaxHours() * 3_600_000L) return "absolute";
        return null;
    }
}
