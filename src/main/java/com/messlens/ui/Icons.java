package com.messlens.ui;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import java.util.HashMap;
import java.util.Map;

/**
 * Small vector icon set (24x24 outline icons, Feather-style geometry).
 * Drawn as JavaFX shapes, so they look identical on every computer
 * (no dependency on emoji fonts) and take their colour from CSS.
 */
public final class Icons {

    private static final Map<String, String> PATHS = new HashMap<>();

    static {
        PATHS.put("camera", "M23 19 a2 2 0 0 1 -2 2 H3 a2 2 0 0 1 -2 -2 V8 a2 2 0 0 1 2 -2 h4 l2 -3 h6 l2 3 h4 a2 2 0 0 1 2 2 z "
                + "M12 9 a4 4 0 1 0 0 8 a4 4 0 1 0 0 -8 z");
        PATHS.put("list", "M8 6 h13 M8 12 h13 M8 18 h13 M3 6 h0.01 M3 12 h0.01 M3 18 h0.01");
        PATHS.put("check-circle", "M22 11.08 V12 a10 10 0 1 1 -5.93 -9.14 M22 4 L12 14.01 l-3 -3");
        PATHS.put("check-square", "M9 11 l3 3 L22 4 M21 12 v7 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 V5 a2 2 0 0 1 2 -2 h11");
        PATHS.put("bar-chart", "M12 20 V10 M18 20 V4 M6 20 v-4");
        PATHS.put("file-text", "M14 2 H6 a2 2 0 0 0 -2 2 v16 a2 2 0 0 0 2 2 h12 a2 2 0 0 0 2 -2 V8 z M14 2 v6 h6 M16 13 H8 M16 17 H8 M10 9 H8");
        PATHS.put("calendar", "M19 4 H5 a2 2 0 0 0 -2 2 v14 a2 2 0 0 0 2 2 h14 a2 2 0 0 0 2 -2 V6 a2 2 0 0 0 -2 -2 z M16 2 v4 M8 2 v4 M3 10 h18");
        PATHS.put("home", "M3 9 l9 -7 9 7 v11 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 z M9 22 V12 h6 v10");
        PATHS.put("sliders", "M4 21 v-7 M4 10 V3 M12 21 v-9 M12 8 V3 M20 21 v-5 M20 12 V3 M1 14 h6 M9 8 h6 M17 16 h6");
        PATHS.put("help", "M12 2 a10 10 0 1 0 0 20 a10 10 0 1 0 0 -20 z M9.09 9 a3 3 0 0 1 5.83 1 c0 2 -3 3 -3 3 M12 17 h0.01");
        PATHS.put("log-out", "M9 21 H5 a2 2 0 0 1 -2 -2 V5 a2 2 0 0 1 2 -2 h4 M16 17 l5 -5 -5 -5 M21 12 H9");
        PATHS.put("refresh", "M23 4 v6 h-6 M1 20 v-6 h6 M3.51 9 a9 9 0 0 1 14.85 -3.36 L23 10 M1 14 l4.64 4.36 A9 9 0 0 0 20.49 15");
        PATHS.put("x", "M18 6 L6 18 M6 6 l12 12");
        PATHS.put("check", "M20 6 L9 17 l-5 -5");
        PATHS.put("download", "M21 15 v4 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 v-4 M7 10 l5 5 5 -5 M12 15 V3");
        PATHS.put("image", "M19 3 H5 a2 2 0 0 0 -2 2 v14 a2 2 0 0 0 2 2 h14 a2 2 0 0 0 2 -2 V5 a2 2 0 0 0 -2 -2 z M8.5 7 a1.5 1.5 0 1 0 0 3 a1.5 1.5 0 1 0 0 -3 z M21 15 l-5 -5 L5 21");
        PATHS.put("trash", "M3 6 h18 M19 6 v14 a2 2 0 0 1 -2 2 H7 a2 2 0 0 1 -2 -2 V6 m3 0 V4 a2 2 0 0 1 2 -2 h4 a2 2 0 0 1 2 2 v2");
        PATHS.put("search", "M11 3 a8 8 0 1 0 0 16 a8 8 0 1 0 0 -16 z M21 21 l-4.35 -4.35");
        PATHS.put("clock", "M12 2 a10 10 0 1 0 0 20 a10 10 0 1 0 0 -20 z M12 6 v6 l4 2");
        PATHS.put("user", "M20 21 v-2 a4 4 0 0 0 -4 -4 H8 a4 4 0 0 0 -4 4 v2 M12 3 a4 4 0 1 0 0 8 a4 4 0 1 0 0 -8 z");
        PATHS.put("eye", "M1 12 s4 -8 11 -8 s11 8 11 8 s-4 8 -11 8 s-11 -8 -11 -8 z M12 9 a3 3 0 1 0 0 6 a3 3 0 1 0 0 -6 z");
        PATHS.put("eye-off", "M17.94 17.94 A10.07 10.07 0 0 1 12 20 c-7 0 -11 -8 -11 -8 a18.45 18.45 0 0 1 5.06 -5.94 M9.9 4.24 A9.12 9.12 0 0 1 12 4 c7 0 11 8 11 8 a18.5 18.5 0 0 1 -2.16 3.19 M14.12 14.12 a3 3 0 1 1 -4.24 -4.24 M1 1 l22 22");
        PATHS.put("lock", "M19 11 H5 a2 2 0 0 0 -2 2 v7 a2 2 0 0 0 2 2 h14 a2 2 0 0 0 2 -2 v-7 a2 2 0 0 0 -2 -2 z M7 11 V7 a5 5 0 0 1 10 0 v4");
        PATHS.put("link", "M10 13 a5 5 0 0 0 7.54 .54 l3 -3 a5 5 0 0 0 -7.07 -7.07 l-1.72 1.71 M14 11 a5 5 0 0 0 -7.54 -.54 l-3 3 a5 5 0 0 0 7.07 7.07 l1.71 -1.71");
        PATHS.put("activity", "M22 12 h-4 l-3 9 L9 3 l-3 9 H2");
        PATHS.put("lens", "M12 2 a10 10 0 1 0 0 20 a10 10 0 1 0 0 -20 z M14.31 8 l5.74 9.94 M9.69 8 h11.48 M7.38 12 l5.74 -9.94 M9.69 16 L3.95 6.06 M14.31 16 H2.83 M16.62 12 l-5.74 9.94");
        PATHS.put("info", "M12 2 a10 10 0 1 0 0 20 a10 10 0 1 0 0 -20 z M12 16 v-4 M12 8 h0.01");
        PATHS.put("alert", "M10.29 3.86 L1.82 18 a2 2 0 0 0 1.71 3 h16.94 a2 2 0 0 0 1.71 -3 L13.71 3.86 a2 2 0 0 0 -3.42 0 z M12 9 v4 M12 17 h0.01");
        PATHS.put("shield", "M12 22 s8 -4 8 -10 V5 l-8 -3 -8 3 v7 c0 6 8 10 8 10 z");
        PATHS.put("folder", "M22 19 a2 2 0 0 1 -2 2 H4 a2 2 0 0 1 -2 -2 V5 a2 2 0 0 1 2 -2 h5 l2 3 h9 a2 2 0 0 1 2 2 z");
        PATHS.put("database", "M12 8 c4.97 0 9 -1.34 9 -3 s-4.03 -3 -9 -3 -9 1.34 -9 3 4.03 3 9 3 z M21 12 c0 1.66 -4 3 -9 3 s-9 -1.34 -9 -3 M3 5 v14 c0 1.66 4 3 9 3 s9 -1.34 9 -3 V5");
        PATHS.put("key", "M21 2 l-2 2 m-7.61 7.61 a5.5 5.5 0 1 1 -7.78 7.78 5.5 5.5 0 0 1 7.78 -7.78 z m0 0 L15.5 7.5 m0 0 l3 3 L22 7 l-3 -3 m-3.5 3.5 L19 4");
        PATHS.put("arrow-right", "M5 12 h14 M12 5 l7 7 -7 7");
        PATHS.put("zoom", "M11 3 a8 8 0 1 0 0 16 a8 8 0 1 0 0 -16 z M21 21 l-4.35 -4.35 M11 8 v6 M8 11 h6");
        PATHS.put("message", "M21 15 a2 2 0 0 1 -2 2 H7 l-4 4 V5 a2 2 0 0 1 2 -2 h14 a2 2 0 0 1 2 2 z");
        PATHS.put("star", "M12 2 l3.09 6.26 L22 9.27 l-5 4.87 1.18 6.88 L12 17.77 l-6.18 3.25 L7 14.14 2 9.27 l6.91 -1.01 z");
    }

    private Icons() {}

    /** Creates an icon of the given pixel size. Colour comes from CSS (.icon, .icon-primary ...). */
    public static StackPane of(String name, double size, String... extraStyleClasses) {
        SVGPath p = new SVGPath();
        p.setContent(PATHS.getOrDefault(name, PATHS.get("lens")));
        p.getStyleClass().add("icon");
        p.getStyleClass().addAll(extraStyleClasses);
        p.setScaleX(size / 24.0);
        p.setScaleY(size / 24.0);
        // Compensate so the visible stroke stays ~2px at 24 and ~1.6px at 18, whatever the size.
        p.setStrokeWidth(2.0 + (24.0 - size) / 24.0);

        StackPane box = new StackPane(p);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    public static boolean has(String name) {
        return PATHS.containsKey(name);
    }

    public static java.util.Set<String> names() {
        return new java.util.TreeSet<>(PATHS.keySet());
    }
}
