package com.messlens.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * MessLens dialogs, drawn INSIDE the main window instead of in a separate OS window.
 *
 * Why: a normal JavaFX Alert is its own window. When the app is in macOS full screen, a window without an
 * owner opens on a different desktop (Space), so the person sees a black screen with a floating box.
 * These dialogs are a dimmed overlay on top of the current page, so they always appear exactly where
 * the person is looking: windowed, maximised or full screen.
 *
 * They are blocking (the call returns after the person has answered), like Alert.showAndWait().
 */
public final class Dialogs {

    /** Visual tone: decides icon colour. */
    public enum Tone { INFO, WARN, ERROR, ASK, DANGER }

    private Dialogs() {}

    // ------------------------------------------------------------------ public API

    /** True when the current window can host an overlay (a scene whose root is a layout pane). */
    public static boolean canHost() {
        Stage st = AppNavigator.getPrimaryStage();
        return st != null && st.getScene() != null && st.getScene().getRoot() instanceof Pane;
    }

    /** Two-button question. Returns true when the person confirms. */
    public static boolean confirm(String title, String message, String confirmText, String cancelText,
                                  String icon, boolean danger) {
        if (!canHost()) return legacyConfirm(title, message);
        Object r = show(h -> {
            Button cancel = button(cancelText, "btn-secondary", h, Boolean.FALSE);
            Button ok = button(confirmText, danger ? "btn-danger-solid" : "btn-primary", h, Boolean.TRUE);
            Built b = new Built();
            b.content = layout(danger ? Tone.DANGER : Tone.ASK, icon, title, message, null, cancel, ok);
            b.focus = danger ? cancel : ok;          // a destructive action is never the default
            b.onEnter = () -> Boolean.TRUE;
            return b;
        }, Boolean.FALSE);
        return Boolean.TRUE.equals(r);
    }

    /** One-button message. */
    public static void notice(Tone tone, String title, String message) {
        if (!canHost()) { legacyNotice(tone, title, message); return; }
        show(h -> {
            Button ok = button("Got it", "btn-primary", h, Boolean.TRUE);
            Built b = new Built();
            b.content = layout(tone, defaultIcon(tone), title, message, null, ok);
            b.focus = ok;
            b.onEnter = () -> Boolean.TRUE;
            return b;
        }, Boolean.TRUE);
    }

    /** Asks for one line of text. Empty when cancelled. When requireText is true, confirm stays disabled until something is typed. */
    public static Optional<String> prompt(String title, String message, String placeholder, String confirmText,
                                          String icon, boolean danger, boolean requireText) {
        if (!canHost()) return legacyPrompt(title, message);
        Object r = show(h -> {
            TextField field = new TextField();
            field.setPromptText(placeholder == null ? "" : placeholder);
            Button cancel = button("Cancel", "btn-secondary", h, null);
            Button ok = new Button(confirmText);
            ok.getStyleClass().addAll(danger ? "btn-danger-solid" : "btn-primary");
            ok.setMinWidth(Region.USE_PREF_SIZE);
            ok.setOnAction(e -> h.close(field.getText() == null ? "" : field.getText().trim()));
            if (requireText) ok.disableProperty().bind(field.textProperty().isEmpty());
            Built b = new Built();
            b.content = layout(danger ? Tone.DANGER : Tone.ASK, icon, title, message, field, cancel, ok);
            b.focus = field;
            b.onEnter = () -> {
                String t = field.getText() == null ? "" : field.getText().trim();
                return (requireText && t.isEmpty()) ? NO_ANSWER : t;
            };
            return b;
        }, null);
        return r instanceof String s ? Optional.of(s) : Optional.empty();
    }

    /**
     * Shows any content as a card on top of the page (used by the photo viewer). Blocks until closed.
     * Esc and a click on the dim area close it. The builder receives the function that closes the card.
     */
    public static void present(double width, double height, java.util.function.Function<Runnable, Node> builder) {
        if (!canHost()) return;
        show(h -> {
            Node inner = builder.apply(() -> h.close(null));
            VBox card = new VBox(inner);
            card.getStyleClass().add("modal-card");
            VBox.setVgrow(inner, Priority.ALWAYS);
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.setArcWidth(44);
            clip.setArcHeight(44);
            clip.widthProperty().bind(card.widthProperty());
            clip.heightProperty().bind(card.heightProperty());
            card.setClip(clip);
            Built b = new Built();
            b.content = card;
            b.width = width;
            b.height = height;
            b.onEnter = () -> Boolean.TRUE;
            return b;
        }, null);
    }

    // ------------------------------------------------------------------ layout

    private static final Object NO_ANSWER = new Object();

    private static String defaultIcon(Tone t) {
        switch (t) {
            case WARN: return "alert";
            case ERROR: return "alert";
            case DANGER: return "trash";
            case ASK: return "help";
            default: return "info";
        }
    }

    private static String toneName(Tone t) {
        switch (t) {
            case WARN: return "warn";
            case ERROR:
            case DANGER: return "bad";
            default: return "primary";
        }
    }

    private static Button button(String text, String style, Handle h, Object result) {
        Button b = new Button(text);
        b.getStyleClass().addAll(style);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> h.close(result));
        return b;
    }

    private static VBox layout(Tone tone, String icon, String title, String message, Node extra, Button... buttons) {
        String t = toneName(tone);
        StackPane badge = new StackPane(Icons.of(icon, 24, "icon-" + t));
        badge.getStyleClass().addAll("modal-icon", "tile-" + t);

        Label heading = new Label(title);
        heading.getStyleClass().add("modal-title");
        heading.setWrapText(true);
        heading.setMaxWidth(Double.MAX_VALUE);

        VBox text = new VBox(6, heading);
        if (message != null && !message.isBlank()) {
            Label body = new Label(message);
            body.getStyleClass().add("modal-text");
            body.setWrapText(true);
            body.setMaxWidth(Double.MAX_VALUE);
            text.getChildren().add(body);
        }
        if (extra != null) {
            VBox.setMargin(extra, new Insets(8, 0, 0, 0));
            text.getChildren().add(extra);
        }
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox body = new HBox(16, badge, text);
        body.setAlignment(Pos.TOP_LEFT);
        body.getStyleClass().add("modal-body");

        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("modal-footer");
        footer.getChildren().addAll(buttons);

        VBox card = new VBox(body, footer);
        card.getStyleClass().add("modal-card");
        return card;
    }

    // ------------------------------------------------------------------ engine

    private static final class Built {
        Node content;
        Node focus;
        Supplier<Object> onEnter;
        double width = 440;
        double height = -1;        // > 0: tall card (viewer); it is capped to the window height
    }

    /** Open modals, newest first: only the newest one reacts to the keyboard. */
    private static final java.util.ArrayDeque<Object> ACTIVE = new java.util.ArrayDeque<>();

    private interface Builder { Built build(Handle h); }

    private static final class Handle {
        private java.util.function.Consumer<Object> closer;
        void close(Object value) { if (closer != null) closer.accept(value); }
    }

    /**
     * Shows the overlay and blocks (nested event loop) until it is closed.
     * Esc / click outside / the window changing underneath = cancelValue.
     */
    private static Object show(Builder builder, Object cancelValue) {
        if (!Platform.isFxApplicationThread()) {
            java.util.concurrent.FutureTask<Object> f = new java.util.concurrent.FutureTask<>(() -> show(builder, cancelValue));
            Platform.runLater(f);
            try { return f.get(); } catch (Exception e) { Thread.currentThread().interrupt(); return cancelValue; }
        }

        Stage stage = AppNavigator.getPrimaryStage();
        Scene scene = stage.getScene();
        Pane host = (Pane) scene.getRoot();

        Handle handle = new Handle();
        Built built = builder.build(handle);
        VBox card = (VBox) built.content;
        card.setPrefWidth(built.width);
        card.setMaxWidth(built.width);
        if (built.height > 0) {
            // tall card: as high as asked, but never taller than the window
            card.setPrefHeight(built.height);
            card.maxHeightProperty().bind(host.heightProperty().subtract(48));
        } else {
            card.setMaxHeight(Region.USE_PREF_SIZE);
        }

        StackPane overlay = new StackPane(card);
        overlay.getStyleClass().add("modal-scrim");
        overlay.setAlignment(Pos.CENTER);
        overlay.setPadding(new Insets(24));
        overlay.setManaged(false);                       // sits on top of the page without disturbing its layout
        overlay.resizeRelocate(0, 0, host.getWidth(), host.getHeight());
        ChangeListener<Number> sizer = (o, a, b) -> overlay.resizeRelocate(0, 0, host.getWidth(), host.getHeight());
        host.widthProperty().addListener(sizer);
        host.heightProperty().addListener(sizer);

        // click on the dim area = cancel; click on the card itself does nothing
        overlay.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == overlay) handle.close(cancelValue);
            e.consume();
        });

        Node previousFocus = scene.getFocusOwner();
        Object loopKey = new Object();
        boolean[] closed = {false};
        boolean[] looping = {false};
        Object[] earlyResult = {cancelValue};

        // keyboard: Esc cancels, Enter confirms, nothing leaks to the page underneath
        EventHandler<KeyEvent> keys = e -> {
            if (ACTIVE.peek() != loopKey) return;            // a newer dialog is on top: it handles the keys
            Node target = e.getTarget() instanceof Node n ? n : null;
            boolean inside = isInside(target, card);
            if (e.getEventType() == KeyEvent.KEY_PRESSED) {
                if (e.getCode() == KeyCode.ESCAPE) {
                    handle.close(cancelValue);
                    e.consume();
                    return;
                }
                if (e.getCode() == KeyCode.ENTER) {
                    if (inside && target instanceof Button b && !b.isDisabled()) {
                        b.fire();
                    } else {
                        Object v = built.onEnter == null ? cancelValue : built.onEnter.get();
                        if (v != NO_ANSWER) handle.close(v);
                    }
                    e.consume();
                    return;
                }
            }
            if (!inside) e.consume();
        };
        scene.addEventFilter(KeyEvent.ANY, keys);

        // if the window switches screens under us (session timeout -> login), just cancel
        ChangeListener<javafx.scene.Parent> rootWatch = (o, a, b) -> handle.close(cancelValue);
        scene.rootProperty().addListener(rootWatch);

        handle.closer = value -> {
            if (closed[0]) return;
            closed[0] = true;
            ACTIVE.remove(loopKey);
            scene.removeEventFilter(KeyEvent.ANY, keys);
            scene.rootProperty().removeListener(rootWatch);
            host.widthProperty().removeListener(sizer);
            host.heightProperty().removeListener(sizer);

            FadeTransition out = new FadeTransition(Duration.millis(110), overlay);
            out.setToValue(0);
            out.setOnFinished(e -> host.getChildren().remove(overlay));
            out.play();

            if (previousFocus != null && previousFocus.getScene() == scene) previousFocus.requestFocus();
            if (looping[0]) Platform.exitNestedEventLoop(loopKey, value);
            else earlyResult[0] = value;
        };

        ACTIVE.push(loopKey);
        host.getChildren().add(overlay);
        overlay.toFront();

        // soft entrance: dim fades in while the card eases up from 94%
        overlay.setOpacity(0);
        card.setScaleX(0.94);
        card.setScaleY(0.94);
        FadeTransition in = new FadeTransition(Duration.millis(140), overlay);
        in.setToValue(1);
        ScaleTransition pop = new ScaleTransition(Duration.millis(170), card);
        pop.setToX(1);
        pop.setToY(1);
        new ParallelTransition(in, pop).play();

        Platform.runLater(() -> { if (built.focus != null) built.focus.requestFocus(); });

        try {
            looping[0] = true;
            return Platform.enterNestedEventLoop(loopKey);
        } catch (IllegalStateException notAllowedNow) {
            // JavaFX refuses a nested loop while an animation/layout pass is running: do not leave a stuck overlay
            looping[0] = false;
            handle.close(cancelValue);
            return cancelValue;
        }
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) if (n == ancestor) return true;
        return false;
    }

    // ------------------------------------------------------------------ fallbacks
    // Only used before any screen exists (nothing to draw on). Still owned by the main window when there is one.

    private static void own(javafx.scene.control.Dialog<?> d) {
        Stage st = AppNavigator.getPrimaryStage();
        if (st != null && st.isShowing()) d.initOwner(st);
        String css = Theme.url();
        if (css != null) d.getDialogPane().getStylesheets().add(css);
    }

    private static boolean legacyConfirm(String title, String message) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        own(a);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    private static void legacyNotice(Tone tone, String title, String message) {
        javafx.scene.control.Alert.AlertType type = tone == Tone.ERROR ? javafx.scene.control.Alert.AlertType.ERROR
                : tone == Tone.WARN ? javafx.scene.control.Alert.AlertType.WARNING
                : javafx.scene.control.Alert.AlertType.INFORMATION;
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        own(a);
        a.showAndWait();
    }

    private static Optional<String> legacyPrompt(String title, String message) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(message);
        own(d);
        return d.showAndWait();
    }
}
