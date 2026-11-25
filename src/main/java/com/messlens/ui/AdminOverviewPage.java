package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.dao.UserDao;
import com.messlens.model.Hostel;
import com.messlens.model.MessMetrics;
import com.messlens.model.User;
import com.messlens.service.UploadService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Administrator "Overview": every mess at a glance, and which ones still need a mess head. */
public class AdminOverviewPage implements Page {

    private final User user;
    private final AppShell shell;
    private final HostelDao hostelDao = new HostelDao();
    private final UserDao userDao = new UserDao();
    private final UploadService uploads = new UploadService();

    private final VBox content = new VBox(18);
    private final Node view;

    public AdminOverviewPage(User user, AppShell shell) {
        this.user = user;
        this.shell = shell;
        content.setPadding(new Insets(4, 32, 30, 32));
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "admin"; }
    @Override public String navLabel() { return "Overview"; }
    @Override public String icon() { return "bar-chart"; }
    @Override public String title() { return "Administration"; }
    @Override public String subtitle() { return "All messes, their staff and registered students."; }
    @Override public Node view() { return view; }

    @Override
    public void onShow() {
        List<Hostel> hostels = hostelDao.findAll();
        List<User> heads = userDao.findByRoles(null, "HEAD");
        List<User> workers = userDao.findByRoles(null, "WORKER");

        int uncovered = 0;
        for (Hostel h : hostels) if (heads.stream().noneMatch(u -> h.getId() == (u.getHostelId() == null ? -1 : u.getHostelId()))) uncovered++;

        content.getChildren().setAll(
                hero(uncovered),
                Ui.kpiGrid(
                        Ui.kpiCard("MESSES", String.valueOf(hostels.size()), "Hostels and mess halls", "home", "primary"),
                        Ui.kpiCard("MESS HEADS", String.valueOf(heads.size()), uncovered + " mess(es) without a head", "shield", uncovered > 0 ? "warn" : "ok"),
                        Ui.kpiCard("KITCHEN STAFF", String.valueOf(workers.size()), "Can send photos", "camera", "primary"),
                        Ui.kpiCard("STUDENTS", String.valueOf(userDao.countByRole("STUDENT")), "Registered accounts", "user", "ok")),
                messList(hostels, heads, workers));
    }

    private Node hero(int uncovered) {
        Button go = Ui.button("Manage staff", "arrow-right", "btn-light");
        go.setOnAction(e -> shell.show("staff"));
        VBox left = new VBox(8,
                Ui.label("ADMINISTRATOR", "hero-eyebrow"),
                Ui.wrap("Welcome, " + user.displayName(), "hero-title"),
                Ui.wrap(uncovered > 0
                        ? uncovered + (uncovered == 1 ? " mess has" : " messes have") + " no mess head yet. Create their accounts to start photo verification."
                        : "Every mess has a mess head. Add kitchen staff or review the pass rates below.", "hero-text"),
                go);
        VBox.setMargin(go, new Insets(8, 0, 0, 0));
        HBox box = new HBox(24, left, Ui.grow());
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("hero");
        HBox.setHgrow(left, Priority.ALWAYS);
        return box;
    }

    private Node messList(List<Hostel> hostels, List<User> heads, List<User> workers) {
        VBox card = Ui.card(0);
        Label head = Ui.label("Messes", "h2");
        VBox.setMargin(head, new Insets(0, 0, 10, 0));
        card.getChildren().add(head);
        for (Hostel h : hostels) {
            User mine = null;
            for (User u : heads) if (u.getHostelId() != null && u.getHostelId() == h.getId()) { mine = u; break; }
            int staff = 0;
            for (User u : workers) if (u.getHostelId() != null && u.getHostelId() == h.getId()) staff++;
            MessMetrics m = uploads.getHostelMetrics(user, h.getId());
            boolean reviewed = m.getApprovedCount() + m.getRejectedCount() > 0;
            String tone = !reviewed ? "" : m.getApprovalRate() >= 80 ? "ok" : m.getApprovalRate() >= 50 ? "warn" : "bad";

            StackPane ring = Ui.ring(m.getApprovalRate() / 100.0, 56, 6, reviewed ? String.format("%.0f", m.getApprovalRate()) : "-", tone, false);
            VBox name = new VBox(2, Ui.label(h.getName(), "h3"),
                    Ui.label(m.getTotalSubmissions() + " photos  ·  " + m.getPendingCount() + " waiting for review", "muted"));
            name.setPrefWidth(260);
            Node headBadge = mine == null ? Ui.badge("No mess head", "warn") : Ui.badge("Head: " + mine.displayName(), "primary");
            HBox row = new HBox(16, ring, name, headBadge, Ui.badge(staff + (staff == 1 ? " kitchen staff" : " kitchen staff"), "neutral"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("dish-row");
            card.getChildren().add(row);
        }
        return card;
    }
}
