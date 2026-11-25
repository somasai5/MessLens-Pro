package com.messlens.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fixed facts about the mess: meals, mess types, days, serving times, complaint categories. */
public final class MessInfo {

    public static final String[] MEALS = {"Breakfast", "Lunch", "Snacks", "Dinner"};
    public static final String[] MESS_TYPES = {"SOUTH", "NORTH", "INTERNATIONAL"};
    public static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    public static final String[] CATEGORIES = {"Food quality", "Hygiene", "Quantity", "Delay in serving", "Other"};

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private MessInfo() {}

    /** "SOUTH" -> "South". */
    public static String label(String messType) {
        if (messType == null || messType.isEmpty()) return "South";
        return messType.substring(0, 1).toUpperCase() + messType.substring(1).toLowerCase();
    }

    /** "South" -> "SOUTH" (null if it is not one of the three mess types). */
    public static String typeOf(String label) {
        for (String t : MESS_TYPES) if (t.equalsIgnoreCase(label)) return t;
        return null;
    }

    public static boolean isMeal(String meal) {
        for (String m : MEALS) if (m.equals(meal)) return true;
        return false;
    }

    public static boolean isCategory(String c) {
        for (String x : CATEGORIES) if (x.equals(c)) return true;
        return false;
    }

    /** Saturday and Sunday have a later breakfast. dayNo: 1 = Monday ... 7 = Sunday. */
    public static boolean isWeekend(int dayNo) {
        return dayNo >= 6;
    }

    /** {start, end} of a meal in minutes after midnight. */
    public static int[] window(String meal, boolean weekend) {
        switch (meal) {
            case "Breakfast": return weekend ? new int[]{450, 570} : new int[]{420, 540};   // 7:30-9:30 / 7:00-9:00
            case "Lunch":     return new int[]{720, 870};                                   // 12:00-2:30
            case "Snacks":    return new int[]{990, 1065};                                  // 4:30-5:45
            default:          return new int[]{1140, 1305};                                 // 7:00-9:45
        }
    }

    /** "7:00 AM - 9:00 AM". */
    public static String timeText(String meal, boolean weekend) {
        int[] w = window(meal, weekend);
        return LocalTime.of(w[0] / 60, w[0] % 60).format(CLOCK) + " - " + LocalTime.of(w[1] / 60, w[1] % 60).format(CLOCK);
    }

    /** True while the meal is being served right now. */
    public static boolean isServingNow(String meal, int dayNo, LocalTime now) {
        int[] w = window(meal, isWeekend(dayNo));
        int m = now.getHour() * 60 + now.getMinute();
        return m >= w[0] && m < w[1];
    }

    /** Eggs, chicken and meat dishes are marked in red on the menu. */
    public static boolean isNonVeg(String item) {
        String s = item.toLowerCase(Locale.ROOT);
        if (s.contains("eggless")) return false;
        return s.contains("egg") || s.contains("chicken") || s.contains("murgh") || s.contains("kodi")
                || s.contains("omelette") || s.contains("mutton") || s.contains("fish") || s.contains("prawn");
    }
}
