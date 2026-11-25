package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.model.Hostel;
import com.messlens.model.MessMetrics;
import com.messlens.service.UploadService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Student "Hostel Quality": one card per hostel with its pass rate. */
public class StudentHostelsPage implements Page {

    private final UploadService uploads = new UploadService();
    private final HostelDao hostelDao = new HostelDao();
    private final FlowPane cards = new FlowPane(18, 18);
    private final Node view;
    private final VBox content;

    private final com.messlens.model.User user;

    public StudentHostelsPage(com.messlens.model.User user) {
        this.user = user;
        content = new VBox(16, cards,
                Ui.wrap("Pass rate = photos approved by the mess head, out of all photos that have been reviewed.", "faint"));
        content.setPadding(new Insets(4, 32, 28, 32));
        // cards share the full width: as many columns as fit, each column an equal share
        content.widthProperty().addListener((o, a, w) -> layoutCards(w.doubleValue() - 64));
        this.view = Ui.scroll(content);
    }

    @Override public String id() { return "hostels"; }
    @Override public String navLabel() { return "Hostel Quality"; }
    @Override public String icon() { return "home"; }
    @Override public String title() { return "Hostel quality"; }
    @Override public String subtitle() { return "How often each hostel's kitchen photos pass inspection."; }
    @Override public Node view() { return view; }

    @Override
    public void onShow() {
        cards.getChildren().clear();
        for (Hostel h : hostelDao.findAll()) cards.getChildren().add(card(h, uploads.getHostelMetrics(user, h.getId())));
        layoutCards(content.getWidth() - 64);
    }

    private void layoutCards(double avail) {
        if (avail <= 0) return;
        int cols = Math.max(1, (int) ((avail + 18) / (360 + 18)));
        double w = Math.floor((avail - 18 * (cols - 1)) / cols) - 1;
        for (Node n : cards.getChildren()) {
            if (n instanceof Region r) { r.setPrefWidth(w); r.setMinWidth(w); r.setMaxWidth(w); }
        }
    }

    private static Node stat(String number, String caption, String tone) {
        Label n = Ui.label(number, "stat-num", "stat-num-" + tone);
        Label c = Ui.label(caption, "stat-cap");
        VBox v = new VBox(1, n, c);
        v.setAlignment(Pos.CENTER_LEFT);
        v.getStyleClass().addAll("stat-pill", "stat-pill-" + tone);
        v.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(v, Priority.ALWAYS);
        return v;
    }

    private Node card(Hostel h, MessMetrics m) {
        double rate = m.getApprovalRate();
        boolean reviewed = m.getApprovedCount() + m.getRejectedCount() > 0;
        String tone = !reviewed ? "" : rate >= 80 ? "ok" : rate >= 50 ? "warn" : "bad";
        String verdict = !reviewed ? "No reviews yet" : rate >= 80 ? "Excellent" : rate >= 50 ? "Needs attention" : "Poor";
        String badgeKind = !reviewed ? "neutral" : tone;
        StackPane ring = Ui.ring(rate / 100.0, 124, 12, reviewed ? String.format("%.0f%%", rate) : "–", tone, false);

        HBox head = new HBox(8, Ui.label("HOSTEL", "eyebrow"), Ui.grow(), Ui.badge(verdict, badgeKind));
        head.setAlignment(Pos.CENTER_LEFT);

        String sub = reviewed ? m.getFormattedApprovalRate() + " of reviewed photos passed"
                              : "Waiting for the first reviewed photo";
        VBox info = new VBox(6,
                Ui.label(h.getName(), "hostel-name"),
                Ui.wrap(sub, "muted"),
                Ui.label(m.getUniqueDishesCount() + " dishes photographed", "faint"));
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox top = new HBox(20, ring, info);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox counts = new HBox(10,
                stat(String.valueOf(m.getApprovedCount()), "APPROVED", "ok"),
                stat(String.valueOf(m.getRejectedCount()), "REJECTED", "bad"),
                stat(String.valueOf(m.getPendingCount()), "PENDING", "warn"));

        VBox card = Ui.card(16, head, top, Ui.divider(), counts);
        card.getStyleClass().add("hostel-card");
        card.setPrefWidth(380);
        card.setMinWidth(320);
        return card;
    }
}
