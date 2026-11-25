package com.messlens.ui;

import com.messlens.model.Upload;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Explains WHO made the final decision on a photo and WHY.
 *
 * A photo can be decided twice: the mess head approves or rejects it, and the warden may then confirm or
 * overturn that decision. The status on the photo is the final result, so showing only the head's note
 * (for example "Properly cooked" next to a Rejected badge) is confusing. This panel shows:
 *  1. the final outcome, who decided it, and the reason that belongs to that decision;
 *  2. a short trail: mess head decision + note, then the warden's decision + reason.
 */
public final class ReviewTrail {

    private ReviewTrail() {}

    public static VBox panel(Upload u) {
        VBox box = new VBox(12);
        String status = u.getStatus() == null ? "" : u.getStatus().toUpperCase();
        boolean pending = "PENDING".equals(status);
        boolean rejected = "REJECTED".equals(status);

        // ---- 1. outcome callout ----
        VBox outcome = new VBox(4);
        Label reasonLabel;
        if (pending) {
            outcome.getStyleClass().add("card-soft");
            outcome.getChildren().add(Ui.label("WAITING FOR REVIEW", "caps"));
            reasonLabel = Ui.wrap("Waiting for the mess head to review this photo.", "body");
        } else {
            boolean byWarden = u.isOverturned();
            outcome.getStyleClass().add(rejected ? "callout-bad" : "callout-ok");
            outcome.getChildren().add(Ui.label((rejected ? "REJECTED" : "APPROVED")
                    + (byWarden ? " BY THE WARDEN" : " BY THE MESS HEAD"), "caps"));
            String reason = byWarden ? u.getWardenRemark() : u.getRemark();
            reasonLabel = Ui.wrap(blank(reason) ? (rejected ? "No reason was written." : "Approved.") : reason, "body");
        }
        outcome.getChildren().add(reasonLabel);
        box.getChildren().add(outcome);

        // ---- 2. trail (only once somebody has decided) ----
        if (!pending) {
            VBox trail = new VBox(0);
            String head = u.headDecision();
            trail.getChildren().add(step("Mess head", Ui.statusBadge(head),
                    Fmt.when(u.getApprovedAt()),
                    blank(u.getRemark()) ? "No note was left." : u.getRemark(),
                    "REJECTED".equals(head) ? "bad" : "ok", u.getWardenAction() != null));
            if (u.getWardenAction() != null) {
                boolean ov = u.isOverturned();
                Label badge = Ui.badge(ov ? "Overturned" : "Confirmed", ov ? (rejected ? "bad" : "ok") : "ok");
                String note = blank(u.getWardenRemark()) ? (ov ? "No reason was written." : null) : u.getWardenRemark();
                trail.getChildren().add(step("Warden", badge, Fmt.when(u.getWardenAt()), note,
                        ov ? (rejected ? "bad" : "ok") : "ok", false));
            }
            box.getChildren().addAll(Ui.label("REVIEW TRAIL", "caps"), trail);
        }
        return box;
    }

    private static HBox step(String who, Label badge, String when, String note, String tone, boolean connector) {
        Region dot = new Region();
        dot.getStyleClass().addAll("trail-dot", "trail-dot-" + tone);
        VBox rail = new VBox(0, dot);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setMinWidth(14);
        rail.setPrefWidth(14);
        rail.setPadding(new javafx.geometry.Insets(5, 0, 0, 0));
        if (connector) {
            Region line = new Region();
            line.getStyleClass().add("trail-line");
            VBox.setVgrow(line, Priority.ALWAYS);
            rail.getChildren().add(line);
        }

        HBox title = new HBox(8, Ui.label(who, "h3"), badge);
        title.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(3, title);
        if (when != null && !when.isBlank()) text.getChildren().add(Ui.label(when, "faint"));
        if (note != null) text.getChildren().add(Ui.wrap(note, "muted"));
        text.setPadding(new javafx.geometry.Insets(0, 0, connector ? 14 : 0, 0));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, rail, text);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
