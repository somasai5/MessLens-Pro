package com.messlens.ui;

import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.UploadService;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every photo of the hostel with its decision - searchable, filterable and exportable. */
public class HeadRecordsPage implements Page {

    private final User user;
    private final UploadService uploads = new UploadService();
    private final int hostelId;

    private final ObservableList<Upload> all = FXCollections.observableArrayList();
    private final FilteredList<Upload> filtered = new FilteredList<>(all, u -> true);
    private final TableView<Upload> table = Tables.newTable("file-text", "No records yet",
            "Photos sent by the kitchen will be listed here.");
    private final Map<String, ToggleButton> filterButtons = new LinkedHashMap<>();
    private final ToggleGroup filterGroup = new ToggleGroup();
    private final TextField search = new TextField();
    private final Label countLabel = Ui.label("", "muted");
    private final Node view;

    public HeadRecordsPage(User user) {
        this.user = user;
        this.hostelId = user.getHostelId() != null ? user.getHostelId() : 1;
        this.view = build();
    }

    @Override public String id() { return "records"; }
    @Override public String navLabel() { return "All Records"; }
    @Override public String icon() { return "file-text"; }
    @Override public String title() { return "All records"; }
    @Override public String subtitle() { return "Every photo ever sent for your hostel. Click any row to see the photo and who decided."; }
    @Override public Node view() { return view; }

    @Override
    public Node headerActions() {
        Button export = Ui.button("Export CSV", "download", "btn-primary");
        export.setOnAction(e -> CsvExport.run(user, uploads));
        return export;
    }

    @Override
    public void onShow() {
        List<Upload> list = uploads.getAllUploads(user, hostelId);
        all.setAll(list);
        int p = 0, a = 0, r = 0;
        for (Upload u : list) {
            String s = u.getStatus() == null ? "" : u.getStatus().toUpperCase();
            if (s.equals("PENDING")) p++;
            else if (s.equals("APPROVED")) a++;
            else if (s.equals("REJECTED")) r++;
        }
        filterButtons.get("ALL").setText("All  " + list.size());
        filterButtons.get("PENDING").setText("Pending  " + p);
        filterButtons.get("APPROVED").setText("Approved  " + a);
        filterButtons.get("REJECTED").setText("Rejected  " + r);
        applyFilter();
    }

    private Node build() {
        HBox filters = new HBox(8);
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

        search.setPromptText("Search dish or worker");
        search.setPrefWidth(260);
        search.textProperty().addListener((o, a, b) -> applyFilter());

        HBox top = new HBox(12, filters, Ui.grow(), countLabel, search);
        top.setAlignment(Pos.CENTER_LEFT);

        // Six columns that always fit: the stage sits under the dish, and the review date, note and
        // any warden decision share one column. Nothing is ever pushed off the right edge.
        table.getColumns().addAll(List.of(
                Tables.photoCol(false),
                Tables.dishCol("Dish", true),
                Tables.statusCol(),
                Tables.textCol("Sent by", Upload::getWorkerUsername, 80, 100),
                Tables.whenCol("Sent", Upload::getUploadedAt, 125, 150),
                Tables.reviewCol("Reviewed", 150, 260)));
        table.setItems(filtered);
        table.setRowFactory(tv -> {
            TableRow<Upload> row = new TableRow<>();
            row.setCursor(javafx.scene.Cursor.HAND);
            row.setOnMouseClicked(e -> {
                // one click opens the big photo with its full review history
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && !row.isEmpty()) PhotoViewer.show(row.getItem());
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox box = new VBox(14, top, table);
        box.setPadding(new Insets(4, 32, 24, 32));
        return box;
    }

    private void applyFilter() {
        ToggleButton t = (ToggleButton) filterGroup.getSelectedToggle();
        String status = t == null ? "ALL" : (String) t.getUserData();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        filtered.setPredicate(u -> {
            boolean okStatus = status.equals("ALL") || status.equalsIgnoreCase(u.getStatus());
            boolean okText = q.isEmpty()
                    || (u.getDishName() != null && u.getDishName().toLowerCase().contains(q))
                    || (u.getWorkerUsername() != null && u.getWorkerUsername().toLowerCase().contains(q));
            return okStatus && okText;
        });
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " record" : " records"));
    }
}
