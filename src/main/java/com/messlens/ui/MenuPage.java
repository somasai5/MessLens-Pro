package com.messlens.ui;

import com.messlens.dao.MenuDao;
import com.messlens.exception.MessLensException;
import com.messlens.model.MenuEntry;
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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Weekly menu for everyone. Pick South / North / International and a day to see the four meals.
 * A mess head can edit the menu of the mess their hostel serves.
 */
public class MenuPage implements Page {

    private final User user;
    private final MenuDao menuDao = new MenuDao();
    private final FeedbackService service = new FeedbackService();
    private final VBox content = new VBox(18);
    private final Node view;

    private String ownType = "SOUTH";   // the menu this user's hostel serves
    private String shownType = "SOUTH"; // the menu on screen
    private int shownDay = 1;           // 1 = Monday ... 7 = Sunday

    public MenuPage(User user) {
        this.user = user;
        content.setPadding(new Insets(4, 32, 30, 32));
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "menu"; }
    @Override public String navLabel() { return "Weekly Menu"; }
    @Override public String icon() { return "calendar"; }
    @Override public String title() { return "Weekly menu"; }
    @Override public String subtitle() { return "What is served this week, meal by meal."; }
    @Override public Node view() { return view; }

    @Override
    public Node headerActions() {
        Button refresh = Ui.button("Refresh", "refresh", "btn-secondary");
        refresh.setOnAction(e -> render());
        return refresh;
    }

    @Override
    public void onShow() {
        if (user.getHostelId() != null) ownType = menuDao.getHostelMessType(user.getHostelId());
        shownType = ownType;
        shownDay = LocalDate.now().getDayOfWeek().getValue();
        render();
    }

    private boolean isHead() {
        return "HEAD".equalsIgnoreCase(user.getRole());
    }

    private void render() {
        int today = LocalDate.now().getDayOfWeek().getValue();
        boolean canEdit = isHead() && shownType.equals(ownType);

        // ---- which menu: South / North / International
        HBox typeRow = new HBox(8);
        ToggleGroup types = new ToggleGroup();
        for (String t : MessInfo.MESS_TYPES) {
            ToggleButton b = Ui.segment(MessInfo.label(t) + (user.getHostelId() != null && t.equals(ownType) ? "  (your mess)" : ""));
            b.setToggleGroup(types);
            b.setUserData(t);
            b.setSelected(t.equals(shownType));
            typeRow.getChildren().add(b);
        }
        types.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) { if (old != null) old.setSelected(true); return; }
            shownType = (String) now.getUserData();
            render();
        });
        HBox controls = new HBox(12, typeRow, Ui.grow());
        controls.setAlignment(Pos.CENTER_LEFT);
        if (isHead() && user.getHostelId() != null) controls.getChildren().add(ownMessChooser());

        // ---- which day
        HBox dayRow = new HBox(8);
        ToggleGroup days = new ToggleGroup();
        for (int d = 1; d <= 7; d++) {
            ToggleButton b = Ui.segment(MessInfo.DAYS[d - 1].substring(0, 3) + (d == today ? "  \u00b7  today" : ""));
            b.setToggleGroup(days);
            b.setUserData(d);
            b.setSelected(d == shownDay);
            dayRow.getChildren().add(b);
        }
        days.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) { if (old != null) old.setSelected(true); return; }
            shownDay = (Integer) now.getUserData();
            render();
        });

        // ---- the four meals of the chosen day
        Map<String, String> items = new HashMap<>();
        for (MenuEntry e : menuDao.findByMessType(shownType)) items.put(e.getDayNo() + "|" + e.getMeal(), e.getItems());

        HBox dayHead = new HBox(10, Ui.label(MessInfo.DAYS[shownDay - 1], "h2"));
        dayHead.setAlignment(Pos.CENTER_LEFT);
        if (shownDay == today) dayHead.getChildren().add(Ui.badge("Today", "primary"));
        dayHead.getChildren().addAll(Ui.grow(), Ui.label(MessInfo.label(shownType) + " mess menu", "muted"));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            c.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(c);
        }
        LocalTime now = LocalTime.now();
        for (int i = 0; i < MessInfo.MEALS.length; i++) {
            String meal = MessInfo.MEALS[i];
            boolean serving = shownDay == today && MessInfo.isServingNow(meal, shownDay, now);
            Node card = mealCard(meal, items.getOrDefault(shownDay + "|" + meal, ""), canEdit, serving);
            grid.add(card, i % 2, i / 2);
        }

        content.getChildren().setAll(controls, dayRow, dayHead, grid);
        if (isHead() && !canEdit) {
            content.getChildren().add(Ui.label("You can edit only your own mess menu (" + MessInfo.label(ownType) + "). Switch back to change it.", "faint"));
        }
        content.getChildren().add(timingsCard());
        content.getChildren().add(Ui.label("Red dishes are non-veg.  (NI) = North Indian dish,  (SI) = South Indian dish.", "faint"));
    }

    /** Mess head only: choose which of the three menus this hostel serves. */
    private Node ownMessChooser() {
        ComboBox<String> box = new ComboBox<>();
        for (String t : MessInfo.MESS_TYPES) box.getItems().add(MessInfo.label(t));
        box.setValue(MessInfo.label(ownType));
        box.setOnAction(e -> {
            String picked = MessInfo.typeOf(box.getValue());
            if (picked == null || picked.equals(ownType)) return;
            try {
                service.setMessType(user, picked);
                ownType = picked;
                shownType = picked;
                UiFx.toast("Your hostel now serves the " + MessInfo.label(picked) + " menu.", true);
                render();
            } catch (MessLensException ex) {
                UiFx.toast(ex.getMessage(), false);
            }
        });
        HBox h = new HBox(8, Ui.label("Your hostel serves", "muted"), box);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    private Node mealCard(String meal, String items, boolean canEdit, boolean serving) {
        Label time = Ui.label(MessInfo.timeText(meal, MessInfo.isWeekend(shownDay)), "faint");
        HBox head = new HBox(10, Ui.label(meal, "h2"), time, Ui.grow());
        head.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(12);
        VBox card = Ui.card(14, head, body);
        card.getStyleClass().add("menu-card");
        if (serving) {
            card.getStyleClass().add("menu-card-now");
            head.getChildren().add(Ui.badge("Serving now", "ok"));
        }
        showItems(body, items);
        if (canEdit) {
            Button edit = Ui.button("Edit", null, "btn-ghost", "btn-small");
            edit.setOnAction(e -> openEditor(body, meal, items));
            head.getChildren().add(edit);
        }
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMaxHeight(Double.MAX_VALUE);
        return card;
    }

    private void showItems(VBox body, String items) {
        if (items == null || items.isBlank()) {
            body.getChildren().setAll(Ui.label("Nothing added for this meal yet.", "muted"));
            return;
        }
        FlowPane chips = new FlowPane(8, 8);
        for (String item : items.split("\\s*,\\s*")) {
            if (item.isBlank()) continue;
            Label chip = new Label(item);
            chip.getStyleClass().add("menu-chip");
            if (MessInfo.isNonVeg(item)) chip.getStyleClass().add("menu-chip-nonveg");
            chips.getChildren().add(chip);
        }
        body.getChildren().setAll(chips);
    }

    private void openEditor(VBox body, String meal, String items) {
        TextArea area = new TextArea(items);
        area.setWrapText(true);
        area.setPrefRowCount(5);
        Button save = Ui.button("Save", "check", "btn-primary", "btn-small");
        Button cancel = Ui.button("Cancel", null, "btn-secondary", "btn-small");
        save.setOnAction(e -> {
            try {
                service.saveMenu(user, shownType, shownDay, meal, area.getText());
                UiFx.toast(meal + " updated.", true);
                render();
            } catch (MessLensException ex) {
                UiFx.toast(ex.getMessage(), false);
            }
        });
        cancel.setOnAction(e -> showItems(body, items));
        body.getChildren().setAll(area, Ui.label("Separate the dishes with commas.", "faint"), new HBox(8, save, cancel));
    }

    private Node timingsCard() {
        VBox card = Ui.card(10, Ui.label("Mess timings", "h2"));
        card.getChildren().addAll(
                timing("Breakfast, Monday to Friday", MessInfo.timeText("Breakfast", false)),
                timing("Breakfast, Saturday and Sunday", MessInfo.timeText("Breakfast", true)),
                timing("Lunch", MessInfo.timeText("Lunch", false)),
                timing("Snacks", MessInfo.timeText("Snacks", false)),
                timing("Dinner", MessInfo.timeText("Dinner", false)));
        return card;
    }

    private Node timing(String name, String time) {
        HBox row = new HBox(10, Ui.label(name, "body"), Ui.grow(), Ui.label(time, "h3"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
