package com.messlens.ui;

import com.messlens.model.Upload;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/** Column builders shared by every table that lists evidence uploads. */
public final class Tables {

    private Tables() {}

    public static TableView<Upload> newTable(String emptyIcon, String emptyTitle, String emptyText) {
        TableView<Upload> t = new TableView<>();
        t.setFixedCellSize(64);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(Ui.emptyState(emptyIcon, emptyTitle, emptyText));
        return t;
    }

    private static TableColumn<Upload, Upload> base(String title, double min, double pref) {
        TableColumn<Upload, Upload> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        c.setMinWidth(min);
        c.setPrefWidth(pref);
        c.setSortable(false);
        return c;
    }

    /** Thumbnail column. */
    public static TableColumn<Upload, Upload> photoCol() {
        return photoCol(false);
    }

    /** Thumbnail column; when openOnClick is true, clicking the picture opens the big photo viewer. */
    public static TableColumn<Upload, Upload> photoCol(boolean openOnClick) {
        TableColumn<Upload, Upload> c = base("Photo", 84, 84);
        c.setMaxWidth(96);
        c.setResizable(false);
        c.setCellFactory(col -> new TableCell<>() {
            private final StackPane thumb = Ui.thumbnail(null, 60, 45);

            {
                if (openOnClick) {
                    thumb.setStyle("-fx-cursor: hand;");
                    thumb.setOnMouseClicked(e -> {
                        if (getItem() != null) PhotoViewer.show(getItem());
                    });
                }
            }

            @Override
            protected void updateItem(Upload u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }
                Ui.setThumb(thumb, u, 60, 45);
                setGraphic(thumb);
            }
        });
        return c;
    }

    /** Dish name on line 1; "Meal · Stage" (or just the meal) on line 2. */
    public static TableColumn<Upload, Upload> dishCol(String title, boolean withStage) {
        TableColumn<Upload, Upload> c = base(title, 170, 260);
        c.setCellFactory(col -> new TableCell<>() {
            private final Label name = Ui.label("", "h3");
            private final Label sub = Ui.label("", "muted");
            private final VBox box = new VBox(2, name, sub);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                name.setMaxWidth(Double.MAX_VALUE);
            }

            {
                    // long names end in "..." instead of pushing the table wider than the window
                    box.prefWidthProperty().bind(widthProperty().subtract(22));
                    box.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                }

            @Override
            protected void updateItem(Upload u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }
                name.setText(u.getDishName());
                sub.setText(withStage ? u.getMeal() + "  ·  " + u.getStage() : u.getMeal());
                setGraphic(box);
            }
        });
        return c;
    }

    public static TableColumn<Upload, Upload> statusCol() {
        TableColumn<Upload, Upload> c = base("Status", 140, 170);
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Upload u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                if (u.isOverturned()) {
                    // the final result was changed by the warden: say so right in the table
                    VBox v = new VBox(3, Ui.statusBadge(u.getStatus()), Ui.label("Changed by warden", "faint"));
                    v.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(v);
                } else if ("CONFIRMED".equalsIgnoreCase(u.getWardenAction())) {
                    VBox v = new VBox(3, Ui.statusBadge(u.getStatus()), Ui.label("Confirmed by warden", "faint"));
                    v.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(v);
                } else {
                    setGraphic(Ui.statusBadge(u.getStatus()));
                }
            }
        });
        return c;
    }

    /**
     * "Review" column: when it was decided on the first line, the reviewer's note (or the warden's reason)
     * on the second. Replaces three separate columns so every date fits on screen.
     */
    public static TableColumn<Upload, Upload> reviewCol(String title, double min, double pref) {
        TableColumn<Upload, Upload> c = base(title, min, pref);
        c.setCellFactory(col -> new TableCell<>() {
            private final Label when = Ui.label("", "body");
            private final Label note = Ui.label("", "faint");
            private final VBox box = new VBox(2, when, note);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                box.prefWidthProperty().bind(widthProperty().subtract(22));
                box.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }

            @Override
            protected void updateItem(Upload u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }
                boolean pending = "PENDING".equalsIgnoreCase(u.getStatus());
                when.setText(pending ? "Waiting for review" : Fmt.when(u.getApprovedAt()));
                String text;
                if (pending) text = "";
                else if (u.isOverturned()) text = "Warden: " + (u.getWardenRemark() == null || u.getWardenRemark().isBlank() ? "no reason given" : u.getWardenRemark());
                else text = u.getRemark() == null || u.getRemark().isBlank() ? "No note" : u.getRemark();
                note.setText(text);
                note.setVisible(!text.isEmpty());
                note.setManaged(!text.isEmpty());
                setGraphic(box);
            }
        });
        return c;
    }

    public static TableColumn<Upload, Upload> textCol(String title, Function<Upload, String> f, double min, double pref) {
        TableColumn<Upload, Upload> c = base(title, min, pref);
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Upload u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                    return;
                }
                String s = f.apply(u);
                setText(s == null || s.isBlank() ? "-" : s);
            }
        });
        return c;
    }

    /** Friendly date column ("Today, 3:42 PM"). */
    public static TableColumn<Upload, Upload> whenCol(String title, Function<Upload, String> f, double min, double pref) {
        return textCol(title, u -> Fmt.when(f.apply(u)), min, pref);
    }
}
