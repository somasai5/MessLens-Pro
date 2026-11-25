package com.messlens.ui;

import com.messlens.audit.AuditEntry;
import com.messlens.audit.VerifyResult;
import com.messlens.exception.MessLensException;
import com.messlens.model.User;
import com.messlens.service.IntegrityService;
import com.messlens.util.AlertUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/**
 * Audit trail and integrity checks for the warden and administrator.
 * "Verify audit log" recomputes the tamper-evident chain; "Verify stored photos" re-hashes every evidence file.
 */
public class AuditPage implements Page {

    private final User user;
    private final IntegrityService integrity = new IntegrityService();
    private final ObservableList<AuditEntry> rows = FXCollections.observableArrayList();
    private final Label countLabel = Ui.label("", "muted");
    private final Node view;

    public AuditPage(User user) {
        this.user = user;
        this.view = build();
    }

    @Override public String id() { return "audit"; }
    @Override public String navLabel() { return "Audit & Integrity"; }
    @Override public String icon() { return "database"; }
    @Override public String title() { return "Audit and integrity"; }
    @Override public String subtitle() { return "A tamper-evident record of everything that happened, and checks that prove nothing was altered."; }
    @Override public Node view() { return view; }

    @Override
    public void onShow() {
        rows.setAll(integrity.recentAudit(user, 300));
        countLabel.setText("Latest " + rows.size() + " entries, newest first");
    }

    private final VBox logResult = new VBox(10);
    private final VBox photoResult = new VBox(10);

    private Node build() {
        // ---- two check cards, side by side
        Button verifyLog = Ui.button("Verify audit log", "shield", "btn-primary");
        verifyLog.setOnAction(e -> verifyLog());
        Button verifyPhotos = Ui.button("Verify stored photos", "image", "btn-primary");
        verifyPhotos.setOnAction(e -> verifyPhotos());

        Label keyNote = Ui.wrap("Keep a copy of audit/audit_verifier.key somewhere offline. With it, the whole history can be re-checked from the first entry.", "muted");
        HBox hint = new HBox(10, Icons.of("key", 16, "icon-primary"), keyNote);
        hint.setAlignment(Pos.TOP_LEFT);
        hint.getStyleClass().add("hint-box");
        HBox.setHgrow(keyNote, Priority.ALWAYS);

        VBox cardLog = checkCard("link", "Audit log chain", "Is the history of actions still unchanged?",
                "Every action (sign-in, approve, reject, overturn) is written to a log. Each entry is sealed with a code that also covers every entry before it, "
                        + "so editing, deleting or inserting a past entry breaks the chain.",
                verifyLog, logResult, hint);
        VBox cardPhotos = checkCard("image", "Stored photos", "Is every photo still exactly as it was uploaded?",
                "When a photo is uploaded, its SHA-256 fingerprint is saved. This check re-reads every photo file and compares it with that fingerprint, "
                        + "so a swapped or edited picture is caught.",
                verifyPhotos, photoResult, null);

        GridPane checks = new GridPane();
        checks.setHgap(20);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            cc.setHgrow(Priority.ALWAYS);
            checks.getColumnConstraints().add(cc);
        }
        checks.add(cardLog, 0, 0);
        checks.add(cardPhotos, 1, 0);

        // ---- activity log
        Button refresh = Ui.button("Refresh", "refresh", "btn-ghost", "btn-small");
        refresh.setOnAction(e -> onShow());
        HBox logHead = new HBox(12, Ui.label("Activity log", "h2"), countLabel, Ui.grow(), refresh);
        logHead.setAlignment(Pos.CENTER_LEFT);

        TableView<AuditEntry> table = new TableView<>(rows);
        table.setFixedCellSize(48);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(Ui.emptyState("database", "No entries yet", "Activity will be recorded here."));
        table.getColumns().add(col("#", 44, 56, e -> String.valueOf(e.seq), false));
        table.getColumns().add(col("When", 130, 160, e -> Fmt.when(e.ts == null ? null : e.ts.replace('T', ' ').replaceAll("\\.\\d+Z?$", "")), false));
        table.getColumns().add(col("Who", 120, 160, e -> e.actorUsername == null ? "-" : e.actorUsername + (e.actorRole == null ? "" : " (" + e.actorRole.toLowerCase() + ")"), false));
        table.getColumns().add(col("What happened", 160, 200, e -> humanize(e.action), true));
        table.getColumns().add(outcomeCol());
        table.getColumns().add(col("Detail", 160, 380, e -> e.detail, false));
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMinHeight(380);

        VBox box = new VBox(20, checks, new VBox(12, logHead, table));
        VBox.setVgrow(box.getChildren().get(1), Priority.ALWAYS);
        box.setPadding(new Insets(4, 32, 24, 32));
        return Ui.scroll(box);
    }

    private VBox checkCard(String icon, String title, String question, String how, Button action, VBox result, Node extra) {
        HBox head = new HBox(12, Ui.iconTile(icon, "primary"), new VBox(1, Ui.label(title, "h2"), Ui.label(question, "muted")));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(14, head, Ui.divider(), Ui.wrap(how, "body"), action, result);
        if (extra != null) card.getChildren().add(extra);
        card.getStyleClass().add("check-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** "WARDEN_OVERTURNED" becomes "Warden overturned". */
    private static String humanize(String action) {
        if (action == null || action.isBlank()) return "-";
        String t = action.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private TableColumn<AuditEntry, AuditEntry> col(String title, double min, double pref, Function<AuditEntry, String> f, boolean pill) {
        TableColumn<AuditEntry, AuditEntry> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        c.setMinWidth(min);
        c.setPrefWidth(pref);
        c.setSortable(false);
        c.setCellFactory(cc -> new TableCell<>() {
            private final Label chip = Ui.label("", "action-pill");

            @Override
            protected void updateItem(AuditEntry e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                String s = f.apply(e);
                String shown = s == null || s.isBlank() ? "-" : s;
                if (pill) {
                    chip.setText(shown);
                    setText(null);
                    setGraphic(chip);
                } else {
                    setGraphic(null);
                    setText(shown);
                }
                setTooltip(shown.length() > 28 ? new javafx.scene.control.Tooltip(shown) : null);
            }
        });
        return c;
    }

    private TableColumn<AuditEntry, AuditEntry> outcomeCol() {
        TableColumn<AuditEntry, AuditEntry> c = new TableColumn<>("Result");
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        c.setMinWidth(72);
        c.setPrefWidth(84);
        c.setSortable(false);
        c.setCellFactory(cc -> new TableCell<>() {
            @Override
            protected void updateItem(AuditEntry e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) { setGraphic(null); return; }
                String o = e.outcome == null ? "OK" : e.outcome;
                setGraphic(Ui.badge(o, "OK".equals(o) ? "ok" : "DENY".equals(o) ? "warn" : "bad"));
            }
        });
        return c;
    }

    private void verifyLog() {
        try {
            VerifyResult v = integrity.verifyAuditLog(user);
            addResult(logResult, v.ok ? "ok" : "bad", v.ok ? "check-circle" : "alert",
                    v.ok ? "Audit log is intact" : "Audit log has been tampered with", v.message, null);
            onShow();
        } catch (MessLensException ex) {
            AlertUtil.showWarning("Not allowed", ex.getMessage());
        }
    }

    private void verifyPhotos() {
        try {
            IntegrityService.EvidenceReport r = integrity.verifyEvidence(user);
            if (r.ok()) {
                addResult(photoResult, "ok", "check-circle", "All stored photos are intact",
                        r.intact + " of " + r.checked + " photos match the SHA-256 recorded when they were uploaded.", null);
            } else {
                int shown = Math.min(4, r.problems.size());
                String lines = String.join("\n", r.problems.subList(0, shown))
                        + (r.problems.size() > shown ? "\n...and " + (r.problems.size() - shown) + " more" : "");
                boolean missing = r.problems.stream().anyMatch(p -> p.contains("missing"));
                addResult(photoResult, "bad", "alert", (r.checked - r.intact) + " of " + r.checked + " photo(s) do not match their upload fingerprint",
                        lines, missing ? "A file is \"missing\" when it is not in the evidence folder on this computer. "
                                + "Restore it from a backup, or ask the kitchen to send the photo again." : null);
            }
            onShow();
        } catch (MessLensException ex) {
            AlertUtil.showWarning("Not allowed", ex.getMessage());
        }
    }

    private void addResult(VBox target, String tone, String icon, String title, String text, String hintText) {
        HBox row = new HBox(12, Icons.of(icon, 22, "icon-" + tone), new VBox(3, Ui.wrap(title, "h3"), Ui.wrap(text, "muted")));
        HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("callout-" + tone);
        target.getChildren().setAll(row);
        if (hintText != null) {
            Label h = Ui.wrap(hintText, "muted");
            HBox hb = new HBox(10, Icons.of("info", 16, "icon-primary"), h);
            hb.setAlignment(Pos.TOP_LEFT);
            hb.getStyleClass().add("hint-box");
            HBox.setHgrow(h, Priority.ALWAYS);
            target.getChildren().add(hb);
        }
        Motion.rise(target, 10, 360);
    }
}
