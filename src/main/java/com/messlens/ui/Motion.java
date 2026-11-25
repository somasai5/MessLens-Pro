package com.messlens.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * All the movement in MessLens in one place, so every screen feels the same:
 * pages rise in one block after another, the highlight in the sidebar glides, the page scrolls
 * itself to whatever you click, and cards lift a little under the mouse.
 *
 * One easing curve is used everywhere (fast start, very soft landing) because that is what makes
 * motion read as "smooth" instead of "animated".
 */
public final class Motion {

    private Motion() {}

    /** Fast start, long soft landing. */
    public static final Interpolator SMOOTH = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);

    private static final String RUNNING = "motion.running";
    private static final String WIRED = "motion.wired";

    // ------------------------------------------------------------------ page reveal

    /** The pieces of a page that should arrive one after another (its first-level blocks). */
    private static List<Node> parts(Node view) {
        Node root = view;
        if (root instanceof ScrollPane sp && sp.getContent() != null) root = sp.getContent();
        List<Node> out = new ArrayList<>();
        if (root instanceof Pane p) {
            for (Node c : p.getChildren()) {
                if (c.isManaged() && c.isVisible()) out.add(c);
                if (out.size() >= 9) break;
            }
        }
        return out;
    }

    /** Stops a running reveal and puts everything back to its resting state. */
    public static void settle(Node view) {
        if (view == null) return;
        Object r = view.getProperties().remove(RUNNING);
        if (r instanceof Animation a) a.stop();
        view.setOpacity(1);
        view.setTranslateY(0);
        for (Node n : parts(view)) {
            n.setOpacity(1);
            n.setTranslateY(0);
        }
    }

    /** Page entrance: each block fades up from slightly below, 55 ms after the previous one. */
    public static void reveal(Node view) {
        if (view == null) return;
        settle(view);
        List<Node> parts = parts(view);
        ParallelTransition all = new ParallelTransition();
        if (parts.isEmpty()) {
            all.getChildren().add(riseTimeline(view, 14, 360, 0));
        } else {
            int i = 0;
            for (Node n : parts) all.getChildren().add(riseTimeline(n, 16, 420, i++ * 55L));
        }
        all.setOnFinished(e -> settle(view));
        view.getProperties().put(RUNNING, all);
        all.play();
    }

    private static Timeline riseTimeline(Node n, double dy, double ms, double delayMs) {
        n.setOpacity(0);
        n.setTranslateY(dy);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(n.opacityProperty(), 0),
                        new KeyValue(n.translateYProperty(), dy)),
                new KeyFrame(Duration.millis(ms),
                        new KeyValue(n.opacityProperty(), 1, SMOOTH),
                        new KeyValue(n.translateYProperty(), 0, SMOOTH)));
        t.setDelay(Duration.millis(delayMs));
        return t;
    }

    /** Fade + rise for one node (used for the page heading and small swaps). */
    public static void rise(Node n, double dy, double ms) {
        Timeline t = riseTimeline(n, dy, ms, 0);
        t.setOnFinished(e -> {
            n.setOpacity(1);
            n.setTranslateY(0);
        });
        t.play();
    }

    /** Fade + slide in from the side (inspector panels, sidebar items). */
    public static void slideIn(Node n, double dx, double ms, double delayMs) {
        n.setOpacity(0);
        n.setTranslateX(dx);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(n.opacityProperty(), 0),
                        new KeyValue(n.translateXProperty(), dx)),
                new KeyFrame(Duration.millis(ms),
                        new KeyValue(n.opacityProperty(), 1, SMOOTH),
                        new KeyValue(n.translateXProperty(), 0, SMOOTH)));
        t.setDelay(Duration.millis(delayMs));
        t.setOnFinished(e -> {
            n.setOpacity(1);
            n.setTranslateX(0);
        });
        t.play();
    }

    /** Changes a label's text with a small fade-up so headings never "jump". */
    public static void swapText(Label l, String text) {
        l.setText(text);
        rise(l, 8, 300);
    }

    /** Whole screen (login -> workspace and back) fades in. */
    public static void screenIn(Node root) {
        root.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(320), root);
        f.setToValue(1);
        f.setInterpolator(Interpolator.EASE_OUT);
        f.setOnFinished(e -> root.setOpacity(1));
        f.play();
    }

    /** Short sideways shake for a field that needs attention. */
    public static void shake(Node n) {
        TranslateTransition t = new TranslateTransition(Duration.millis(60), n);
        t.setFromX(0);
        t.setToX(7);
        t.setCycleCount(6);
        t.setAutoReverse(true);
        t.setOnFinished(e -> n.setTranslateX(0));
        t.play();
    }

    // ------------------------------------------------------------------ scrolling

    /** Animates a ScrollPane to a vertical position (0 = top, 1 = bottom). */
    public static void scrollTo(ScrollPane sp, double target) {
        double t = Math.max(0, Math.min(1, target));
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(sp.vvalueProperty(), sp.getVvalue())),
                new KeyFrame(Duration.millis(380), new KeyValue(sp.vvalueProperty(), t, SMOOTH)));
        tl.play();
    }

    /**
     * Scrolls the pane, smoothly, just far enough that {@code target} is fully visible
     * (with a little breathing room). Does nothing when it is already in view.
     */
    public static void scrollIntoView(ScrollPane sp, Node target, double margin) {
        if (sp == null || target == null) return;
        Platform.runLater(() -> {
            Node content = sp.getContent();
            if (content == null || target.getScene() == null) return;
            double viewportH = sp.getViewportBounds().getHeight();
            double contentH = content.getLayoutBounds().getHeight();
            double range = contentH - viewportH;
            if (range <= 1) return;

            Bounds t = target.localToScene(target.getBoundsInLocal());
            Bounds c = content.localToScene(content.getBoundsInLocal());
            double top = t.getMinY() - c.getMinY();
            double bottom = top + t.getHeight();
            double offset = sp.getVvalue() * range;

            double wanted = offset;
            if (bottom + margin > offset + viewportH) wanted = bottom + margin - viewportH;
            if (top - margin < wanted) wanted = Math.max(0, top - margin);
            if (Math.abs(wanted - offset) < 1) return;
            scrollTo(sp, wanted / range);
        });
    }

    // ------------------------------------------------------------------ hover feedback

    /** Buttons and cards lift 2 px under the mouse. Safe to call again for the same nodes. */
    public static void wireHover(Node node) {
        if (!(node instanceof Parent root)) return;
        for (Node n : root.lookupAll(".button")) {
            if (n instanceof Button b && hasClass(b, "btn-")) lift(b, -1.5);
        }
        for (Node n : root.lookupAll(".kpi-card")) lift(n, -3);
        for (Node n : root.lookupAll(".demo-chip")) lift(n, -2);
        for (Node n : root.lookupAll(".photo-tile")) lift(n, -3);
        for (Node n : root.lookupAll(".hostel-card")) lift(n, -3);
        for (Node n : root.lookupAll(".check-card")) lift(n, -3);
    }

    private static boolean hasClass(Node n, String prefix) {
        for (String s : n.getStyleClass()) if (s.startsWith(prefix)) return true;
        return false;
    }

    public static void lift(Node n, double dy) {
        if (n.getProperties().putIfAbsent(WIRED, Boolean.TRUE) != null) return;
        TranslateTransition up = new TranslateTransition(Duration.millis(180), n);
        up.setToY(dy);
        up.setInterpolator(SMOOTH);
        TranslateTransition down = new TranslateTransition(Duration.millis(220), n);
        down.setToY(0);
        down.setInterpolator(SMOOTH);
        n.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (n.isDisabled()) return;
            down.stop();
            up.playFromStart();
        });
        n.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            up.stop();
            down.playFromStart();
        });
    }

    // ------------------------------------------------------------------ ambient

    /** Slow endless drifting (login background orbs). */
    public static void drift(Node n, double dx, double dy, double seconds) {
        TranslateTransition t = new TranslateTransition(Duration.seconds(seconds), n);
        t.setByX(dx);
        t.setByY(dy);
        t.setAutoReverse(true);
        t.setCycleCount(Animation.INDEFINITE);
        t.setInterpolator(Interpolator.EASE_BOTH);
        t.play();
    }
}
