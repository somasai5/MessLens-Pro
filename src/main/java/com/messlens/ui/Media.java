package com.messlens.ui;

import javafx.scene.image.Image;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Loads evidence photos from disk (with a small cache) and tolerates Windows-style paths. */
public final class Media {

    private static final Map<String, Image> CACHE = new HashMap<>();

    private Media() {}

    public static File file(String path) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (!f.exists()) f = new File(path.replace('\\', File.separatorChar).replace('/', File.separatorChar));
        if (!f.exists()) f = new File(path.replace('\\', '/'));
        return f.exists() ? f : null;
    }

    /** Small scaled copy for tables and tiles. Returns null when the file is missing. */
    public static Image thumb(String path, double w, double h) {
        File f = file(path);
        if (f == null) return null;
        String key = f.getAbsolutePath() + "@" + (int) w + "x" + (int) h;
        return CACHE.computeIfAbsent(key, k -> {
            try {
                // load at 2x for sharp rendering on high-DPI screens
                return new Image(f.toURI().toString(), w * 2, h * 2, true, true, false);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Full-size image for the big viewers. */
    public static Image full(String path) {
        File f = file(path);
        if (f == null) return null;
        String key = f.getAbsolutePath() + "@full";
        return CACHE.computeIfAbsent(key, k -> {
            try {
                return new Image(f.toURI().toString(), 1400, 1050, true, true, false);
            } catch (Exception e) {
                return null;
            }
        });
    }

    public static void forget(String path) {
        File f = file(path);
        if (f == null) return;
        CACHE.keySet().removeIf(k -> k.startsWith(f.getAbsolutePath() + "@"));
    }
}
