package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.model.Hostel;
import com.messlens.model.User;
import com.messlens.service.AuthService;
import com.messlens.util.Config;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.File;

/**
 * Account, password and photo source. A profile banner on top, then two balanced columns:
 * the password form on the left, tips / photo source / about on the right, so the whole width is used.
 */
public class SettingsPage implements Page {

    private final User user;
    private final VBox content = new VBox(20);
    private final Node view;

    public SettingsPage(User user) {
        this.user = user;
        build();
        content.setPadding(new Insets(4, 32, 28, 32));
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "settings"; }
    @Override public String navLabel() { return "Settings"; }
    @Override public String icon() { return "sliders"; }
    @Override public String title() { return "Settings"; }
    @Override public String subtitle() { return "Your account, password and how this computer is set up."; }
    @Override public Node view() { return view; }
    @Override public double maxWidth() { return 1180; }

    private void build() {
        content.getChildren().add(profileHero());

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }
        Node pw = passwordCard();
        VBox right = new VBox(20);
        right.getChildren().add(tipsCard());
        if ("WORKER".equalsIgnoreCase(user.getRole())) right.getChildren().add(photoSourceCard());
        right.getChildren().add(aboutCard());
        grid.add(pw, 0, 0);
        grid.add(right, 1, 0);
        GridPane.setVgrow(pw, Priority.NEVER);
        ((Region) pw).setMaxHeight(Region.USE_PREF_SIZE);
        GridPane.setValignment(pw, javafx.geometry.VPos.TOP);
        content.getChildren().add(grid);
    }

    // ---------------------------------------------------------------- profile banner

    private Node profileHero() {
        String hostel = "All hostels";
        if (user.getHostelId() != null) {
            Hostel h = new HostelDao().findById(user.getHostelId());
            if (h != null) hostel = h.getName();
        }
        String role = roleName();
        String uname = user.getUsername() == null ? "User" : user.getUsername();

        Label avatar = Ui.label(uname.substring(0, 1).toUpperCase(), "avatar-xl");
        Label name = Ui.label(uname, "hero-title");
        Label sub = Ui.label("Manage your account and keep it secure.", "hero-text");
        HBox chips = new HBox(8,
                chip("user", role),
                chip("home", hostel + (user.getHostelId() == null ? "" : " hostel")));
        chips.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(6, name, sub, chips);
        VBox.setMargin(chips, new Insets(6, 0, 0, 0));

        HBox hero = new HBox(22, avatar, text, Ui.grow());
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("profile-hero");
        return hero;
    }

    private static HBox chip(String icon, String text) {
        HBox c = new HBox(7, Icons.of(icon, 14, "icon-white"), Ui.label(text, "hero-chip-text"));
        c.setAlignment(Pos.CENTER_LEFT);
        c.getStyleClass().add("hero-chip");
        return c;
    }

    private String roleName() {
        return "WORKER".equalsIgnoreCase(user.getRole()) ? "Kitchen staff"
                : "HEAD".equalsIgnoreCase(user.getRole()) ? "Mess head"
                : "ADMIN".equalsIgnoreCase(user.getRole()) ? "Administrator"
                : "WARDEN".equalsIgnoreCase(user.getRole()) ? "Warden" : "Student";
    }

    // ---------------------------------------------------------------- password

    private Node passwordCard() {
        PasswordField current = new PasswordField();
        current.setPromptText("Current password");
        PasswordField next = new PasswordField();
        next.setPromptText("At least 6 characters");
        PasswordField again = new PasswordField();
        again.setPromptText("Type the new password again");

        VBox newBox = new VBox(8, Ui.field("New password", Ui.passwordWithEye(next)));

        Label msg = Ui.wrap("", "field-error");
        msg.setVisible(false);
        msg.setManaged(false);

        Button save = Ui.button("Update password", "key", "btn-primary");
        save.setOnAction(e -> {
            msg.setVisible(true);
            msg.setManaged(true);
            if (!next.getText().equals(again.getText())) {
                showMsg(msg, "The two new passwords are different.", false);
                Motion.shake(again);
                return;
            }
            try {
                new AuthService().changePassword(current.getText(), next.getText());
                current.clear();
                next.clear();
                again.clear();
                showMsg(msg, "Password updated. Use the new one next time you sign in.", true);
                UiFx.toast("Password changed", true);
            } catch (Exception ex) {
                showMsg(msg, ex.getMessage(), false);
            }
        });

        VBox card = Ui.card(16,
                row("lock", "Change password", "Pick something only you would know."),
                Ui.divider(),
                Ui.field("Current password", Ui.passwordWithEye(current)),
                newBox,
                Ui.field("Repeat new password", Ui.passwordWithEye(again)),
                msg, save);
        return card;
    }

    private static int strength(String pw) {
        if (pw == null || pw.isEmpty()) return 0;
        if (pw.length() < 8) return 1;
        int s = 2;
        if (pw.length() >= 12) s++;
        boolean letter = pw.chars().anyMatch(Character::isLetter);
        boolean digit = pw.chars().anyMatch(Character::isDigit);
        boolean other = pw.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        boolean upper = pw.chars().anyMatch(Character::isUpperCase);
        if ((letter && digit) || other) s++;
        if (upper && other && pw.length() >= 12) s++;
        return Math.min(5, s);
    }

    private static void showMsg(Label l, String text, boolean ok) {
        l.setText(text);
        l.getStyleClass().removeAll("field-error", "field-ok");
        l.getStyleClass().add(ok ? "field-ok" : "field-error");
    }

    private static Node row(String icon, String title, String sub) {
        HBox r = new HBox(12, Ui.iconTile(icon, "primary"), new VBox(1, Ui.label(title, "h2"), Ui.label(sub, "muted")));
        r.setAlignment(Pos.CENTER_LEFT);
        return r;
    }

    // ---------------------------------------------------------------- tips

    private Node tipsCard() {
        return Ui.card(12,
                row("shield", "A good password", "Easy to remember, hard to guess."),
                Ui.divider(),
                tip("Use 8 or more characters."),
                tip("A short phrase of three or four words works better than one clever word."),
                tip("Do not reuse a password from another website or app."));
    }

    private static Node tip(String text) {
        StackPane dot = new StackPane(Icons.of("check", 12, "icon-ok"));
        dot.getStyleClass().add("tip-dot-ok");
        Label l = Ui.wrap(text, "body");
        HBox.setHgrow(l, Priority.ALWAYS);
        HBox r = new HBox(12, dot, l);
        r.setAlignment(Pos.CENTER_LEFT);
        return r;
    }

    // ---------------------------------------------------------------- photo source

    private Node photoSourceCard() {
        CheckBox cb = new CheckBox("Choose photos from this computer instead of the webcam");
        cb.setSelected(Config.isDevMode());
        cb.setWrapText(true);
        cb.setOnAction(e -> {
            Config.setDevMode(cb.isSelected());
            UiFx.toast(cb.isSelected() ? "Photo picker enabled on the Capture page" : "Webcam only", true);
        });
        return Ui.card(12,
                row("camera", "Photo source", "Where evidence photos come from."),
                Ui.divider(),
                Ui.wrap("The Capture page uses the webcam. If this computer has no camera, the photo picker "
                        + "appears automatically. Turn this on to always show it (useful for testing with sample photos).", "muted"),
                cb);
    }

    // ---------------------------------------------------------------- about

    private Node aboutCard() {
        File ev = new File(Config.getEvidenceDir());
        return Ui.card(12,
                row("info", "About MessLens", "Version 1.0"),
                Ui.divider(),
                Ui.wrap("Mess transparency and evidence verification: every cooking stage is photographed, "
                        + "fingerprinted and reviewed before students see it.", "body"),
                new VBox(4, Ui.label("PHOTOS ARE SAVED IN", "caps"), Ui.wrap(ev.getAbsolutePath(), "mono", "muted")));
    }
}
