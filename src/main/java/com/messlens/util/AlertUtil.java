package com.messlens.util;

import com.messlens.ui.Dialogs;
import javafx.application.Platform;

import java.util.Optional;

/**
 * Utility for user-friendly dialogs and alerts.
 * Keeps stack traces away from end-users while presenting actionable error and status messages.
 *
 * Everything here is shown INSIDE the main window (see {@link Dialogs}), so it also works in macOS
 * full screen, where a separate dialog window would open on another desktop.
 */
public class AlertUtil {

    public static void showInfo(String title, String message) {
        later(() -> Dialogs.notice(Dialogs.Tone.INFO, title, message));
    }

    public static void showWarning(String title, String message) {
        later(() -> Dialogs.notice(Dialogs.Tone.WARN, title, message));
    }

    public static void showError(String title, String message) {
        later(() -> Dialogs.notice(Dialogs.Tone.ERROR, title, message));
    }

    /** Simple confirm / cancel question. */
    public static boolean showConfirmation(String title, String message) {
        return showConfirmation(title, message, "Confirm", "Cancel", "help", false);
    }

    /**
     * Question with your own button labels and icon.
     *
     * @param danger true for destructive actions: red confirm button, and Cancel is focused first
     */
    public static boolean showConfirmation(String title, String message, String confirmText, String cancelText,
                                           String icon, boolean danger) {
        return Dialogs.confirm(title, message, confirmText, cancelText, icon, danger);
    }

    /** Asks for a line of text. Empty when the person cancels. */
    public static Optional<String> askText(String title, String message, String placeholder, String confirmText,
                                           String icon, boolean danger, boolean requireText) {
        return Dialogs.prompt(title, message, placeholder, confirmText, icon, danger, requireText);
    }

    private static void later(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}
