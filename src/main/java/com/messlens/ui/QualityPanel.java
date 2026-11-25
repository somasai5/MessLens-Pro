package com.messlens.ui;

import com.messlens.model.QualityChecklist;
import com.messlens.model.Upload;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Map;

/** Read-only view of the quality checklist the kitchen filled in with a photo. */
public final class QualityPanel {

    private QualityPanel() {}

    public static VBox panel(Upload u) {
        VBox box = new VBox(8);
        box.getChildren().add(Ui.label("QUALITY CHECKLIST", "caps"));
        String stored = u == null ? null : u.getQualityCheck();
        if (stored == null || stored.isBlank()) {
            box.getChildren().add(Ui.wrap("No checklist was recorded with this photo.", "muted"));
            return box;
        }
        Map<String, String> answers = QualityChecklist.decode(stored);
        int bad = QualityChecklist.problems(u.getStage(), stored).size();
        Label summary = Ui.wrap(bad == 0 ? "All standards met" : bad + (bad == 1 ? " item needs attention" : " items need attention"), "body");
        VBox head = new VBox(2, summary);
        head.getStyleClass().add(bad == 0 ? "callout-ok" : "callout-warn");
        box.getChildren().add(head);

        for (QualityChecklist.Item it : QualityChecklist.items(u.getStage())) {
            String v = answers.get(it.key());
            if (it.number() && (v == null || v.isBlank())) continue;      // optional reading left empty
            boolean ok = QualityChecklist.passes(it, v);
            String text = it.number() ? it.label() + ": " + v.trim() + " " + it.unit() + "  (" + limit(it) + ")" : it.label();
            HBox row = new HBox(8, Icons.of(ok ? "check-circle" : "alert", 16, ok ? "icon-ok" : "icon-bad"),
                    Ui.wrap(text, ok ? "field-ok" : "field-error"));
            row.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(row);
        }
        return box;
    }

    private static String limit(QualityChecklist.Item it) {
        if (it.min() != null) return "at least " + trim(it.min()) + " " + it.unit();
        if (it.max() != null) return "at most " + trim(it.max()) + " " + it.unit();
        return "";
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
