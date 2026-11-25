package com.messlens.ui;

import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.UploadService;
import com.messlens.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker "My Submissions" page: filters, a full-width table with the reason for every decision, and a click on
 * any photo opens it big on the screen together with the review trail (head, then warden).
 */
public class WorkerSubmissionsPage implements Page {

    private final User user;
    private final AppShell shell;
    private final UploadService uploads = new UploadService();

    private final ObservableList<Upload> all = FXCollections.observableArrayList();
    private final FilteredList<Upload> filtered = new FilteredList<>(all, u -> true);
    private final TableView<Upload> table = Tables.newTable("list", "No submissions yet",
            "Photos you send from the Capture page will appear here.");
    private final Map<String, ToggleButton> filterButtons = new LinkedHashMap<>();
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final TextField search = new TextField();
    private final HBox banner = new HBox(12);
    private final Label bannerText = Ui.wrap("", "body");

    private final Node view;

    // ---- rejected photos the worker has already opened (remembered between runs, per user)
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(WorkerSubmissionsPage.class);

    private static java.util.Set<String> seen(User u) {
        String raw = PREFS.get("seenRejected_" + u.getId(), "");
        return new java.util.HashSet<>(raw.isBlank() ? List.<String>of() : java.util.Arrays.asList(raw.split(",")));
    }

    private static void markSeen(User u, int uploadId) {
        java.util.Set<String> s = seen(u);
        s.add(String.valueOf(uploadId));
        PREFS.put("seenRejected_" + u.getId(), String.join(",", s));
    }

    /** Rejected photos the worker has not opened yet: they drive the banner and the sidebar badge. */
    private static int unseenRejected(User u, List<Upload> list) {
        java.util.Set<String> s = seen(u);
        int n = 0;
        for (Upload x : list) {
            if ("REJECTED".equalsIgnoreCase(x.getStatus()) && !s.contains(String.valueOf(x.getId()))) n++;
        }
        return n;
    }

    public WorkerSubmissionsPage(User user, AppShell shell) {
        this.user = user;
        this.shell = shell;
        this.view = build();
    }

    @Override public String id() { return "submissions"; }
    @Override public String navLabel() { return "My Submissions"; }
    @Override public String icon() { return "list"; }
    @Override public String title() { return "My submissions"; }
    @Override public String subtitle() { return "Every photo you sent, and what the mess head decided. Click a photo to open it."; }
    @Override public Node view() { return view; }

    @Override
    public Node headerActions() {
        Button refresh = Ui.button("Refresh", "refresh", "btn-secondary");
        refresh.setOnAction(e -> reload());
        return refresh;
    }

    @Override
    public void onShow() {
        reload();
    }

    /** Sidebar badge: how many photos were rejected and need to be retaken. */
    @Override
    public int badgeCount() {
        return unseenRejected(user, uploads.getWorkerUploads(user));
    }

    // ================================================================ layout

    private Node build() {
        // ---- filter row
        FlowPane filters = new FlowPane(8, 8);
        filters.setAlignment(Pos.CENTER_LEFT);
        for (String key : new String[]{"ALL", "PENDING", "APPROVED", "REJECTED"}) {
            ToggleButton t = Ui.segment(key);
            t.setToggleGroup(filterGroup);
            t.setUserData(key);
            filterButtons.put(key, t);
            filters.getChildren().add(t);
        }
        filterGroup.selectToggle(filterButtons.get("ALL"));
        filterGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null && a != null) a.setSelected(true);
            else applyFilter();
        });

        search.setPromptText("Search by dish name");
        search.setPrefWidth(240);
        search.setMinWidth(160);
        search.textProperty().addListener((o, a, b) -> applyFilter());

        HBox.setHgrow(filters, Priority.ALWAYS);
        HBox filterRow = new HBox(12, filters, search);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // ---- banner for rejected photos
        banner.getStyleClass().add("callout-bad");
        banner.setAlignment(Pos.CENTER_LEFT);
        Button show = Ui.button("Show rejected", null, "btn-link");
        show.setOnAction(e -> filterGroup.selectToggle(filterButtons.get("REJECTED")));
        banner.getChildren().addAll(Icons.of("alert", 20, "icon-bad"), bannerText, Ui.grow(), show);
        banner.setVisible(false);
        banner.setManaged(false);

        // ---- table (the whole width; a click on a row opens the photo)
        table.getColumns().addAll(List.of(
                Tables.photoCol(),
                Tables.dishCol("Dish", true),
                Tables.statusCol(),
                Tables.textCol("Reason", WorkerSubmissionsPage::finalReason, 200, 360),
                Tables.whenCol("Sent", Upload::getUploadedAt, 130, 160)));
        table.setItems(filtered);
        table.setRowFactory(tv -> {
            TableRow<Upload> row = new TableRow<>();
            row.setStyle("-fx-cursor: hand;");
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && !row.isEmpty()) open(row.getItem());
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox box = new VBox(14, filterRow, banner, table);
        box.setPadding(new Insets(4, 32, 28, 32));
        return box;
    }

    /** The reason that belongs to the FINAL decision: the warden's when it was overturned, otherwise the head's. */
    private static String finalReason(Upload u) {
        String s = u.getStatus() == null ? "" : u.getStatus().toUpperCase();
        if ("PENDING".equals(s)) return "Waiting for the mess head";
        String r = u.isOverturned() ? u.getWardenRemark() : u.getRemark();
        return (u.isOverturned() ? "Warden: " : "") + (r == null || r.isBlank() ? "No note" : r);
    }

    // ================================================================ behaviour

    private void reload() {
        List<Upload> list = uploads.getWorkerUploads(user);
        all.setAll(list);

        int pending = 0, approved = 0, rejected = 0;
        for (Upload u : list) {
            String s = u.getStatus() == null ? "" : u.getStatus().toUpperCase();
            if (s.equals("PENDING")) pending++;
            else if (s.equals("APPROVED")) approved++;
            else if (s.equals("REJECTED")) rejected++;
        }
        filterButtons.get("ALL").setText("All  " + list.size());
        filterButtons.get("PENDING").setText("Pending  " + pending);
        filterButtons.get("APPROVED").setText("Approved  " + approved);
        filterButtons.get("REJECTED").setText("Rejected  " + rejected);

        int unseen = unseenRejected(user, list);
        boolean showBanner = unseen > 0;
        banner.setVisible(showBanner);
        banner.setManaged(showBanner);
        bannerText.setText(unseen == 1
                ? "1 photo was rejected. Click it to read why, then retake it on the Capture page."
                : unseen + " photos were rejected. Click them to read why, then retake them on the Capture page.");

        applyFilter();
        shell.refreshBadges();
    }

    private void applyFilter() {
        ToggleButton t = (ToggleButton) filterGroup.getSelectedToggle();
        String status = t == null ? "ALL" : (String) t.getUserData();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        filtered.setPredicate(u -> {
            boolean okStatus = status.equals("ALL") || status.equalsIgnoreCase(u.getStatus());
            boolean okText = q.isEmpty() || (u.getDishName() != null && u.getDishName().toLowerCase().contains(q));
            return okStatus && okText;
        });
    }

    /** Opens the photo on screen; a pending photo can be withdrawn from there. */
    private void open(Upload u) {
        if ("REJECTED".equalsIgnoreCase(u.getStatus())) markSeen(user, u.getId());
        PhotoViewer.show(u, () -> withdraw(u));
        // once the rejected photo has been read, the red banner and the sidebar badge go away
        int unseen = unseenRejected(user, all);
        banner.setVisible(unseen > 0);
        banner.setManaged(unseen > 0);
        if (unseen > 0) bannerText.setText(unseen == 1
                ? "1 photo was rejected. Click it to read why, then retake it on the Capture page."
                : unseen + " photos were rejected. Click them to read why, then retake them on the Capture page.");
        shell.refreshBadges();
    }

    private void withdraw(Upload u) {
        if (u == null || !"PENDING".equalsIgnoreCase(u.getStatus())) return;
        boolean ok = AlertUtil.showConfirmation("Withdraw this photo?",
                "Remove the " + u.getStage() + " photo of \"" + u.getDishName() + "\"? You can send a new one afterwards.",
                "Withdraw photo", "Keep it", "trash", true);
        if (!ok) return;
        try {
            if (uploads.withdrawUpload(user, u.getId())) {
                Media.forget(u.getImagePath());
                UiFx.toast("Photo withdrawn", true);
            } else {
                AlertUtil.showWarning("Could not withdraw", "This photo has already been reviewed.");
            }
        } catch (com.messlens.exception.MessLensException ex) {
            AlertUtil.showWarning("Could not withdraw", ex.getMessage());
        }
        reload();
    }
}
