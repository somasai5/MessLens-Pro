package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.model.Hostel;
import com.messlens.model.User;
import com.messlens.util.AlertUtil;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left navigation. Every item opens a real page; the active page is highlighted.
 * The bottom block holds Settings, Help and the signed-in user.
 */
public class SidebarView extends VBox {

    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<String, Label> counts = new HashMap<>();
    private final Map<String, Page> pages = new HashMap<>();

    /** The glowing highlight behind the active item. It glides from item to item. */
    private final Rectangle pill = new Rectangle();
    private Timeline pillMove;
    private String activeId;

    public SidebarView(User user, List<Page> main, List<Page> footer, Consumer<String> onNav) {
        getStyleClass().add("sidebar");
        setPrefWidth(252);
        setMinWidth(252);
        setMaxWidth(252);
        setPadding(new Insets(20, 14, 16, 14));
        setSpacing(4);

        String role = user.getRole() == null ? "STUDENT" : user.getRole().toUpperCase();

        pill.getStyleClass().add("nav-pill");
        pill.setArcWidth(24);
        pill.setArcHeight(24);
        pill.setManaged(false);
        pill.setMouseTransparent(true);
        pill.setVisible(false);
        getChildren().add(pill);              // first child = drawn behind the buttons

        // ----- brand -----
        StackPane mark = new StackPane(Icons.of("lens", 22, "icon-white"));
        mark.getStyleClass().add("brand-mark");
        Label name = Ui.label("MessLens", "brand-name");
        Label caption = Ui.label(roleCaption(role).toUpperCase(), "brand-role");
        HBox brand = Ui.row(10, mark, new VBox(0, name, caption));
        brand.setPadding(new Insets(2, 6, 20, 6));
        getChildren().add(brand);

        // ----- main nav -----
        getChildren().add(Ui.label("WORKSPACE", "nav-section"));
        for (Page p : main) getChildren().add(navItem(p, onNav));

        getChildren().add(Ui.grow());

        // ----- footer nav -----
        getChildren().add(Ui.label("SUPPORT", "nav-section"));
        for (Page p : footer) getChildren().add(navItem(p, onNav));

        getChildren().add(userCard(user, role));
    }

    private StackPane navItem(Page p, Consumer<String> onNav) {
        pages.put(p.id(), p);
        Button b = new Button(p.navLabel());
        b.setGraphic(Icons.of(p.icon(), 20));
        b.setContentDisplay(ContentDisplay.LEFT);
        b.getStyleClass().add("nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> onNav.accept(p.id()));
        buttons.put(p.id(), b);

        Label count = Ui.label("", "nav-count");
        count.setVisible(false);
        count.setManaged(false);
        count.setMouseTransparent(true);
        counts.put(p.id(), count);
        StackPane.setAlignment(count, Pos.CENTER_RIGHT);
        StackPane.setMargin(count, new Insets(0, 12, 0, 0));

        StackPane wrap = new StackPane(b, count);
        // when layout gives this item its final position, put the highlight exactly there
        // (layout values only: translate animations on the items must not move the highlight)
        wrap.layoutYProperty().addListener((o, a, n) -> {
            if (p.id().equals(activeId)) placePill(false);
        });
        wrap.heightProperty().addListener((o, a, n) -> {
            if (p.id().equals(activeId)) placePill(false);
        });
        return wrap;
    }

    private VBox userCard(User user, String role) {
        String uname = user.getUsername() == null ? "User" : user.getUsername();
        Label avatar = Ui.label(uname.substring(0, 1).toUpperCase(), "avatar");

        String hostel = "";
        if (user.getHostelId() != null) {
            Hostel h = new HostelDao().findById(user.getHostelId());
            if (h != null) hostel = h.getName();
        }
        String line = roleCaption(role) + (hostel.isEmpty() ? "" : "  ·  " + hostel);

        Label n = Ui.label(uname, "h3");
        Label r = Ui.label(line, "faint");
        r.setMaxWidth(140);
        VBox who = new VBox(0, n, r);
        HBox top = Ui.row(10, avatar, who);

        Button out = Ui.button("Sign out", "log-out", "btn-secondary", "btn-small");
        out.setMaxWidth(Double.MAX_VALUE);
        out.setOnAction(e -> {
            if (AlertUtil.showConfirmation("Sign out of MessLens?",
                    "You will need to sign in again to get back to your workspace.",
                    "Sign out", "Stay signed in", "log-out", false)) {
                AppNavigator.showLogin();
            }
        });

        VBox card = new VBox(10, top, out);
        card.getStyleClass().add("user-card");
        VBox.setMargin(card, new Insets(8, 0, 0, 0));
        return card;
    }

    private static String roleCaption(String role) {
        switch (role) {
            case "WORKER": return "Kitchen staff";
            case "HEAD": return "Mess head";
            case "ADMIN": return "Administrator";
            case "WARDEN": return "Warden";
            default: return "Student";
        }
    }

    public void setActive(String id) {
        boolean first = activeId == null;
        activeId = id;
        buttons.forEach((k, b) -> {
            b.getStyleClass().remove("active");
            if (k.equals(id)) b.getStyleClass().add("active");
        });
        Platform.runLater(() -> placePill(!first));
    }

    /** Moves the highlight behind the active item (animated) or snaps it there (no animation). */
    private void placePill(boolean animate) {
        Button b = activeId == null ? null : buttons.get(activeId);
        if (b == null || b.getParent() == null || getScene() == null) return;
        // the items are direct children of this sidebar, so their layout position IS the position inside it
        Region wrap = (Region) b.getParent();
        if (wrap.getHeight() <= 0) return;
        double tx = wrap.getLayoutX(), ty = wrap.getLayoutY(), tw = wrap.getWidth(), th = wrap.getHeight();

        if (pillMove != null) pillMove.stop();
        if (!animate || !pill.isVisible()) {
            pill.setX(tx);
            pill.setY(ty);
            pill.setWidth(tw);
            pill.setHeight(th);
            if (!pill.isVisible()) {
                pill.setVisible(true);
                pill.setOpacity(0);
                Timeline fade = new Timeline(new KeyFrame(Duration.millis(420), new KeyValue(pill.opacityProperty(), 1, Motion.SMOOTH)));
                fade.play();
            }
            return;
        }
        pillMove = new Timeline(new KeyFrame(Duration.millis(420),
                new KeyValue(pill.xProperty(), tx, Motion.SMOOTH),
                new KeyValue(pill.yProperty(), ty, Motion.SMOOTH),
                new KeyValue(pill.widthProperty(), tw, Motion.SMOOTH),
                new KeyValue(pill.heightProperty(), th, Motion.SMOOTH)));
        pillMove.play();
    }

    /** Menu items slide in from the left, one after another, when a workspace opens. */
    public void playEntrance() {
        double delay = 40;
        for (Node n : getChildren()) {
            if (n == pill) continue;
            Motion.slideIn(n, -16, 420, delay);
            delay += 45;
        }
    }

    public void refreshBadges() {
        counts.forEach((id, label) -> {
            int n = pages.get(id).badgeCount();
            boolean show = n > 0;
            label.setText(String.valueOf(n));
            label.setVisible(show);
            label.setManaged(show);
        });
    }
}
