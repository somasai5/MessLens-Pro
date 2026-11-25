package com.messlens.ui;

import com.messlens.exception.MessLensException;
import com.messlens.exception.ValidationException;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.ApprovalService;
import com.messlens.service.UploadService;
import com.messlens.util.AlertUtil;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Mess head "Review Queue". Left: photos waiting, oldest first. Right: the selected photo,
 * quick notes, and Approve / Reject buttons that are always visible (they never scroll away).
 */
public class HeadReviewPage implements Page {

    private static final String[] STAGES = {"Ingredients", "Cooking", "Serving"};

    private final User user;
    private final AppShell shell;
    private final UploadService uploads = new UploadService();
    private final ApprovalService approvals = new ApprovalService();
    private final int hostelId;

    private final ObservableList<Upload> queue = FXCollections.observableArrayList();
    private final TableView<Upload> table = Tables.newTable("check-circle", "All caught up",
            "No photos are waiting for review right now.");
    private final Label queueTitle = Ui.label("Waiting for review", "h2");
    private List<Upload> allRecords = List.of();
    private Upload selected;
    private ScrollPane inspectorScroll;   // so notes can scroll themselves into view
    private VBox noteBox;

    // inspector
    private final StackPane inspectorHost = new StackPane();
    private final VBox inspectorEmpty = Ui.emptyState("image", "Select a photo",
            "Pick a photo from the queue to inspect it and make a decision.");
    private final VBox inspectorBody = new VBox(0);
    private final StackPane photoFrame = new StackPane();
    private final ImageView photo;
    private final Label dishLabel = Ui.wrap("", "h1");
    private final Label metaLabel = Ui.label("", "muted");
    private final Label senderLabel = Ui.label("", "muted");
    private final FlowPane progressRow = new FlowPane(6, 6);
    private final VBox qualityHolder = new VBox();   // the kitchen's quality checklist for the selected photo
    private final TextArea remark = new TextArea();
    private final Label remarkError = Ui.wrap("A note is required when rejecting, so the worker knows what to fix.", "field-error");
    private final Button approveBtn = Ui.button("Approve", "check", "btn-success", "btn-large");
    private final Button rejectBtn = Ui.button("Reject", "x", "btn-danger", "btn-large");

    private final Node view;

    public HeadReviewPage(User user, AppShell shell) {
        this.user = user;
        this.shell = shell;
        this.hostelId = user.getHostelId() != null ? user.getHostelId() : 1;
        this.photo = Ui.fittedImage(photoFrame, 8);
        this.view = build();
    }

    @Override public String id() { return "review"; }
    @Override public String navLabel() { return "Review Queue"; }
    @Override public String icon() { return "check-square"; }
    @Override public String title() { return "Review queue"; }
    @Override public String subtitle() { return "Look at each photo, then approve it or reject it with a reason."; }
    @Override public Node view() { return view; }

    @Override
    public Node headerActions() {
        Button refresh = Ui.button("Refresh", "refresh", "btn-secondary");
        refresh.setOnAction(e -> reload(-1));
        return refresh;
    }

    @Override
    public void onShow() {
        reload(-1);
    }

    @Override
    public int badgeCount() {
        return uploads.getHostelMetrics(user, hostelId).getPendingCount();
    }

    // ================================================================ layout

    private Node build() {
        // ---- queue
        table.getColumns().addAll(List.of(
                Tables.photoCol(),
                Tables.dishCol("Dish", true),
                Tables.textCol("Sent by", Upload::getWorkerUsername, 64, 100),
                Tables.textCol("Waiting", u -> Fmt.ago(u.getUploadedAt()), 72, 100)));
        table.setItems(queue);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showUpload(b));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox left = new VBox(12, queueTitle, table);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMinWidth(350);

        // ---- inspector
        photoFrame.getStyleClass().add("photo-frame");
        photoFrame.setMinHeight(130);
        photoFrame.setMaxHeight(260);
        photoFrame.setStyle("-fx-cursor: hand;");
        photoFrame.getChildren().add(photo);
        photoFrame.setOnMouseClicked(e -> PhotoViewer.show(selected));
        Label zoomHint = Ui.label("Click the photo to enlarge", "faint");


        FlowPane quick = new FlowPane(8, 8);
        addChip(quick, "Fresh and clean", "ok");
        addChip(quick, "Properly cooked", "ok");
        addChip(quick, "Photo is blurry", "warn");
        addChip(quick, "Wrong dish or stage", "warn");
        addChip(quick, "Hygiene problem", "bad");

        remark.setPromptText("Write a note for the kitchen (required to reject)");
        remark.setPrefRowCount(3);
        remark.setWrapText(true);
        remark.textProperty().addListener((o, a, b) -> hideRemarkError());
        remarkError.setVisible(false);
        remarkError.setManaged(false);

        noteBox = new VBox(8, Ui.label("YOUR NOTE", "caps"), remark, remarkError);
        VBox scrollContent = new VBox(12,
                photoFrame, zoomHint,
                new VBox(2, dishLabel, metaLabel, senderLabel),
                progressRow,
                Ui.divider(),
                qualityHolder,
                Ui.divider(),
                Ui.label("QUICK NOTES", "caps"), quick,
                noteBox);
        scrollContent.setPadding(new Insets(20, 20, 20, 20));
        ScrollPane sp = Ui.scroll(scrollContent);
        inspectorScroll = sp;
        VBox.setVgrow(sp, Priority.ALWAYS);
        // the photo gets taller on a tall window and shorter on a short one, so the note box is usually in view
        photoFrame.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(140, Math.min(250, sp.getHeight() * 0.34)), sp.heightProperty()));
        // clicking into the note box brings it fully on screen, smoothly
        remark.focusedProperty().addListener((o, was, now) -> {
            if (now) Motion.scrollIntoView(sp, noteBox, 18);
        });

        approveBtn.setMaxWidth(Double.MAX_VALUE);
        rejectBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(approveBtn, Priority.ALWAYS);
        HBox.setHgrow(rejectBtn, Priority.ALWAYS);
        approveBtn.setOnAction(e -> decide("APPROVED"));
        rejectBtn.setOnAction(e -> decide("REJECTED"));
        HBox buttons = new HBox(12, rejectBtn, approveBtn);
        buttons.setPadding(new Insets(14, 20, 20, 20));
        buttons.setStyle("-fx-border-color: #e6e8f2; -fx-border-width: 1 0 0 0;");

        inspectorBody.getChildren().addAll(sp, buttons);
        inspectorHost.getChildren().addAll(inspectorEmpty, inspectorBody);
        inspectorEmpty.setPadding(new Insets(60, 20, 60, 20));

        VBox right = new VBox(inspectorHost);
        right.getStyleClass().add("card-flat");
        right.setPrefWidth(440);
        right.setMinWidth(340);
        right.setMaxWidth(480);
        VBox.setVgrow(inspectorHost, Priority.ALWAYS);

        HBox row = new HBox(20, left, right);
        row.setPadding(new Insets(4, 32, 24, 32));
        row.setFillHeight(true);
        showUpload(null);
        return row;
    }

    private void addChip(FlowPane pane, String text, String kind) {
        Button chip = new Button(text);
        chip.getStyleClass().addAll("chip", "chip-" + kind);
        chip.setOnAction(e -> {
            remark.setText(text);
            remark.requestFocus();
            remark.positionCaret(text.length());
            Motion.scrollIntoView(inspectorScroll, noteBox, 18);
        });
        pane.getChildren().add(chip);
    }

    // ================================================================ behaviour

    /** Reloads the queue; keeps the selection at the same position when {@code selectIndex} is >= 0. */
    private void reload(int selectIndex) {
        queue.setAll(uploads.getPendingUploads(user, hostelId));
        allRecords = uploads.getAllUploads(user, hostelId);
        int n = queue.size();
        queueTitle.setText(n == 0 ? "Waiting for review" : "Waiting for review  (" + n + ")");
        if (selectIndex >= 0 && n > 0) {
            table.getSelectionModel().select(Math.min(selectIndex, n - 1));
        } else if (selected != null) {
            boolean still = false;
            for (Upload u : queue) {
                if (u.getId() == selected.getId()) {
                    table.getSelectionModel().select(u);
                    still = true;
                    break;
                }
            }
            if (!still) showUpload(null);
        } else {
            showUpload(null);
        }
        shell.refreshBadges();
    }

    private void showUpload(Upload u) {
        selected = u;
        boolean has = u != null;
        inspectorEmpty.setVisible(!has);
        inspectorEmpty.setManaged(!has);
        inspectorBody.setVisible(has);
        inspectorBody.setManaged(has);
        hideRemarkError();
        remark.clear();
        if (!has) return;
        if (inspectorScroll != null) inspectorScroll.setVvalue(0);
        Motion.slideIn(inspectorBody, 14, 340, 0);

        photo.setImage(Media.full(u.getImagePath()));
        dishLabel.setText(u.getDishName());
        metaLabel.setText(u.getMeal() + "  ·  " + u.getStage() + " photo");
        senderLabel.setText("Sent by " + (u.getWorkerUsername() == null ? "kitchen" : u.getWorkerUsername())
                + "  ·  " + Fmt.when(u.getUploadedAt()));
        buildProgress(u);
        qualityHolder.getChildren().setAll(QualityPanel.panel(u));
    }

    /** Shows where the other two photos of the same dish stand, which helps judging this one. */
    private void buildProgress(Upload u) {
        progressRow.getChildren().clear();
        String day = u.getUploadedAt() == null || u.getUploadedAt().length() < 10 ? "" : u.getUploadedAt().substring(0, 10);
        for (String st : STAGES) {
            String status = null;
            boolean current = st.equalsIgnoreCase(u.getStage());
            if (current) {
                status = "REVIEWING";
            } else {
                for (Upload o : allRecords) {
                    if (st.equalsIgnoreCase(o.getStage()) && o.getMeal().equals(u.getMeal())
                            && o.getDishName().equalsIgnoreCase(u.getDishName())
                            && o.getUploadedAt() != null && o.getUploadedAt().startsWith(day)) {
                        status = o.getStatus();
                        break;
                    }
                }
            }
            Label b;
            if ("REVIEWING".equals(status)) b = Ui.badge(st + "  ·  This photo", "primary");
            else if (status == null) b = Ui.badge(st + "  ·  -", "neutral");
            else b = Ui.badge(st + "  ·  " + Fmt.shortStatus(status), Ui.statusKind(status));
            progressRow.getChildren().add(b);
        }
    }

    private void hideRemarkError() {
        remarkError.setVisible(false);
        remarkError.setManaged(false);
        remark.setStyle("");
    }

    private void decide(String decision) {
        if (selected == null) return;
        String note = remark.getText() == null ? "" : remark.getText().trim();
        if ("REJECTED".equals(decision) && note.isEmpty()) {
            remarkError.setVisible(true);
            remarkError.setManaged(true);
            remark.setStyle("-fx-border-color: #dc2626; -fx-border-width: 2px;");
            remark.requestFocus();
            Motion.scrollIntoView(inspectorScroll, noteBox, 18);
            Motion.shake(remark);
            return;
        }
        int idx = table.getSelectionModel().getSelectedIndex();
        Upload u = selected;
        try {
            if (approvals.reviewUpload(user, u.getId(), decision, note)) {
                boolean ok = "APPROVED".equals(decision);
                UiFx.toast(u.getDishName() + " (" + u.getStage() + ") " + (ok ? "approved" : "rejected"), ok);
                selected = null;
                reload(idx);      // jump to the next photo in the queue
            }
        } catch (ValidationException e) {
            AlertUtil.showWarning("Please check your note", e.getMessage());
        } catch (MessLensException e) {
            AlertUtil.showError("Could not save the decision", e.getMessage());
        } catch (Exception e) {
            AlertUtil.showError("Unexpected problem", e.getMessage());
        }
    }
}
