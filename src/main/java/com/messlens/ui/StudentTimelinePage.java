package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.model.Hostel;
import com.messlens.model.MessMetrics;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.UploadService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Student "Food Timeline": verified photos per dish (Ingredients -> Cooking -> Serving). Filters apply instantly. */
public class StudentTimelinePage implements Page {

    private static final String[] STAGES = {"Ingredients", "Cooking", "Serving"};

    private final User user;
    private final UploadService uploads = new UploadService();
    private final HostelDao hostelDao = new HostelDao();

    private final ComboBox<Hostel> hostelBox = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final CheckBox allDates = new CheckBox("Show all dates");
    private final ToggleGroup mealGroup = new ToggleGroup();
    private final HBox summary = new HBox(16);
    private final VBox cards = new VBox(16);
    private boolean loading;
    private final Node view;

    public StudentTimelinePage(User user) {
        this.user = user;
        this.view = build();
    }

    @Override public String id() { return "timeline"; }
    @Override public String navLabel() { return "Food Timeline"; }
    @Override public String icon() { return "calendar"; }
    @Override public String title() { return "Food timeline"; }
    @Override public String subtitle() { return "Photos of how your food was prepared, checked by the mess head."; }
    @Override public Node view() { return view; }

    @Override
    public void onShow() {
        load();
    }

    private Node build() {
        List<Hostel> hostels = hostelDao.findAll();
        hostelBox.setItems(FXCollections.observableArrayList(hostels));
        Hostel mine = null;
        if (user.getHostelId() != null) mine = hostelDao.findById(user.getHostelId());
        if (mine != null) {
            for (Hostel h : hostels) if (h.getId() == mine.getId()) hostelBox.setValue(h);
        }
        if (hostelBox.getValue() == null && !hostels.isEmpty()) {
            // open on the student's own hostel / mess when they chose one at registration
            Hostel own = null;
            for (Hostel h : hostels) if (user.getHostelId() != null && h.getId() == user.getHostelId()) own = h;
            hostelBox.setValue(own != null ? own : hostels.get(0));
        }
        hostelBox.setPrefWidth(180);
        datePicker.setPrefWidth(170);

        HBox meals = new HBox(8);
        for (String m : new String[]{"All meals", "Breakfast", "Lunch", "Dinner"}) {
            ToggleButton t = Ui.segment(m);
            t.setToggleGroup(mealGroup);
            t.setUserData(m.equals("All meals") ? "ALL" : m);
            meals.getChildren().add(t);
        }
        mealGroup.selectToggle(mealGroup.getToggles().get(0));
        mealGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null && a != null) a.setSelected(true);
            else load();
        });

        hostelBox.valueProperty().addListener((o, a, b) -> load());
        datePicker.valueProperty().addListener((o, a, b) -> load());
        allDates.selectedProperty().addListener((o, a, b) -> {
            datePicker.setDisable(b);
            load();
        });

        HBox filters = new HBox(16,
                Ui.field("HOSTEL", hostelBox),
                Ui.field("DATE", new HBox(12, datePicker, allDates)),
                Ui.field("MEAL", meals));
        ((HBox) ((VBox) filters.getChildren().get(1)).getChildren().get(1)).setAlignment(Pos.CENTER_LEFT);
        filters.setAlignment(Pos.BOTTOM_LEFT);
        VBox filterCard = Ui.card(0, filters);

        summary.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(16, filterCard, summary, cards);
        content.setPadding(new Insets(4, 32, 28, 32));
        return Ui.scroll(content);
    }

    private void load() {
        if (loading || hostelBox.getValue() == null) return;
        loading = true;
        try {
            Hostel hostel = hostelBox.getValue();
            String date = allDates.isSelected() || datePicker.getValue() == null ? null : datePicker.getValue().toString();
            ToggleButton t = (ToggleButton) mealGroup.getSelectedToggle();
            String meal = t == null ? "ALL" : (String) t.getUserData();

            List<Upload> approved = uploads.getApprovedTimeline(user, hostel.getId(), date, meal);
            MessMetrics m = uploads.getHostelMetrics(user, hostel.getId());

            // group by day + meal + dish
            Map<String, List<Upload>> groups = new LinkedHashMap<>();
            for (Upload u : approved) {
                String day = u.getUploadedAt() == null || u.getUploadedAt().length() < 10 ? "" : u.getUploadedAt().substring(0, 10);
                groups.computeIfAbsent(day + "|" + u.getMeal() + "|" + u.getDishName().toLowerCase(), k -> new ArrayList<>()).add(u);
            }
            List<List<Upload>> ordered = new ArrayList<>(groups.values());
            ordered.sort(Comparator.comparing((List<Upload> l) ->
                    l.stream().map(Upload::getUploadedAt).max(String::compareTo).orElse("")).reversed());

            summary.getChildren().setAll(
                    stat(String.valueOf(ordered.size()), "dishes verified"),
                    stat(String.valueOf(approved.size()), "photos approved"),
                    stat(m.getFormattedApprovalRate(), hostel.getName() + " pass rate"));

            cards.getChildren().clear();
            if (ordered.isEmpty()) {
                VBox empty = Ui.emptyState("image", "No verified photos found",
                        "Nothing has been approved for " + hostel.getName()
                                + (date != null ? " on " + Fmt.when(date + " 00:00:00").split(",")[0] : "")
                                + (!"ALL".equals(meal) ? " (" + meal + ")" : "")
                                + " yet. Try another date or tick \"Show all dates\".");
                VBox card = Ui.card(0, empty);
                cards.getChildren().add(card);
                return;
            }
            for (List<Upload> g : ordered) cards.getChildren().add(dishCard(g, date == null));
        } finally {
            loading = false;
        }
    }

    private Node stat(String number, String caption) {
        Label n = Ui.label(number, "h1");
        VBox box = new VBox(0, n, Ui.label(caption, "muted"));
        box.getStyleClass().add("stat-card");
        return box;
    }

    private Node dishCard(List<Upload> group, boolean showDate) {
        Upload first = group.get(0);
        Map<String, List<Upload>> byStage = new LinkedHashMap<>();   // every approved photo of a stage, newest first
        for (Upload u : group) byStage.computeIfAbsent(u.getStage(), k -> new ArrayList<>()).add(u);
        for (List<Upload> l : byStage.values()) {
            l.sort((a, b) -> String.valueOf(b.getUploadedAt()).compareTo(String.valueOf(a.getUploadedAt())));
        }
        int done = 0;
        for (String s : STAGES) if (byStage.containsKey(s)) done++;

        HBox header = new HBox(10, Ui.label(first.getDishName(), "h2"), Ui.badge(first.getMeal(), "neutral"));
        if (showDate) header.getChildren().add(Ui.label(Fmt.when(first.getUploadedAt()).split(",")[0], "muted"));
        header.getChildren().addAll(Ui.grow(),
                Ui.badge(done + " of 3 stages verified", done == 3 ? "ok" : "warn"));
        header.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / 3);
            c.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(c);
            grid.add(stageTile(i + 1, STAGES[i], byStage.get(STAGES[i])), i, 0);
        }
        return Ui.card(16, header, grid);
    }

    private Node stageTile(int number, String stage, List<Upload> photos) {
        Upload u = photos == null || photos.isEmpty() ? null : photos.get(0);
        Label title = Ui.label(number + "   " + stage.toUpperCase(), "caps");
        StackPane frame = new StackPane();
        frame.setMinHeight(120);
        frame.prefHeightProperty().bind(frame.widthProperty().multiply(0.72));
        VBox tile = new VBox(8, title, frame);
        tile.setMaxWidth(Double.MAX_VALUE);

        if (u == null) {
            frame.getStyleClass().add("photo-frame-light");
            frame.getChildren().add(new VBox(6, Icons.of("clock", 26, "icon-faint"), Ui.label("Not verified yet", "faint")));
            ((VBox) frame.getChildren().get(0)).setAlignment(Pos.CENTER);
            tile.getStyleClass().add("photo-tile-empty");
            return tile;
        }
        frame.getStyleClass().add("photo-frame");
        ImageView iv = Ui.fittedImage(frame, 0);
        iv.setImage(Media.full(u.getImagePath()));
        Rectangle clip = new Rectangle();
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        frame.setClip(clip);
        frame.getChildren().add(iv);

        // both decisions, side by side: the mess head first, then the warden
        String headSays = "APPROVED".equals(u.headDecision()) ? "Approved" : "Rejected";
        HBox ok = new HBox(6, Icons.of("check-circle", 16, "icon-ok"),
                Ui.label("Mess head: " + headSays + "  ·  " + Fmt.when(u.getApprovedAt() != null ? u.getApprovedAt() : u.getUploadedAt()), "field-ok"));
        ok.setAlignment(Pos.CENTER_LEFT);
        String wardenText;
        String wardenStyle = "field-ok";
        if (u.getWardenAction() == null) {
            wardenText = "Warden: not reviewed yet";
            wardenStyle = "faint";
        } else if (u.isOverturned()) {
            wardenText = "Warden: Approved (changed the mess head's decision)";
        } else {
            wardenText = "Warden: Confirmed";
        }
        HBox warden = new HBox(6, Icons.of(u.getWardenAction() == null ? "clock" : "check-circle", 16,
                u.getWardenAction() == null ? "icon-faint" : "icon-ok"), Ui.label(wardenText, wardenStyle));
        warden.setAlignment(Pos.CENTER_LEFT);
        tile.getChildren().addAll(ok, warden);
        tile.getStyleClass().add("photo-tile");
        tile.setOnMouseClicked(e -> PhotoViewer.show(u));
        if (photos.size() > 1) {
            // more than one photo for this stage: a small strip of the others, each opens big on click
            HBox strip = new HBox(6, Ui.label("+" + (photos.size() - 1) + " more", "faint"));
            strip.setAlignment(Pos.CENTER_LEFT);
            for (int i = 1; i < photos.size(); i++) {
                Upload extra = photos.get(i);
                javafx.scene.image.Image th = Media.thumb(extra.getImagePath(), 56, 40);
                if (th == null) continue;
                ImageView tiv = new ImageView(th);
                tiv.setFitWidth(56);
                tiv.setFitHeight(40);
                tiv.setPreserveRatio(true);
                tiv.setStyle("-fx-cursor: hand;");
                tiv.setOnMouseClicked(e -> { PhotoViewer.show(extra); e.consume(); });
                strip.getChildren().add(tiv);
            }
            tile.getChildren().add(strip);
        }
        return tile;
    }
}
