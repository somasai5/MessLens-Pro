package com.messlens.ui;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Human-friendly formatting for the timestamps stored as "yyyy-MM-dd HH:mm:ss". */
public final class Fmt {

    private static final DateTimeFormatter IN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private Fmt() {}

    public static LocalDateTime parse(String ts) {
        if (ts == null || ts.length() < 19) return null;
        try {
            return LocalDateTime.parse(ts.substring(0, 19).replace('T', ' '), IN);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "Today, 3:42 PM" / "Yesterday, 9:10 AM" / "2 Nov, 6:05 PM". */
    public static String when(String ts) {
        LocalDateTime t = parse(ts);
        if (t == null) return ts == null || ts.isBlank() ? "-" : ts;
        LocalDate today = LocalDate.now();
        String clock = t.format(CLOCK);
        if (t.toLocalDate().equals(today)) return "Today, " + clock;
        if (t.toLocalDate().equals(today.minusDays(1))) return "Yesterday, " + clock;
        return t.format(DAY) + ", " + clock;
    }

    /** "12 min ago", "3 h ago", "2 days ago". */
    public static String ago(String ts) {
        LocalDateTime t = parse(ts);
        if (t == null) return "-";
        long mins = Duration.between(t, LocalDateTime.now()).toMinutes();
        if (mins < 1) return "just now";
        if (mins < 60) return mins + " min ago";
        if (mins < 60 * 24) return (mins / 60) + " h ago";
        return (mins / (60 * 24)) + " d ago";
    }

    public static boolean isToday(String ts) {
        LocalDateTime t = parse(ts);
        return t != null && t.toLocalDate().equals(LocalDate.now());
    }

    public static String status(String s) {
        if (s == null) return "-";
        switch (s.toUpperCase()) {
            case "APPROVED": return "Approved";
            case "REJECTED": return "Rejected";
            case "PENDING": return "Waiting for review";
            default: return s;
        }
    }

    public static String shortStatus(String s) {
        if (s == null) return "-";
        switch (s.toUpperCase()) {
            case "APPROVED": return "Approved";
            case "REJECTED": return "Rejected";
            case "PENDING": return "Pending";
            default: return s;
        }
    }
}
