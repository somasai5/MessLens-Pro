package com.messlens.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The quality standards checked at each photo stage. A photo is the proof; these answers are the result.
 * Limits below are typical food-safety figures. Confirm them against your hostel's or university's standard
 * (for example FSSAI guidelines) and change them here if needed.
 */
public final class QualityChecklist {

    /** Cold ingredients should be stored at or below this temperature (deg C). */
    public static final double COLD_STORAGE_MAX_C = 5;
    /** Food should reach at least this temperature when cooked (deg C). */
    public static final double COOKING_MIN_C = 75;
    /** Hot food should still be at least this temperature when served (deg C). */
    public static final double SERVING_MIN_C = 60;

    /** One line of the checklist: a yes/no confirmation, or a number (a temperature). */
    public record Item(String key, String label, boolean number, String unit, boolean required, Double min, Double max) {}

    private static final Map<String, List<Item>> BY_STAGE = new LinkedHashMap<>();

    static {
        BY_STAGE.put("Ingredients", List.of(
                check("expiry_ok", "Expiry date is visible and not expired"),
                check("pack_ok", "Packaging is intact, no signs of spoilage"),
                temp("cold_temp", "Storage temperature of cold items (if any)", false, null, COLD_STORAGE_MAX_C)));
        BY_STAGE.put("Cooking", List.of(
                check("gloves_ok", "Staff wear gloves, cap and apron"),
                check("utensils_ok", "Utensils and surfaces are clean"),
                check("oil_ok", "Oil is fresh (not dark or reused)"),
                temp("cook_temp", "Cooking temperature", true, COOKING_MIN_C, null)));
        BY_STAGE.put("Serving", List.of(
                check("plates_ok", "Plates and serving area are clean"),
                check("portion_ok", "Portion matches the standard"),
                temp("serve_temp", "Food temperature at serving", true, SERVING_MIN_C, null)));
    }

    private QualityChecklist() {}

    private static Item check(String key, String label) {
        return new Item(key, label, false, null, false, null, null);
    }

    private static Item temp(String key, String label, boolean required, Double min, Double max) {
        return new Item(key, label, true, "\u00b0C", required, min, max);
    }

    public static List<Item> items(String stage) {
        List<Item> l = stage == null ? null : BY_STAGE.get(stage);
        return l == null ? List.of() : l;
    }

    // ---------------------------------------------------------------- storage format: key=value;key=value

    public static String encode(Map<String, String> answers) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : answers.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue().trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static Map<String, String> decode(String stored) {
        Map<String, String> m = new LinkedHashMap<>();
        if (stored == null || stored.isBlank()) return m;
        for (String part : stored.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) m.put(part.substring(0, i), part.substring(i + 1));
        }
        return m;
    }

    // ---------------------------------------------------------------- checking

    /** An error message when the answers cannot be accepted (a required temperature is missing or not a number), else null. */
    public static String validate(String stage, Map<String, String> answers) {
        for (Item it : items(stage)) {
            if (!it.number()) continue;
            String v = answers.get(it.key());
            if (v == null || v.isBlank()) {
                if (it.required()) return "Enter the " + it.label().toLowerCase() + ".";
                continue;
            }
            Double d = parse(v);
            if (d == null || d < -50 || d > 400) return "Enter a valid number for " + it.label().toLowerCase() + ".";
        }
        return null;
    }

    /** True when this answer meets the standard. */
    public static boolean passes(Item it, String value) {
        if (!it.number()) return "1".equals(value);
        Double d = parse(value);
        if (d == null) return !it.required();            // an optional reading that was left empty is not a failure
        if (it.min() != null && d < it.min()) return false;
        return it.max() == null || d <= it.max();
    }

    /** The labels of every item that did NOT meet the standard. Empty list = all standards met. */
    public static List<String> problems(String stage, String stored) {
        Map<String, String> a = decode(stored);
        List<String> out = new ArrayList<>();
        for (Item it : items(stage)) {
            String v = a.get(it.key());
            if (it.number() && (v == null || v.isBlank()) && !it.required()) continue;
            if (!passes(it, v)) out.add(it.label());
        }
        return out;
    }

    public static Double parse(String v) {
        try {
            return v == null ? null : Double.parseDouble(v.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
