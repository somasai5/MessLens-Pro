package com.messlens.util;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Takes a photo with the Mac's built-in camera using the free "imagesnap" tool
 * (install once with: brew install imagesnap).
 */
public final class MacCamera {

    public static final String NOT_INSTALLED = "NOT_INSTALLED";
    private static final String[] PATHS = {"/opt/homebrew/bin/imagesnap", "/usr/local/bin/imagesnap", "/usr/bin/imagesnap"};

    private MacCamera() {}

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    public static String findTool() {
        for (String p : PATHS) {
            if (new File(p).canExecute()) return p;
        }
        return null;
    }

    /** Blocks for about 2-4 seconds; call from a background thread. */
    public static byte[] capture() throws IOException, InterruptedException {
        String tool = findTool();
        if (tool == null) throw new IOException(NOT_INSTALLED);

        File tmp = File.createTempFile("messlens_cam_", ".jpg");
        try {
            Process p = new ProcessBuilder(tool, "-q", "-w", "1", tmp.getAbsolutePath())
                    .redirectErrorStream(true).start();
            if (!p.waitFor(25, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("The camera did not respond. Please try again.");
            }
            byte[] bytes = Files.readAllBytes(tmp.toPath());
            if (bytes.length == 0) {
                throw new IOException("No picture was taken. Allow camera access for Terminal in System Settings > Privacy & Security > Camera, then try again.");
            }
            return bytes;
        } finally {
            tmp.delete();
        }
    }

    /**
     * Live preview for the Mac camera: runs imagesnap in time-lapse mode (a new JPEG every ~0.3 s)
     * and reports the newest finished frame. The latest frame is also what "Take photo" uses, so
     * the picture is exactly what the worker saw. No extra install needed beyond imagesnap.
     */
    public static final class Live {
        private Process proc;
        private Path dir;
        private Thread poller;
        private volatile boolean running;
        private volatile byte[] latest;

        public boolean isRunning() { return running; }
        public byte[] latestFrame() { return latest; }

        public synchronized void start(Consumer<byte[]> onFrame, Runnable onFail) throws IOException {
            if (running) return;
            String tool = findTool();
            if (tool == null) throw new IOException(NOT_INSTALLED);
            dir = Files.createTempDirectory("messlens_live_");
            proc = new ProcessBuilder(tool, "-q", "-w", "0.5", "-t", "0.3")
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            running = true;
            poller = new Thread(() -> poll(onFrame, onFail), "MessLens-MacLive");
            poller.setDaemon(true);
            poller.start();
        }

        private void poll(Consumer<byte[]> onFrame, Runnable onFail) {
            String lastName = "";
            long started = System.currentTimeMillis();
            boolean gotAny = false;
            try {
                while (running) {
                    File[] files = dir.toFile().listFiles((d, n) -> n.toLowerCase().endsWith(".jpg"));
                    if (files != null && files.length > 0) {
                        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                        long now = System.currentTimeMillis();
                        File pick = null;
                        for (File f : files) {                 // newest file that is finished writing
                            if (now - f.lastModified() > 120 && f.length() > 0) { pick = f; break; }
                        }
                        if (pick != null && !pick.getName().equals(lastName)) {
                            byte[] bytes = Files.readAllBytes(pick.toPath());
                            lastName = pick.getName();
                            latest = bytes;
                            gotAny = true;
                            onFrame.accept(bytes);
                        }
                        for (File f : files) {                 // keep the folder tiny
                            if (!f.getName().equals(lastName) && now - f.lastModified() > 1000) f.delete();
                        }
                    }
                    if (!gotAny && (!proc.isAlive() || System.currentTimeMillis() - started > 8000)) {
                        running = false;
                        onFail.run();
                        return;
                    }
                    Thread.sleep(120);
                }
            } catch (Exception ignored) {
                // stop() ends the loop; any other failure just ends the preview
            }
        }

        public synchronized void stop() {
            running = false;
            if (proc != null) { proc.destroy(); proc.destroyForcibly(); proc = null; }
            if (dir != null) {
                File[] fs = dir.toFile().listFiles();
                if (fs != null) for (File f : fs) f.delete();
                dir.toFile().delete();
                dir = null;
            }
        }
    }
}
