package com.messlens.ui;

import com.messlens.exception.MessLensException;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.security.AccessDecisionService;
import com.messlens.security.Action;
import com.messlens.security.Resource;
import com.messlens.service.ApprovalService;
import com.messlens.service.UploadService;
import com.messlens.util.AlertUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The warden is the SECOND reviewer. The mess head decides each photo; the warden then confirms that decision or
 * overturns it (with a reason, inside the review window). Photos the warden uploaded can never be reviewed by the
 * same person (separation of duties), and a decision is countersigned only once.
 */
public class WardenOversightPage implements Page {

    private final User user;
    private final int hostelId;
    private final UploadService uploads = new UploadService();
    private final ApprovalService approvals = new ApprovalService();
    private final AccessDecisionService pdp = AccessDecisionService.INSTANCE;

    private final ObservableList<Upload> decided = FXCollections.observableArrayList();
    private final FilteredList<Upload> filtered = new FilteredList<>(decided, u -> true);
    private final TableView<Upload> table = Tables.newTable("shield", "Nothing to review",
            "Photos the mess head has decided on will appear here.");
    private final ToggleButton awaitingOnly = Ui.segment("Awaiting my review");
    private final VBox kpis = new VBox();
    private final Label countLabel = Ui.label("", "muted");
    private final Node view;

    public WardenOversightPage(User user) {
        this.user = user;
        this.hostelId = user.getHostelId() != null ? user.getHostelId() : 1;
        this.view = build();
    }

    @Override public String id() { return "oversight"; }
    @Override public String navLabel() { return "Second Review"; }
    @Override public String icon() { return "shield"; }
    @Override public String title() { return "Second review"; }
    @Override public String subtitle() { return "Confirm or overturn the mess head's decisions for your hostel."; }
    @Override public Node view() { return view; }

    @Override
    public int badgeCount() {
        int n = 0;
        for (Upload u : load()) if (u.getWardenAction() == null) n++;
        return n;
    }

    @Override
    public void onShow() {
        List<Upload> list = load();
        decided.setAll(list);
        int waiting = 0, confirmed = 0, overturned = 0;
        for (Upload u : list) {
            if (u.getWardenAction() == null) waiting++;
            else if ("CONFIRMED".equals(u.getWardenAction())) confirmed++;
            else overturned++;
        }
        kpis.getChildren().setAll(Ui.kpiGrid(
                Ui.kpiCard("AWAITING YOUR REVIEW", String.valueOf(waiting), "Decided by the mess head", "clock", waiting > 0 ? "warn" : "ok"),
                Ui.kpiCard("CONFIRMED", String.valueOf(confirmed), "Head's decision stands", "check-circle", "ok"),
                Ui.kpiCard("OVERTURNED", String.valueOf(overturned), "Reversed with a reason", "alert", "bad")));
        apply();
    }

    private List<Upload> load() {
        List<Upload> out = new ArrayList<>();
        for (Upload u : uploads.getAllUploads(user, hostelId)) {
            if (!"PENDING".equalsIgnoreCase(u.getStatus())) out.add(u);
        }
        return out;
    }

    private Node build() {
        awaitingOnly.setSelected(true);
        awaitingOnly.setOnAction(e -> apply());
        HBox top = new HBox(12, awaitingOnly, Ui.grow(), countLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().add(Tables.photoCol(true));
        table.getColumns().add(Tables.dishCol("Dish", true));
        table.getColumns().add(Tables.statusCol());
        table.getColumns().add(Tables.textCol("Head's note", Upload::getRemark, 150, 220));
        table.getColumns().add(Tables.whenCol("Decided", Upload::getApprovedAt, 120, 140));
        table.getColumns().add(actionCol());
        table.setItems(filtered);
        table.setRowFactory(tv -> {
            TableRow<Upload> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) PhotoViewer.show(row.getItem());
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Label note = Ui.wrap("You are the second reviewer. Confirm keeps the decision. Overturn reverses it and needs a reason; "
                + "it is possible for a limited number of days after the head's decision, and every step is written to the audit log.", "muted");
        VBox box = new VBox(14, kpis, note, top, table);
        box.setPadding(new Insets(4, 32, 24, 32));
        return box;
    }

    private void apply() {
        boolean only = awaitingOnly.isSelected();
        filtered.setPredicate(u -> !only || u.getWardenAction() == null);
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " decision" : " decisions"));
    }

    private Resource resourceOf(Upload u) {
        return Resource.of("upload").id(u.getId()).hostelId(u.getHostelId()).owner(u.getWorkerId()).status(u.getStatus())
                .decidedAt(ApprovalService.parse(u.getApprovedAt())).countersigned(u.getWardenAction() != null);
    }

    private TableColumn<Upload, Upload> actionCol() {
        TableColumn<Upload, Upload> c = new TableColumn<>("Your review");
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        c.setMinWidth(250);
        c.setPrefWidth(270);
        c.setSortable(false);
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Upload u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                if (u.getWardenAction() != null) {
                    boolean ov = "OVERTURNED".equals(u.getWardenAction());
                    setGraphic(Ui.badge(ov ? "Overturned by you" : "Confirmed by you", ov ? "bad" : "ok"));
                    return;
                }
                Button confirm = Ui.button("Confirm", "check", "btn-success", "btn-small");
                Button overturn = Ui.button("Overturn", "refresh", "btn-danger", "btn-small");
                boolean canConfirm = pdp.check(user, Action.CONFIRM_DECISION, resourceOf(u)).permit;
                boolean canOverturn = pdp.check(user, Action.OVERTURN_DECISION, resourceOf(u)).permit;
                confirm.setDisable(!canConfirm);
                overturn.setDisable(!canOverturn);
                if (canConfirm && !canOverturn) overturn.setTooltip(new Tooltip("The review window for this decision has closed."));
                confirm.setOnAction(e -> act(u, "CONFIRM", ""));
                overturn.setOnAction(e -> askReason(u));
                HBox box = new HBox(8, confirm, overturn);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });
        return c;
    }

    private void askReason(Upload u) {
        Optional<String> r = AlertUtil.askText("Overturn this decision?",
                "Why is the " + u.getStatus().toLowerCase() + " decision wrong? Your reason is saved in the audit log.",
                "Write the reason here", "Overturn decision", "alert", true, true);
        if (r.isPresent()) act(u, "OVERTURN", r.get());
    }

    private void act(Upload u, String action, String reason) {
        try {
            approvals.countersign(user, u.getId(), action, reason);
            UiFx.toast("OVERTURN".equals(action) ? "Decision overturned" : "Decision confirmed", true);
            onShow();
        } catch (MessLensException ex) {
            AlertUtil.showWarning("Could not complete the review", ex.getMessage());
            onShow();
        }
    }
}
