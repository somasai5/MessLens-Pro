package com.messlens.util;

import java.io.File;

/**
 * Finds a stored evidence photo even when its path was saved on another operating system.
 * Rows created on Windows contain "evidence\\photo.jpg"; macOS and Linux need "evidence/photo.jpg".
 */
public final class PathUtil {

    private PathUtil() {}

    /** The existing file for a stored path, or null when it really is not on this computer. */
    public static File resolve(String path) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (f.exists()) return f;
        f = new File(path.replace('\\', File.separatorChar).replace('/', File.separatorChar));
        if (f.exists()) return f;
        f = new File(path.replace('\\', '/'));
        return f.exists() ? f : null;
    }
}
