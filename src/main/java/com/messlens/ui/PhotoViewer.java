package com.messlens.ui;

import com.messlens.model.Upload;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Shows one evidence photo big, together with everything known about it (who decided, why, the review trail).
 *
 * It opens ON the current page (an in-window card, see {@link Dialogs}) instead of in a separate window, so it
 * always appears where the person is looking, also in full screen. Esc or a click outside closes it.
 */
public final class PhotoViewer {

    private PhotoViewer() {}

    public static void show(Upload u) {
        show(u, null);
    }

    /**
     * @param onWithdraw when given and the photo is still pending, a "Withdraw this photo" button is shown;
     *                   the viewer closes first and then this runs.
     */
    public static void show(Upload u, Runnable onWithdraw) {
        if (u == null || !Dialogs.canHost()) return;
        boolean canWithdraw = onWithdraw != null && "PENDING".equalsIgnoreCase(u.getStatus());
        boolean[] withdraw = {false};
        Dialogs.present(1040, 640, close -> build(u, canWithdraw, close, withdraw));
        if (withdraw[0] && onWithdraw != null) onWithdraw.run();
    }

    private static Node build(Upload u, boolean canWithdraw, Runnable close, boolean[] withdraw) {
        // ----- photo side -----
        StackPane photo = new StackPane();
        photo.getStyleClass().add("photo-frame");
        photo.setMinSize(0, 0);
        photo.setPrefWidth(10);
        HBox.setHgrow(photo, Priority.ALWAYS);
        Image img = Media.full(u.getImagePath());
        if (img != null) {
            ImageView iv = Ui.fittedImage(photo, 24);
            iv.setImage(img);
            photo.getChildren().add(iv);
        } else {
            photo.getChildren().add(Ui.label("Photo file not found on this computer", "muted"));
        }

        // ----- details side (scrolls when long; buttons stay in view) -----
        Label dish = Ui.wrap(u.getDishName(), "h1");
        Label meta = Ui.label(u.getMeal() + "  ·  " + u.getStage(), "muted");
        VBox info = new VBox(14, dish, meta, Ui.statusBadge(u.getStatus()), Ui.divider());
        info.getChildren().add(infoRow("Photo taken", Fmt.when(u.getUploadedAt())));
        if (u.getWorkerUsername() != null) info.getChildren().add(infoRow("Sent by", u.getWorkerUsername()));
        if (u.getHostelName() != null) info.getChildren().add(infoRow("Hostel", u.getHostelName()));
        info.getChildren().add(ReviewTrail.panel(u));
        info.getChildren().add(QualityPanel.panel(u));

        if (u.getImageHash() != null) {
            String h = u.getImageHash();
            Label fp = Ui.label(h.length() > 20 ? h.substring(0, 20) + "..." : h, "mono", "muted");
            Tooltip.install(fp, new Tooltip("SHA-256 fingerprint:\n" + h));
            Button copy = Ui.button("Copy", null, "btn-link");
            copy.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(h);
                Clipboard.getSystemClipboard().setContent(cc);
                copy.setText("Copied");
            });
            info.getChildren().add(new VBox(4, Ui.label("TAMPER-PROOF FINGERPRINT", "caps"), Ui.row(6, fp, copy)));
        }
        info.setPadding(new Insets(26, 26, 12, 26));

        ScrollPane scroll = Ui.scroll(info);
        scroll.setFitToHeight(false);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox footer = new VBox(8);
        footer.setPadding(new Insets(8, 26, 24, 26));
        if (canWithdraw) {
            Button w = Ui.button("Withdraw this photo", "trash", "btn-danger");
            w.setMaxWidth(Double.MAX_VALUE);
            w.setOnAction(e -> {
                withdraw[0] = true;
                close.run();
            });
            footer.getChildren().add(w);
        }
        Button done = Ui.button("Close", null, "btn-primary");
        done.setMaxWidth(Double.MAX_VALUE);
        done.setOnAction(e -> close.run());
        footer.getChildren().add(done);

        VBox side = new VBox(scroll, footer);
        side.setStyle("-fx-background-color: white;");
        side.setMinWidth(340);
        side.setPrefWidth(340);
        side.setMaxWidth(340);

        HBox root = new HBox(photo, side);
        root.setAlignment(Pos.TOP_LEFT);
        return root;
    }

    private static VBox infoRow(String label, String value) {
        Label v = Ui.wrap(value, "body");
        v.setAlignment(Pos.CENTER_LEFT);
        return new VBox(2, Ui.label(label.toUpperCase(), "caps"), v);
    }
}
