package com.messlens.ui;

import com.messlens.model.User;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The frame every signed-in user sees: sidebar on the left, page heading on top,
 * and the currently selected {@link Page} filling the rest of the window.
 */
public class AppShell extends BorderPane {

    private final User user;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final List<Page> mainPages = new ArrayList<>();
    private final List<Page> footerPages = new ArrayList<>();

    private SidebarView sidebar;
    private final StackPane host = new StackPane();
    private final Label titleLabel = Ui.label("", "h1");
    private final Label subtitleLabel = Ui.wrap("", "muted");
    private final Label eyebrowLabel = Ui.label("", "eyebrow");
    private final HBox actionsBox = new HBox(10);
    private final VBox column = new VBox();   // heading + page, centred and width-capped
    private String currentId;
    private Animation leaving;          // the old page fading out

    public AppShell(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public void addMainPage(Page p) {
        pages.put(p.id(), p);
        mainPages.add(p);
    }

    public void addFooterPage(Page p) {
        pages.put(p.id(), p);
        footerPages.add(p);
    }

    /** Builds the frame and opens the first page. Call after all pages were added. */
    public void start(String firstPageId) {
        sidebar = new SidebarView(user, mainPages, footerPages, this::show);

        eyebrowLabel.setVisible(false);      // the sidebar already shows where you are; no repeated label above the title
        eyebrowLabel.setManaged(false);
        VBox headings = new VBox(4, eyebrowLabel, titleLabel, subtitleLabel);
        HBox.setHgrow(headings, Priority.ALWAYS);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH));
        HBox dateChip = Ui.row(8, Icons.of("calendar", 16, "icon-primary"), Ui.label(today, "h3"));
        dateChip.getStyleClass().add("date-chip");
        dateChip.setMinWidth(Region.USE_PREF_SIZE);     // never shortened to "Fri, 13 Nov 2..."
        dateChip.setAlignment(Pos.CENTER);
        HBox header = new HBox(16, headings, actionsBox, dateChip);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setFillHeight(false);          // buttons and the date chip keep their own height and sit on one centre line
        actionsBox.setFillHeight(false);
        header.getStyleClass().add("page-header");

        VBox.setVgrow(host, Priority.ALWAYS);
        // soft fade where content scrolls under the heading (instead of a hard cut)
        javafx.scene.layout.Region fade = new javafx.scene.layout.Region();
        fade.getStyleClass().add("scroll-fade");
        fade.setMouseTransparent(true);
        fade.setPrefHeight(8);
        fade.setMaxHeight(8);
        StackPane.setAlignment(fade, Pos.TOP_CENTER);
        StackPane hostWithFade = new StackPane(host, fade);
        VBox.setVgrow(hostWithFade, Priority.ALWAYS);

        column.getChildren().addAll(header, hostWithFade);
        StackPane canvas = new StackPane(column);
        canvas.getStyleClass().add("page-canvas");
        canvas.setAlignment(Pos.TOP_CENTER);

        setLeft(sidebar);
        setCenter(canvas);
        setMinWidth(0);
        show(firstPageId);
        sidebar.playEntrance();
        Motion.wireHover(sidebar);
    }

    public void show(String id) {
        Page p = pages.get(id);
        if (p == null) return;
        if (currentId != null && !currentId.equals(id)) {
            Page old = pages.get(currentId);
            if (old != null) old.onHide();
        }
        currentId = id;

        Motion.swapText(titleLabel, p.title());
        Motion.swapText(eyebrowLabel, p.navLabel().toUpperCase());
        Motion.swapText(subtitleLabel, p.subtitle());
        actionsBox.getChildren().clear();
        Node actions = p.headerActions();
        if (actions != null) {
            if (actions instanceof HBox row) {          // several buttons: line them up in the middle, on the right
                row.setAlignment(Pos.CENTER_RIGHT);
                row.setFillHeight(false);
            }
            actionsBox.getChildren().add(actions);
            Motion.rise(actionsBox, 8, 320);
        }

        sidebar.setActive(id);
        p.onShow();
        sidebar.refreshBadges();

        column.setMaxWidth(p.maxWidth());
        Node view = p.view();
        swapPage(view);

        if (AppNavigator.getPrimaryStage() != null) {
            AppNavigator.getPrimaryStage().setTitle("MessLens  -  " + p.title());
        }
    }

    /**
     * Old page fades out quickly (90 ms), the new one rises in block by block.
     * Clicking another item mid-way simply restarts from wherever the screen is.
     */
    private void swapPage(Node view) {
        if (leaving != null) {
            leaving.stop();
            leaving = null;
        }
        Node old = host.getChildren().isEmpty() ? null : host.getChildren().get(0);
        Runnable enter = () -> {
            if (old != null) old.setOpacity(1);
            host.getChildren().setAll(view);
            Motion.reveal(view);
            Motion.wireHover(view);
        };
        if (old == null || old == view) {
            enter.run();
            return;
        }
        FadeTransition out = new FadeTransition(Duration.millis(90), old);
        out.setToValue(0);
        out.setOnFinished(e -> {
            leaving = null;
            enter.run();
        });
        leaving = out;
        out.play();
    }

    public String currentPageId() {
        return currentId;
    }

    /** Re-reads the small counters in the sidebar (e.g. number of photos waiting for review). */
    public void refreshBadges() {
        if (sidebar != null) sidebar.refreshBadges();
    }

    public void dispose() {
        for (Page p : pages.values()) p.dispose();
    }
}
