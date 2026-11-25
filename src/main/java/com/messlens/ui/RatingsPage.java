package com.messlens.ui;

import com.messlens.dao.MenuDao;
import com.messlens.dao.RatingDao;
import com.messlens.exception.MessLensException;
import com.messlens.model.MealRating;
import com.messlens.model.MenuEntry;
import com.messlens.model.MessInfo;
import com.messlens.model.RatingSummary;
import com.messlens.model.User;
import com.messlens.service.FeedbackService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Students rate today's meals from 1 to 5 stars. The mess head sees the averages and the written comments
 * for the last 7 days.
 */
public class RatingsPage implements Page {

    private final User user;
    private final boolean head;
    private final MenuDao menuDao = new MenuDao();
    private final RatingDao ratingDao = new RatingDao();
    private final FeedbackService service = new FeedbackService();
    private final VBox content = new VBox(18);
    private final Node view;

    public RatingsPage(User user) {
        this.user = user;
        this.head = "HEAD".equalsIgnoreCase(user.getRole());
        content.setPadding(new Insets(4, 32, 30, 32));
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "ratings"; }
    @Override public String navLabel() { return head ? "Meal Ratings" : "Rate Meals"; }
    @Override public String icon() { return "star"; }
    @Override public String title() { return head ? "Meal ratings" : "Rate today's meals"; }
    @Override public String subtitle() {
        return head ? "What students think of each meal, over the last 7 days." : "Tell the kitchen how today's food was.";
    }
    @Override public Node view() { return view; }

    @Override
    public Node headerActions() {
        if (!head) return null;
        Button refresh = Ui.button("Refresh", "refresh", "btn-secondary");
        refresh.setOnAction(e -> onShow());
        return refresh;
    }

    @Override
    public void onShow() {
        if (head) showHead(); else showStudent();
    }

    // ------------------------------------------------------------------ student

    private void showStudent() {
        int dayNo = LocalDate.now().getDayOfWeek().getValue();
        String today = LocalDate.now().toString();
        String type = user.getHostelId() != null ? menuDao.getHostelMessType(user.getHostelId()) : "SOUTH";
        Map<String, String> menu = new HashMap<>();
        for (MenuEntry e : menuDao.findByMessType(type)) if (e.getDayNo() == dayNo) menu.put(e.getMeal(), e.getItems());
        Map<String, MealRating> mine = ratingDao.findForUserOnDate(user.getId(), today);

        GridPane grid = twoColumns();
        for (int i = 0; i < MessInfo.MEALS.length; i++) {
            String meal = MessInfo.MEALS[i];
            grid.add(ratingCard(meal, menu.getOrDefault(meal, ""), mine.get(meal), dayNo), i % 2, i / 2);
        }
        content.getChildren().setAll(grid,
                Ui.label("You can change a rating again later today. Only the mess head sees your name next to a comment.", "faint"));
    }

    private Node ratingCard(String meal, String items, MealRating existing, int dayNo) {
        HBox top = new HBox(10, Ui.label(meal, "h2"), Ui.label(MessInfo.timeText(meal, MessInfo.isWeekend(dayNo)), "faint"), Ui.grow());
        top.setAlignment(Pos.CENTER_LEFT);
        if (existing != null) top.getChildren().add(Ui.badge("You gave " + existing.getStars() + " / 5", "primary"));

        int[] chosen = {existing != null ? existing.getStars() : 0};
        Button[] stars = new Button[5];
        HBox starRow = new HBox(4);
        for (int i = 0; i < 5; i++) {
            int value = i + 1;
            Button b = new Button();
            b.setGraphic(Icons.of("star", 30));
            b.getStyleClass().add("star-btn");
            b.setOnAction(e -> { chosen[0] = value; paint(stars, value); });
            stars[i] = b;
            starRow.getChildren().add(b);
        }
        paint(stars, chosen[0]);

        TextField note = new TextField(existing != null && existing.getNote() != null ? existing.getNote() : "");
        note.setPromptText("Add a comment (optional), e.g. the rice was undercooked");
        Button save = Ui.button(existing == null ? "Submit rating" : "Update rating", "check", "btn-primary", "btn-small");
        save.setOnAction(e -> {
            try {
                service.rateMeal(user, meal, chosen[0], note.getText());
                UiFx.toast("Thanks! Your " + meal.toLowerCase() + " rating was saved.", true);
                showStudent();
            } catch (MessLensException ex) {
                UiFx.toast(ex.getMessage(), false);
            }
        });
        HBox actions = new HBox(10, save);

        VBox card = Ui.card(12, top, Ui.wrap(shorten(items), "muted"), starRow, note, actions);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static void paint(Button[] stars, int count) {
        for (int i = 0; i < stars.length; i++) {
            stars[i].getStyleClass().remove("star-on");
            if (i < count) stars[i].getStyleClass().add("star-on");
        }
    }

    private static String shorten(String items) {
        if (items == null || items.isBlank()) return "No menu added for this meal.";
        return items.length() <= 110 ? items : items.substring(0, 107).replaceAll("[, ]+\\S*$", "") + " ...";
    }

    // ------------------------------------------------------------------ mess head

    private void showHead() {
        int hostelId = user.getHostelId() != null ? user.getHostelId() : 1;
        List<RatingSummary> summary = ratingDao.summaryForHostel(hostelId, LocalDate.now().minusDays(6).toString());
        Map<String, RatingSummary> byMeal = new HashMap<>();
        int total = 0;
        double starSum = 0;
        RatingSummary best = null, worst = null;
        for (RatingSummary s : summary) {
            byMeal.put(s.getMeal(), s);
            total += s.getCount();
            starSum += s.getAverage() * s.getCount();
            if (best == null || s.getAverage() > best.getAverage()) best = s;
            if (worst == null || s.getAverage() < worst.getAverage()) worst = s;
        }
        double overall = total == 0 ? 0 : starSum / total;

        Node kpis = Ui.kpiGrid(
                Ui.kpiCard("Average rating", total == 0 ? "-" : String.format("%.1f", overall), "Out of 5 stars", "star", "primary"),
                Ui.kpiCard("Ratings received", String.valueOf(total), "In the last 7 days", "check-square", "ok"),
                Ui.kpiCard("Best meal", best == null ? "-" : best.getMeal(), best == null ? "No ratings yet" : String.format("%.1f stars", best.getAverage()), "check-circle", "ok"),
                Ui.kpiCard("Needs attention", worst == null || worst == best ? "-" : worst.getMeal(),
                        worst == null || worst == best ? "Nothing to flag" : String.format("%.1f stars", worst.getAverage()), "alert", "warn"));

        GridPane dials = new GridPane();
        dials.setHgap(16);
        for (int i = 0; i < MessInfo.MEALS.length; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(25);
            c.setHgrow(Priority.ALWAYS);
            dials.getColumnConstraints().add(c);
            dials.add(mealDial(MessInfo.MEALS[i], byMeal.get(MessInfo.MEALS[i])), i, 0);
        }

        VBox comments = Ui.card(6, Ui.label("Recent comments", "h2"));
        List<MealRating> notes = ratingDao.recentWithNotes(hostelId, 12);
        if (notes.isEmpty()) {
            comments.getChildren().add(Ui.emptyState("message", "No comments yet", "Students' written comments about the food will appear here."));
        }
        for (MealRating r : notes) {
            VBox who = new VBox(2, Ui.label(r.getStudentName(), "h3"), Ui.label(Fmt.ago(r.getCreatedAt()), "faint"));
            who.setMinWidth(150);
            javafx.scene.control.Label text = Ui.wrap(r.getNote(), "body");
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(14, Ui.badge(r.getStars() + " / 5", tone(r.getStars())), Ui.badge(r.getMeal(), "neutral"), text, who);
            row.setAlignment(Pos.TOP_LEFT);
            row.getStyleClass().add("dish-row");
            comments.getChildren().add(row);
        }
        content.getChildren().setAll(kpis, dials, comments);
    }

    private Node mealDial(String meal, RatingSummary s) {
        StackPane ring = s == null
                ? Ui.ring(0, 112, 11, "-", "", false)
                : Ui.ring(s.getAverage() / 5.0, 112, 11, String.format("%.1f", s.getAverage()), tone(s.getAverage()), false);
        VBox card = Ui.card(8, ring, Ui.label(meal, "h2"),
                Ui.label(s == null ? "No ratings yet" : s.getCount() + (s.getCount() == 1 ? " rating" : " ratings"), "muted"));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static String tone(double stars) {
        return stars >= 4 ? "ok" : stars >= 3 ? "warn" : "bad";
    }

    private static GridPane twoColumns() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            c.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(c);
        }
        return grid;
    }
}
