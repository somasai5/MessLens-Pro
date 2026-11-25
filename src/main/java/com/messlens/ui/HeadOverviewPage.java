package com.messlens.ui;

import com.messlens.model.MessMetrics;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.UploadService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mess head "Overview": the four numbers that matter plus a board of today's dishes. */
public class HeadOverviewPage implements Page {

    private static final String[] STAGES = {"Ingredients", "Cooking", "Serving"};

    private final User user;
    private final AppShell shell;
    private final UploadService uploads = new UploadService();
    private final int hostelId;

    private final VBox content = new VBox(18);
    private final Node view;

    public HeadOverviewPage(User user, AppShell shell) {
        this.user = user;
        this.shell = shell;
        this.hostelId = user.getHostelId() != null ? user.getHostelId() : 1;
        content.setPadding(new Insets(4, 32, 30, 32));
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "overview"; }
    @Override public String navLabel() { return "Overview"; }
    @Override public String icon() { return "bar-chart"; }
    @Override public String title() { return "Overview"; }
    @Override public String subtitle() { return "How the kitchen is doing today."; }
    @Override public Node view() { return view; }

    @Override
    public Node headerActions() {
        Button export = Ui.button("Export CSV", "download", "btn-secondary");
        export.setOnAction(e -> CsvExport.run(user, uploads));
        Button refresh = Ui.button("Refresh", "refresh", "btn-secondary");
        refresh.setOnAction(e -> onShow());
        return new HBox(10, export, refresh);
    }

    @Override
    public void onShow() {
        MessMetrics m = uploads.getHostelMetrics(user, hostelId);
        List<Upload> all = uploads.getAllUploads(user, hostelId);

        content.getChildren().clear();
        content.getChildren().add(hero(m, all));
        content.getChildren().add(kpiRow(m));
        content.getChildren().add(todayCard(all));
        shell.refreshBadges();
    }

    // ---------------------------------------------------------------- pieces

    private Node hero(MessMetrics m, List<Upload> all) {
        int h = java.time.LocalTime.now().getHour();
        String greet = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String name = user.getUsername() == null ? "" : user.getUsername();
        int todayCount = 0;
        for (Upload u : all) if (Fmt.isToday(u.getUploadedAt())) todayCount++;

        String line = m.getPendingCount() > 0
                ? m.getPendingCount() + (m.getPendingCount() == 1 ? " photo is" : " photos are") + " waiting for your decision."
                : "You are all caught up. Nothing is waiting for review.";

        Button go = Ui.button(m.getPendingCount() > 0 ? "Open review queue" : "View all records",
                "arrow-right", "btn-light");
        go.setOnAction(e -> shell.show(m.getPendingCount() > 0 ? "review" : "records"));

        VBox left = new VBox(8,
                Ui.label("MESS HEAD  ·  OVERVIEW", "hero-eyebrow"),
                Ui.wrap(greet + ", " + name, "hero-title"),
                Ui.wrap(line, "hero-text"),
                heroStats(todayCount, m));
        left.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(go, new Insets(8, 0, 0, 0));
        left.getChildren().add(go);

        StackPane ring = Ui.ring(m.getApprovalRate() / 100.0, 132, 12, String.format("%.0f%%", m.getApprovalRate()), "", true);
        VBox ringBox = new VBox(6, ring, Ui.label("PASS RATE", "hero-stat-caption"));
        ringBox.setAlignment(Pos.CENTER);

        HBox box = new HBox(24, left, Ui.grow(), ringBox);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("hero");
        HBox.setHgrow(left, Priority.ALWAYS);
        return box;
    }

    private Node heroStats(int today, MessMetrics m) {
        HBox row = new HBox(12,
                heroStat(String.valueOf(today), "PHOTOS TODAY"),
                heroStat(String.valueOf(m.getUniqueDishesCount()), "DISHES"),
                heroStat(String.valueOf(m.getRejectedCount()), "REJECTED"));
        row.setPadding(new Insets(6, 0, 0, 0));
        return row;
    }

    private Node heroStat(String number, String caption) {
        VBox v = new VBox(0, Ui.label(number, "hero-stat-number"), Ui.label(caption, "hero-stat-caption"));
        v.getStyleClass().add("hero-pill");
        return v;
    }

    private Node kpiRow(MessMetrics m) {
        return Ui.kpiGrid(
                Ui.kpiCard("PHOTOS RECEIVED", String.valueOf(m.getTotalSubmissions()), "All time, your hostel", "image", "primary"),
                Ui.kpiCard("WAITING FOR REVIEW", String.valueOf(m.getPendingCount()), "Need your decision", "clock", "warn"),
                Ui.kpiCard("APPROVED", String.valueOf(m.getApprovedCount()), m.getRejectedCount() + " rejected so far", "check-circle", "ok"),
                Ui.kpiCard("PASS RATE", m.getFormattedApprovalRate(), "Approved of all reviewed", "shield", "primary"));
    }

    /** Dishes that received photos today with one coloured badge per stage. */
    private Node todayCard(List<Upload> all) {
        Map<String, List<Upload>> groups = new LinkedHashMap<>();
        for (Upload u : all) {
            if (!Fmt.isToday(u.getUploadedAt())) continue;
            groups.computeIfAbsent(u.getMeal() + "|" + u.getDishName().toLowerCase(), k -> new ArrayList<>()).add(u);
        }
        List<List<Upload>> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparingInt((List<Upload> l) -> l.stream().mapToInt(Upload::getId).max().orElse(0)).reversed());

        VBox card = Ui.card(0);
        HBox head = new HBox(10, Ui.label("Today's dishes", "h2"), Ui.badge(ordered.size() + " live", "primary"));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(head, new Insets(0, 0, 10, 0));
        card.getChildren().add(head);

        if (ordered.isEmpty()) {
            card.getChildren().add(Ui.emptyState("calendar", "No photos today yet",
                    "As soon as the kitchen sends photos, each dish and its three stages will show up here."));
            return card;
        }

        int shown = 0;
        for (List<Upload> g : ordered) {
            if (shown++ >= 10) break;
            card.getChildren().add(dishRow(g));
        }
        return card;
    }

    private Node dishRow(List<Upload> group) {
        Upload first = group.get(0);   // list is newest first
        VBox name = new VBox(2, Ui.label(first.getDishName(), "h3"),
                Ui.label(first.getMeal() + "  ·  last photo " + Fmt.ago(first.getUploadedAt()), "muted"));
        name.setMinWidth(200);
        name.setPrefWidth(260);

        HBox stages = Ui.stageTrack(group, STAGES);

        HBox row = new HBox(16, name, stages);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dish-row");
        return row;
    }
}
