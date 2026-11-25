package com.messlens.ui;

import com.messlens.dao.ComplaintDao;
import com.messlens.exception.MessLensException;
import com.messlens.model.Complaint;
import com.messlens.model.MessInfo;
import com.messlens.model.User;
import com.messlens.service.FeedbackService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import java.util.List;

/**
 * Complaints. A student writes a complaint to the mess head of their hostel and later reads the reply.
 * The mess head sees only their own hostel's complaints, answers them, and that marks them resolved.
 */
public class ComplaintsPage implements Page {

    private static final String ANY_MEAL = "Not about one meal";

    private final User user;
    private final AppShell shell;
    private final boolean head;
    private final ComplaintDao dao = new ComplaintDao();
    private final FeedbackService service = new FeedbackService();
    private final VBox content = new VBox(18);
    private final Node view;
    private String filter = "ALL";    // head only: ALL / OPEN / RESOLVED

    public ComplaintsPage(User user, AppShell shell) {
        this.user = user;
        this.shell = shell;
        this.head = "HEAD".equalsIgnoreCase(user.getRole());
        content.setPadding(new Insets(4, 32, 30, 32));
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "complaints"; }
    @Override public String navLabel() { return "Complaints"; }
    @Override public String icon() { return "message"; }
    @Override public String title() { return head ? "Complaints" : "Complaints and feedback"; }
    @Override public String subtitle() {
        return head ? "Read what students reported and reply to them." : "Something wrong with the food or the mess? Tell your mess head.";
    }
    @Override public Node view() { return view; }

    @Override
    public int badgeCount() {
        return head && user.getHostelId() != null ? dao.countOpen(user.getHostelId()) : 0;
    }

    @Override
    public void onShow() {
        if (head) showHead(); else showStudent();
    }

    // ------------------------------------------------------------------ student

    private void showStudent() {
        ComboBox<String> category = new ComboBox<>();
        category.getItems().addAll(MessInfo.CATEGORIES);
        category.setValue(MessInfo.CATEGORIES[0]);
        category.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> meal = new ComboBox<>();
        meal.getItems().add(ANY_MEAL);
        meal.getItems().addAll(MessInfo.MEALS);
        meal.setValue(ANY_MEAL);
        meal.setMaxWidth(Double.MAX_VALUE);

        TextArea text = new TextArea();
        text.setWrapText(true);
        text.setPrefRowCount(5);
        text.setPromptText("Describe what happened (at least 10 characters)");

        // optional photo
        byte[][] photo = new byte[1][];
        ImageView preview = new ImageView();
        preview.setFitWidth(340);
        preview.setFitHeight(200);
        preview.setPreserveRatio(true);
        preview.setVisible(false);
        preview.setManaged(false);
        Button attach = Ui.button("Add a photo (optional)", "image", "btn-secondary");
        Button removePhoto = Ui.button("Remove photo", "x", "btn-danger", "btn-small");
        removePhoto.setVisible(false);
        removePhoto.setManaged(false);
        attach.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a photo for your complaint");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Photos (JPG, PNG)", "*.jpg", "*.jpeg", "*.png"));
            File f = chooser.showOpenDialog(AppNavigator.getPrimaryStage());
            if (f == null) return;
            try {
                if (f.length() > 5L * 1024 * 1024) { UiFx.toast("That photo is over 5 MB. Please choose a smaller one.", false); return; }
                byte[] bytes = Files.readAllBytes(f.toPath());
                Image img = new Image(new java.io.ByteArrayInputStream(bytes));
                if (img.isError() || img.getWidth() < 1) { UiFx.toast("That file could not be opened as a photo.", false); return; }
                photo[0] = bytes;
                preview.setImage(img);
                preview.setVisible(true);
                preview.setManaged(true);
                removePhoto.setVisible(true);
                removePhoto.setManaged(true);
                attach.setText("Change photo");
            } catch (IOException ex) {
                UiFx.toast("Could not read that photo.", false);
            }
        });
        removePhoto.setOnAction(e -> {
            photo[0] = null;
            preview.setImage(null);
            preview.setVisible(false);
            preview.setManaged(false);
            removePhoto.setVisible(false);
            removePhoto.setManaged(false);
            attach.setText("Add a photo (optional)");
        });
        HBox photoRow = new HBox(10, attach, removePhoto);
        photoRow.setAlignment(Pos.CENTER_LEFT);

        Button send = Ui.button("Send complaint", "arrow-right", "btn-primary");
        send.setMaxWidth(Double.MAX_VALUE);
        send.setOnAction(e -> {
            try {
                service.fileComplaint(user, category.getValue(), ANY_MEAL.equals(meal.getValue()) ? null : meal.getValue(), text.getText(), photo[0]);
                UiFx.toast("Your complaint was sent to the mess head.", true);
                showStudent();
            } catch (MessLensException ex) {
                UiFx.toast(ex.getMessage(), false);
            }
        });
        VBox form = Ui.card(14, Ui.label("New complaint", "h2"),
                Ui.field("What is it about?", category), Ui.field("Which meal?", meal), Ui.field("Details", text), Ui.field("Photo", new VBox(8, photoRow, preview)), send);
        form.setMinWidth(360);
        form.setPrefWidth(400);
        form.setMaxWidth(420);

        List<Complaint> mine = dao.findByStudent(user.getId());
        VBox list = new VBox(12);
        HBox header = new HBox(10, Ui.label("My complaints", "h2"), Ui.badge(String.valueOf(mine.size()), "neutral"));
        header.setAlignment(Pos.CENTER_LEFT);
        list.getChildren().add(header);
        if (mine.isEmpty()) {
            list.getChildren().add(Ui.card(0, Ui.emptyState("message", "No complaints yet", "When you send one, you will see the mess head's reply here.")));
        }
        for (Complaint c : mine) list.getChildren().add(studentCard(c));
        HBox.setHgrow(list, Priority.ALWAYS);

        HBox layout = new HBox(18, form, list);
        layout.setAlignment(Pos.TOP_LEFT);
        content.getChildren().setAll(layout);
    }

    private Node studentCard(Complaint c) {
        VBox card = baseCard(c, false);
        if (c.getReply() != null && !c.getReply().isBlank()) {
            card.getChildren().add(replyBox("Reply from your mess head", c));
        } else if ("READ".equals(c.getStatus())) {
            card.getChildren().add(Ui.label("The mess head has read your complaint. No reply is needed.", "muted"));
        } else {
            Button withdraw = Ui.button("Withdraw", "x", "btn-danger", "btn-small");
            withdraw.setOnAction(e -> {
                if (!Dialogs.confirm("Withdraw this complaint?", "The mess head will no longer see it. You can send a new one any time.",
                        "Withdraw", "Keep it", "alert", true)) return;
                try {
                    service.withdrawComplaint(user, c.getId());
                    UiFx.toast("Complaint withdrawn.", true);
                    showStudent();
                } catch (MessLensException ex) {
                    UiFx.toast(ex.getMessage(), false);
                }
            });
            HBox row = new HBox(10, Ui.label("Waiting for a reply.", "muted"), Ui.grow(), withdraw);
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        }
        return card;
    }

    // ------------------------------------------------------------------ mess head

    private void showHead() {
        int hostelId = user.getHostelId() != null ? user.getHostelId() : 1;
        List<Complaint> all = dao.findByHostel(hostelId);
        long open = all.stream().filter(c -> "OPEN".equals(c.getStatus())).count();
        long read = all.stream().filter(c -> "READ".equals(c.getStatus())).count();

        HBox filters = new HBox(8);
        ToggleGroup group = new ToggleGroup();
        String[][] defs = {{"ALL", "All  " + all.size()}, {"OPEN", "Open  " + open}, {"READ", "Read  " + read}, {"RESOLVED", "Resolved  " + (all.size() - open - read)}};
        for (String[] d : defs) {
            ToggleButton b = Ui.segment(d[1]);
            b.setToggleGroup(group);
            b.setUserData(d[0]);
            b.setSelected(d[0].equals(filter));
            filters.getChildren().add(b);
        }
        group.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) { if (old != null) old.setSelected(true); return; }
            filter = (String) now.getUserData();
            showHead();
        });

        VBox list = new VBox(14);
        int shown = 0;
        for (Complaint c : all) {
            if (!"ALL".equals(filter) && !filter.equals(c.getStatus())) continue;
            list.getChildren().add(headCard(c));
            shown++;
        }
        if (shown == 0) {
            list.getChildren().add(Ui.card(0, Ui.emptyState("check-circle", "Nothing here",
                    "ALL".equals(filter) ? "No student has sent a complaint yet." : "No complaints match this filter.")));
        }
        content.getChildren().setAll(filters, list);
        if (shell != null) shell.refreshBadges();
    }

    private Node headCard(Complaint c) {
        VBox card = baseCard(c, true);
        if ("OPEN".equals(c.getStatus()) || "READ".equals(c.getStatus())) {
            TextArea reply = new TextArea();
            reply.setWrapText(true);
            reply.setPrefRowCount(2);
            reply.setPromptText("Reply to the student (optional)");
            Button send = Ui.button("Send reply and resolve", "check", "btn-primary", "btn-small");
            send.setOnAction(e -> {
                try {
                    service.replyToComplaint(user, c.getId(), reply.getText());
                    UiFx.toast("Reply sent. The complaint is resolved.", true);
                    showHead();
                } catch (MessLensException ex) {
                    UiFx.toast(ex.getMessage(), false);
                }
            });
            HBox actions = new HBox(10, send);
            if ("OPEN".equals(c.getStatus())) {
                Button markRead = Ui.button("Mark as read", "eye", "btn-secondary", "btn-small");
                markRead.setOnAction(e -> {
                    try {
                        service.markComplaintRead(user, c.getId());
                        UiFx.toast("Marked as read.", true);
                        showHead();
                    } catch (MessLensException ex) {
                        UiFx.toast(ex.getMessage(), false);
                    }
                });
                actions.getChildren().add(markRead);
            }
            card.getChildren().addAll(reply, actions);
        } else if (c.getReply() != null) {
            card.getChildren().add(replyBox("Your reply", c));
        }
        return card;
    }

    // ------------------------------------------------------------------ shared pieces

    private VBox baseCard(Complaint c, boolean showStudent) {
        HBox top = new HBox(10, Ui.label(c.getCategory(), "h3"));
        top.setAlignment(Pos.CENTER_LEFT);
        if (c.getMeal() != null && !c.getMeal().isBlank()) top.getChildren().add(Ui.badge(c.getMeal(), "neutral"));
        top.getChildren().addAll(Ui.grow(),
                "OPEN".equals(c.getStatus()) ? Ui.badge("Open", "warn")
                        : "READ".equals(c.getStatus()) ? Ui.badge("Read", "neutral") : Ui.badge("Resolved", "ok"));
        String by = (showStudent ? c.getStudentName() + "  \u00b7  " : "") + Fmt.when(c.getCreatedAt());
        VBox card = new VBox(10, top, Ui.label(by, "faint"), Ui.wrap(c.getDescription(), "body"));
        if (c.hasImage()) {
            Image thumb = Media.thumb(c.getImagePath(), 260, 160);
            if (thumb != null) {
                ImageView iv = new ImageView(thumb);
                iv.setFitWidth(260);
                iv.setFitHeight(160);
                iv.setPreserveRatio(true);
                iv.setStyle("-fx-cursor: hand;");
                iv.setOnMouseClicked(e -> showFullPhoto(c.getImagePath()));
                card.getChildren().add(iv);
            } else {
                card.getChildren().add(Ui.label("Photo attached (file not found on this computer)", "faint"));
            }
        }
        card.getStyleClass().add("card-flat");
        card.setPadding(new Insets(18));
        return card;
    }

    private void showFullPhoto(String path) {
        Image full = Media.full(path);
        if (full == null) return;
        Dialogs.present(900, 650, close -> {
            ImageView iv = new ImageView(full);
            iv.setPreserveRatio(true);
            iv.setFitWidth(860);
            iv.setFitHeight(560);
            Button done = Ui.button("Close", "x", "btn-secondary");
            done.setOnAction(e -> close.run());
            VBox box = new VBox(12, iv, done);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(16));
            return box;
        });
    }

    private Node replyBox(String heading, Complaint c) {
        VBox box = new VBox(4, Ui.label(heading + (c.getRepliedAt() != null ? "  \u00b7  " + Fmt.when(c.getRepliedAt()) : ""), "h3"),
                Ui.wrap(c.getReply(), "body"));
        box.getStyleClass().add("card-soft");
        return box;
    }
}
