package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.exception.MessLensException;
import com.messlens.model.Hostel;
import com.messlens.model.User;
import com.messlens.service.AuthService;
import com.messlens.util.AlertUtil;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff accounts. The administrator creates mess heads and kitchen staff for any mess;
 * a mess head creates kitchen staff for their own mess only. New accounts get a one-time
 * temporary password which must be changed at first sign-in.
 */
public class StaffPage implements Page {

    private final User actor;
    private final boolean admin;
    private final AuthService auth = new AuthService();
    private final HostelDao hostelDao = new HostelDao();

    private final ComboBox<String> roleBox = new ComboBox<>();
    private final ComboBox<Hostel> hostelBox = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final TextField userField = new TextField();
    private final Label error = Ui.wrap("", "field-error");
    private final VBox credentials = new VBox(8);
    private final ObservableList<User> rows = FXCollections.observableArrayList();
    private final Map<Integer, String> hostelNames = new HashMap<>();
    private final Node view;

    public StaffPage(User actor) {
        this.actor = actor;
        this.admin = "ADMIN".equalsIgnoreCase(actor.getRole());
        this.view = build();
    }

    @Override public String id() { return "staff"; }
    @Override public String navLabel() { return admin ? "Staff" : "Kitchen Staff"; }
    @Override public String icon() { return "user"; }
    @Override public String title() { return admin ? "Staff accounts" : "Kitchen staff"; }
    @Override public String subtitle() {
        return admin ? "Create mess heads, wardens and kitchen staff for every mess."
                : "Create and manage the kitchen staff of your mess.";
    }
    @Override public Node view() { return view; }

    @Override
    public void onShow() {
        List<Hostel> hostels = hostelDao.findAll();
        hostelNames.clear();
        for (Hostel h : hostels) hostelNames.put(h.getId(), h.getName());
        if (admin) {
            Hostel keep = hostelBox.getValue();
            hostelBox.setItems(FXCollections.observableArrayList(hostels));
            if (keep != null) for (Hostel h : hostels) if (h.getId() == keep.getId()) hostelBox.setValue(h);
        }
        reload();
    }

    private void reload() {
        rows.setAll(auth.listStaff(actor));
    }

    // ---------------------------------------------------------------- layout

    private Node build() {
        roleBox.setItems(FXCollections.observableArrayList("Kitchen staff", "Mess head", "Warden"));
        roleBox.setValue("Kitchen staff");
        roleBox.setMaxWidth(Double.MAX_VALUE);
        hostelBox.setPromptText("Choose a mess");
        hostelBox.setMaxWidth(Double.MAX_VALUE);
        nameField.setPromptText("Full name");
        userField.setPromptText(admin ? "e.g. annapurna.head" : "e.g. ganga.cook2");

        error.setVisible(false);
        error.setManaged(false);
        credentials.setVisible(false);
        credentials.setManaged(false);

        Button create = Ui.button("Create account", "check", "btn-primary");
        create.setOnAction(e -> create());

        HBox fields;
        if (admin) {
            fields = new HBox(14, grow(Ui.field("ROLE", roleBox)), grow(Ui.field("MESS / HOSTEL", hostelBox)),
                    grow(Ui.field("FULL NAME", nameField)), grow(Ui.field("USERNAME", userField)));
        } else {
            Hostel mine = actor.getHostelId() == null ? null : hostelDao.findById(actor.getHostelId());
            Label mess = Ui.label(mine == null ? "Your mess" : mine.getName(), "h3");
            VBox messBox = Ui.field("MESS / HOSTEL", mess);
            fields = new HBox(14, grow(messBox), grow(Ui.field("FULL NAME", nameField)), grow(Ui.field("USERNAME", userField)));
        }
        fields.setAlignment(Pos.BOTTOM_LEFT);
        HBox actions = new HBox(12, create, Ui.wrap(
                "A temporary password is generated. The person must replace it when they first sign in.", "faint"));
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox form = Ui.card(14, Ui.label(admin ? "Add a staff account" : "Add kitchen staff", "h2"), fields, error, actions);

        VBox listCard = Ui.card(12, Ui.label(admin ? "All staff" : "Your kitchen staff", "h2"), table());

        VBox content = new VBox(18, form, credentials, listCard);
        content.setPadding(new Insets(4, 32, 30, 32));
        return Ui.scroll(content);
    }

    private static Node grow(Node n) {
        HBox.setHgrow(n, Priority.ALWAYS);
        if (n instanceof VBox) ((VBox) n).setMaxWidth(Double.MAX_VALUE);
        return n;
    }

    private TableView<User> table() {
        TableView<User> t = new TableView<>(rows);
        t.setFixedCellSize(60);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(Ui.emptyState("user", "No staff yet",
                admin ? "Create the first mess head above." : "Create your first kitchen staff account above."));
        t.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(240.0, rows.size() * 60.0 + 48.0), rows));

        TableColumn<User, User> who = col("Person", 150, 360);   // takes the spare width
        who.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                VBox box = new VBox(2, Ui.label(u.displayName(), "h3"), Ui.label(u.getUsername(), "muted"));
                box.setAlignment(Pos.CENTER_LEFT);      // centre the two lines vertically in the row
                setGraphic(box);
            }
        });
        TableColumn<User, User> role = col("Role", 110, 120);
        role.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                String r = u.getRole() == null ? "" : u.getRole().toUpperCase();
                setGraphic(Ui.badge(com.messlens.security.Roles.label(r), "HEAD".equals(r) ? "primary" : "WARDEN".equals(r) ? "ok" : "neutral"));
            }
        });
        TableColumn<User, User> mess = col("Mess", 100, 120);
        mess.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : hostelNames.getOrDefault(u.getHostelId() == null ? -1 : u.getHostelId(), "-"));
            }
        });
        TableColumn<User, User> state = col("Status", 150, 160);
        state.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                setGraphic(u.isMustChangePassword() ? Ui.badge("Not signed in yet", "warn") : Ui.badge("Active", "ok"));
            }
        });
        TableColumn<User, User> act = col("", 336, 336);
        act.setMaxWidth(336);          // fixed width: both buttons always fit, nothing gets clipped
        act.setResizable(false);
        act.setCellFactory(c -> new TableCell<>() {
            private final Button reset = Ui.button("Reset password", "key", "btn-secondary", "btn-small");
            private final Button remove = Ui.button("Remove", "trash", "btn-danger", "btn-small");
            private final HBox box = new HBox(8, reset, remove);
            {
                box.setAlignment(Pos.CENTER_RIGHT);
                reset.setMinWidth(Region.USE_PREF_SIZE);
                remove.setMinWidth(Region.USE_PREF_SIZE);
                reset.setOnAction(e -> { if (getItem() != null) resetPassword(getItem()); });
                remove.setOnAction(e -> { if (getItem() != null) remove(getItem()); });
            }
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setGraphic(empty || u == null ? null : box);
            }
        });
        t.getColumns().add(who);
        t.getColumns().add(role);
        t.getColumns().add(mess);
        t.getColumns().add(state);
        t.getColumns().add(act);
        return t;
    }

    private TableColumn<User, User> col(String title, double min, double pref) {
        TableColumn<User, User> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        c.setMinWidth(min);
        c.setPrefWidth(pref);
        c.setSortable(false);
        return c;
    }

    // ---------------------------------------------------------------- actions

    private void create() {
        hideError();
        try {
            String picked = roleBox.getValue();
            String role = !admin ? "WORKER" : "Mess head".equals(picked) ? "HEAD" : "Warden".equals(picked) ? "WARDEN" : "WORKER";
            Integer hostelId = admin ? (hostelBox.getValue() == null ? null : hostelBox.getValue().getId()) : actor.getHostelId();
            String temp = auth.createStaff(actor, role, nameField.getText(), userField.getText(), hostelId);
            String username = userField.getText().trim().toLowerCase();
            showCredentials(nameField.getText().trim(), username, temp);
            nameField.clear();
            userField.clear();
            reload();
            UiFx.toast("Account created", true);
        } catch (MessLensException ex) {
            error.setText(ex.getMessage());
            error.setVisible(true);
            error.setManaged(true);
        }
    }

    private void resetPassword(User u) {
        if (!AlertUtil.showConfirmation("Reset password?", "Give " + u.displayName() + " a new temporary password. They will be asked to change it when they next sign in.",
                "Reset password", "Cancel", "key", false)) return;
        try {
            String temp = auth.resetStaffPassword(actor, u);
            showCredentials(u.displayName(), u.getUsername(), temp);
            reload();
        } catch (MessLensException ex) {
            AlertUtil.showError("Could not reset", ex.getMessage());
        }
    }

    private void remove(User u) {
        if (!AlertUtil.showConfirmation("Remove this account?", "The account of " + u.displayName() + " will be removed. This cannot be undone.",
                "Remove account", "Keep account", "trash", true)) return;
        try {
            auth.removeStaff(actor, u);
            reload();
            UiFx.toast("Account removed", true);
        } catch (MessLensException ex) {
            AlertUtil.showError("Cannot remove", ex.getMessage());
        }
    }

    private void hideError() {
        error.setVisible(false);
        error.setManaged(false);
    }

    private void showCredentials(String name, String username, String temp) {
        Label title = Ui.label("Account ready for " + name, "h3");
        Label u = Ui.label("Username:  " + username, "body");
        Label p = Ui.label("Temporary password:  " + temp, "h3");
        Button copy = Ui.button("Copy login details", "file-text", "btn-secondary", "btn-small");
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString("MessLens login\nUsername: " + username + "\nTemporary password: " + temp
                    + "\n(You will be asked to choose a new password at first sign-in.)");
            Clipboard.getSystemClipboard().setContent(cc);
            UiFx.toast("Copied", true);
        });
        Label note = Ui.wrap("Share these now. The password is shown only once; use Reset password if it is lost.", "muted");
        credentials.getChildren().setAll(title, u, p, copy, note);
        credentials.getStyleClass().remove("callout-ok");
        credentials.getStyleClass().add("callout-ok");
        credentials.setVisible(true);
        credentials.setManaged(true);
    }
}
