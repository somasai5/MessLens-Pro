package com.messlens.util;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages live webcam capture, background frame acquisition, and conversion to JavaFX Image.
 * Handles webcam absence gracefully without crashing the UI.
 */
public class WebcamService {

    private Webcam webcam;
    private ScheduledExecutorService executor;
    private volatile BufferedImage latestFrame;
    private volatile boolean running = false;
    private boolean webcamAvailable = false;

    public WebcamService() {
        try {
            this.webcam = Webcam.getDefault();
            if (this.webcam != null) {
                Dimension[] nonStandardResolutions = new Dimension[] {
                        WebcamResolution.VGA.getSize() // 640x480
                };
                this.webcam.setCustomViewSizes(nonStandardResolutions);
                this.webcam.setViewSize(WebcamResolution.VGA.getSize());
                webcamAvailable = true;
            } else {
                System.out.println("No default webcam detected on this system.");
            }
        } catch (Throwable t) {
            System.err.println("Webcam initialization notice: " + t.getMessage());
            webcamAvailable = false;
        }
    }

    public boolean isWebcamAvailable() {
        return webcamAvailable && webcam != null;
    }

    /**
     * Starts streaming frames from the webcam to the provided JavaFX Consumer callback.
     */
    public synchronized void start(Consumer<Image> frameConsumer) {
        if (!isWebcamAvailable() || running) {
            return;
        }

        try {
            if (!webcam.isOpen()) {
                webcam.open(true); // Open asynchronously
            }
            running = true;
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MessLens-Webcam-Thread");
                t.setDaemon(true);
                return t;
            });

            executor.scheduleAtFixedRate(() -> {
                if (!running || !webcam.isOpen()) {
                    return;
                }
                try {
                    BufferedImage frame = webcam.getImage();
                    if (frame != null) {
                        latestFrame = frame;
                        Image fxImage = convertToFxImage(frame);
                        if (fxImage != null) {
                            Platform.runLater(() -> frameConsumer.accept(fxImage));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Webcam frame capture error: " + e.getMessage());
                }
            }, 0, 40, TimeUnit.MILLISECONDS); // ~25 FPS

        } catch (Exception e) {
            System.err.println("Failed to start webcam streaming: " + e.getMessage());
            running = false;
        }
    }

    /**
     * Captures the current snapshot as a JPEG byte array.
     */
    public synchronized byte[] captureFrameBytes() throws IOException {
        BufferedImage frame = latestFrame;
        if (frame == null && webcam != null && webcam.isOpen()) {
            frame = webcam.getImage();
        }
        if (frame == null) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(frame, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * Stops the webcam capture and releases the camera hardware.
     */
    public synchronized void stop() {
        running = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        if (webcam != null && webcam.isOpen()) {
            try {
                webcam.close();
            } catch (Exception e) {
                System.err.println("Error closing webcam: " + e.getMessage());
            }
        }
    }

    /**
     * Robust conversion from BufferedImage to JavaFX Image.
     * Tries SwingFXUtils first; falls back to ImageIO byte stream if SwingFXUtils is unavailable.
     */
    public static Image convertToFxImage(BufferedImage bImage) {
        if (bImage == null) return null;
        try {
            return SwingFXUtils.toFXImage(bImage, null);
        } catch (Throwable t) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImage, "png", baos);
                return new Image(new ByteArrayInputStream(baos.toByteArray()));
            } catch (IOException ex) {
                return null;
            }
        }
    }
}

