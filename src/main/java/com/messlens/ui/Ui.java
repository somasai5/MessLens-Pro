package com.messlens.ui;

import com.messlens.model.Upload;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/** Tiny factory methods so screens read like a layout description instead of styling code. */
public final class Ui {

    private Ui() {}

    // ---------- text ----------

    public static Label label(String text, String... styleClasses) {
        Label l = new Label(calm(text, styleClasses));
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    /** Small caption styles that used to be typed in ALL CAPS. */
    private static final java.util.Set<String> CAPTION_STYLES = java.util.Set.of(
            "caps", "field-label", "eyebrow", "hero-eyebrow", "hero-stat-caption", "nav-section", "brand-role", "stat-cap");

    /** "PHOTOS RECEIVED" -> "Photos received" for caption labels; every other text is left exactly as written. */
    private static String calm(String text, String[] styleClasses) {
        if (text == null || text.length() < 3 || !text.equals(text.toUpperCase(java.util.Locale.ROOT))) return text;
        boolean caption = false;
        for (String c : styleClasses) if (CAPTION_STYLES.contains(c)) { caption = true; break; }
        if (!caption) return text;
        StringBuilder out = new StringBuilder(text.toLowerCase(java.util.Locale.ROOT));
        boolean startOfPart = true;
        for (int i = 0; i < out.length(); i++) {
            char ch = out.charAt(i);
            if (ch == '\u00b7') { startOfPart = true; continue; }
            if (startOfPart && Character.isLetter(ch)) {
                out.setCharAt(i, Character.toUpperCase(ch));
                startOfPart = false;
            }
        }
        return out.toString();
    }

    /** A label that wraps onto several lines instead of being cut off. */
    public static Label wrap(String text, String... styleClasses) {
        Label l = label(text, styleClasses);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static Label badge(String text, String kind) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", "badge-" + kind);
        return l;
    }

    public static Label statusBadge(String status) {
        String s = status == null ? "" : status.toUpperCase();
        switch (s) {
            case "APPROVED": return badge("Approved", "ok");
            case "REJECTED": return badge("Rejected", "bad");
            case "PENDING":  return badge("Pending", "warn");
            default:         return badge(status == null ? "-" : status, "neutral");
        }
    }

    public static String statusKind(String status) {
        String s = status == null ? "" : status.toUpperCase();
        switch (s) {
            case "APPROVED": return "ok";
            case "REJECTED": return "bad";
            case "PENDING":  return "warn";
            default:         return "neutral";
        }
    }

    // ---------- layout ----------

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox row(double spacing, Node... children) {
        HBox h = new HBox(spacing, children);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    public static VBox column(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    public static VBox card(double spacing, Node... children) {
        VBox c = new VBox(spacing, children);
        c.getStyleClass().add("card");
        return c;
    }

    public static Region divider() {
        Region r = new Region();
        r.getStyleClass().add("divider");
        return r;
    }

    /** "Label above control" form field. */
    /** A password box with an eye button on its right: click it to see the password, click again to hide it. */
    public static StackPane passwordWithEye(javafx.scene.control.PasswordField pf) {
        javafx.scene.control.TextField shown = new javafx.scene.control.TextField();
        pf.getStyleClass().add("pass-with-eye");
        shown.getStyleClass().add("pass-with-eye");
        shown.promptTextProperty().bind(pf.promptTextProperty());
        shown.textProperty().bindBidirectional(pf.textProperty());
        shown.setVisible(false);
        shown.setManaged(false);
        shown.setOnAction(e -> { if (pf.getOnAction() != null) pf.getOnAction().handle(e); });
        Button eye = new Button();
        eye.setGraphic(Icons.of("eye", 18));
        eye.getStyleClass().add("login-eye");
        eye.setFocusTraversable(false);
        eye.setOnAction(e -> {
            boolean show = !shown.isVisible();
            shown.setVisible(show);
            shown.setManaged(show);
            pf.setVisible(!show);
            pf.setManaged(!show);
            eye.setGraphic(Icons.of(show ? "eye-off" : "eye", 18));
            (show ? shown : pf).requestFocus();
        });
        StackPane stack = new StackPane(pf, shown, eye);
        StackPane.setAlignment(eye, Pos.CENTER_RIGHT);
        return stack;
    }

    public static VBox field(String labelText, Node control) {
        VBox v = new VBox(6, label(labelText, "field-label"), control);
        return v;
    }

    /** Vertical scroll container: content always fits the width, and nothing is ever cut off vertically. */
    public static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);   // no bar on the right edge; wheel and trackpad still scroll
        return sp;
    }

    // ---------- controls ----------

    public static Button button(String text, String icon, String... styleClasses) {
        Button b = new Button(text);
        if (icon != null) b.setGraphic(Icons.of(icon, 18));
        b.getStyleClass().addAll(styleClasses);
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    public static ToggleButton segment(String text) {
        ToggleButton t = new ToggleButton(text);
        t.getStyleClass().add("segment");
        t.setMinWidth(Region.USE_PREF_SIZE);   // never truncate to "All..." when the row is tight
        return t;
    }


    // ---------- dashboard widgets ----------

    /** Rounded tinted square holding an icon. tone: primary, ok, warn, bad, neutral or glass. */
    public static StackPane iconTile(String icon, String tone) {
        StackPane s;
        if ("neutral".equals(tone)) s = new StackPane(Icons.of(icon, 20));
        else if ("glass".equals(tone)) s = new StackPane(Icons.of(icon, 20, "icon-white"));
        else s = new StackPane(Icons.of(icon, 20, "icon-" + tone));
        s.getStyleClass().addAll("icon-tile", "tile-" + tone);
        return s;
    }

    /** Dashboard number card: icon tile, big number, caption and a small hint line. */
    public static VBox kpiCard(String caption, String number, String sub, String icon, String tone) {
        Label num = label(number, "kpi-number");
        HBox top = new HBox(10, iconTile(icon, tone), grow());
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(4, top, num, label(caption, "caps"), label(sub, "faint"));
        VBox.setMargin(num, new Insets(10, 0, 0, 0));
        card.getStyleClass().add("kpi-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Row of four equally wide cards. */
    public static javafx.scene.layout.GridPane kpiGrid(Node... cards) {
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(16);
        for (int i = 0; i < cards.length; i++) {
            javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
            c.setPercentWidth(100.0 / cards.length);
            c.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(c);
            g.add(cards[i], i, 0);
        }
        return g;
    }

    /**
     * Circular progress ring with a centre label.
     * @param fraction 0..1, @param tone "", "ok", "warn", "bad" (ignored when light)
     */
    public static StackPane ring(double fraction, double size, double stroke, String centerText, String tone, boolean light) {
        double f = Math.max(0, Math.min(1, fraction));
        double c = size / 2;
        double r = (size - stroke) / 2;
        Circle track = new Circle(c, c, r);
        track.getStyleClass().add(light ? "ring-track-light" : "ring-track");
        track.setStrokeWidth(stroke);
        Pane pane = new Pane(track);
        if (f > 0.001) {
            Arc arc = new Arc(c, c, r, r, 90, -360 * Math.min(f, 0.9999));
            arc.setType(ArcType.OPEN);
            arc.setStrokeWidth(stroke);
            arc.getStyleClass().add(light ? "ring-arc-light" : "ring-arc");
            if (!light && tone != null && !tone.isEmpty()) arc.getStyleClass().add("ring-arc-" + tone);
            pane.getChildren().add(arc);
        }
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        Label l = label(centerText, light ? "ring-number-light" : "ring-number");
        l.setMinWidth(Region.USE_PREF_SIZE);
        l.setWrapText(false);
        l.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        StackPane out = new StackPane(pane, l);
        out.setMinSize(size, size);
        out.setPrefSize(size, size);
        out.setMaxSize(size, size);
        return out;
    }

    /** Ingredients - Cooking - Serving tracker for one dish (uploads of the same dish). */
    public static HBox stageTrack(java.util.List<Upload> group, String[] stages) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        boolean prevDone = false;
        for (int i = 0; i < stages.length; i++) {
            String status = null;
            for (Upload u : group) {
                if (stages[i].equalsIgnoreCase(u.getStage())) { status = u.getStatus(); break; }
            }
            String kind = status == null ? "none" : statusKind(status);
            StackPane dot = new StackPane();
            dot.getStyleClass().add("track-dot");
            if (!"none".equals(kind) && !"neutral".equals(kind)) dot.getStyleClass().add("track-dot-" + kind);
            if ("ok".equals(kind)) dot.getChildren().add(Icons.of("check", 14, "icon-white"));
            else if ("warn".equals(kind)) dot.getChildren().add(Icons.of("clock", 14, "icon-white"));
            else if ("bad".equals(kind)) dot.getChildren().add(Icons.of("x", 14, "icon-white"));
            else dot.getChildren().add(label(String.valueOf(i + 1), "faint"));

            String sub = status == null ? "Not sent" : Fmt.shortStatus(status);
            Label subL = label(sub, "none".equals(kind) || "neutral".equals(kind) ? "track-sub" : "track-sub-" + kind);
            VBox text = new VBox(0, label(stages[i], "track-name"), subL);
            if (i > 0) {
                Region line = new Region();
                line.getStyleClass().add("track-line");
                if (prevDone) line.getStyleClass().add("track-line-done");
                box.getChildren().add(line);
            }
            box.getChildren().add(new HBox(8, dot, text));
            ((HBox) box.getChildren().get(box.getChildren().size() - 1)).setAlignment(Pos.CENTER_LEFT);
            prevDone = "ok".equals(kind);
        }
        return box;
    }

    // ---------- empty states ----------

    public static VBox emptyState(String icon, String title, String message) {
        StackPane circle = new StackPane(Icons.of(icon, 30, "icon-primary"));
        circle.getStyleClass().add("empty-circle");
        Label t = label(title, "h3");
        Label m = wrap(message, "muted");
        m.setMaxWidth(320);
        m.setAlignment(Pos.CENTER);
        m.setStyle("-fx-text-alignment: center;");
        VBox v = new VBox(10, circle, t, m);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(36, 20, 36, 20));
        return v;
    }

    // ---------- photos ----------

    /** Rounded thumbnail of an upload (or a neutral placeholder when the file is missing). */
    public static StackPane thumbnail(Upload u, double w, double h) {
        StackPane box = new StackPane();
        box.getStyleClass().add("photo-frame");
        box.setMinSize(w, h);
        box.setPrefSize(w, h);
        box.setMaxSize(w, h);
        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        box.setClip(clip);
        setThumb(box, u, w, h);
        return box;
    }

    public static void setThumb(StackPane box, Upload u, double w, double h) {
        box.getChildren().clear();
        Image img = u == null ? null : Media.thumb(u.getImagePath(), w, h);
        if (img == null) {
            box.getChildren().add(Icons.of("image", Math.min(w, h) * 0.45, "icon-faint"));
            return;
        }
        ImageView iv = new ImageView(img);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        box.getChildren().add(iv);
    }

    /**
     * Image that always fits inside the given pane (keeps aspect ratio, resizes with the window).
     * The image is not part of the pane's layout, so its size can never push the pane wider than
     * the space it was given; it is simply re-centred whenever anything changes.
     */
    public static ImageView fittedImage(StackPane pane, double padding) {
        ImageView iv = new ImageView();
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setManaged(false);
        iv.fitWidthProperty().bind(pane.widthProperty().subtract(padding));
        iv.fitHeightProperty().bind(pane.heightProperty().subtract(padding));
        Runnable center = () -> {
            double w = iv.getLayoutBounds().getWidth();
            double h = iv.getLayoutBounds().getHeight();
            iv.setLayoutX((pane.getWidth() - w) / 2);
            iv.setLayoutY((pane.getHeight() - h) / 2);
        };
        pane.widthProperty().addListener((o, a, b) -> center.run());
        pane.heightProperty().addListener((o, a, b) -> center.run());
        iv.imageProperty().addListener((o, a, b) -> center.run());
        iv.layoutBoundsProperty().addListener((o, a, b) -> center.run());
        return iv;
    }
}
