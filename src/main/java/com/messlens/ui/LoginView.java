package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.exception.MessLensException;
import com.messlens.model.Hostel;
import com.messlens.model.User;
import com.messlens.service.AuthService;
import com.messlens.util.Config;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Sign-in screen: brand panel on the left, sign-in form and one-click demo accounts on the right. */
public class LoginView {

    private final String notice;

    public LoginView() { this(null); }

    public LoginView(String notice) { this.notice = notice; }

    private final AuthService authService = new AuthService();
    private final TextField userField = new TextField();
    private final PasswordField passField = new PasswordField();
    private final Label error = Ui.wrap("", "field-error");
    private final Button signIn = Ui.button("Sign in", "arrow-right", "btn-primary", "btn-large");

    private VBox brandContent;
    private VBox loginCard;

    public Parent getView() {
        StackPane brand = (StackPane) brandPanel();
        HBox root = new HBox(brand, formPanel());
        brand.prefWidthProperty().bind(root.widthProperty().multiply(0.46));
        javafx.application.Platform.runLater(() -> {
            int i = 0;
            for (Node n : brandContent.getChildren()) Motion.slideIn(n, -24, 560, 140 + 80L * i++);
            Motion.rise(loginCard, 28, 640);
        });
        return root;
    }

    private Node brandPanel() {
        StackPane mark = new StackPane(Icons.of("lens", 24, "icon-white"));
        mark.getStyleClass().add("brand-mark");
        HBox brand = Ui.row(12, mark, Ui.label("MessLens", "login-brand-name"), Ui.label("v1.0", "login-version"));

        Text t1 = new Text("Know exactly how your hostel food is ");
        t1.getStyleClass().add("login-title-text");
        Text t2 = new Text("made.");
        t2.getStyleClass().add("login-title-accent");
        TextFlow title = new TextFlow(t1, t2);
        title.setLineSpacing(2);

        Label lead = Ui.wrap("Photo-verified kitchen transparency, from the first ingredient to the last plate.", "login-brand-text");

        Node preview = glassPreview();

        HBox points = new HBox(18,
                miniPoint("camera", "Capture", "Staff photograph every stage"),
                miniPoint("shield", "Review", "Mess head approves or rejects"),
                miniPoint("check-circle", "Verify", "Students see what passed"));

        Region spacerTop = new Region();
        VBox.setVgrow(spacerTop, Priority.ALWAYS);
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        brandContent = new VBox(22, brand, title, lead, spacerTop, preview, spacer, points);
        brandContent.setAlignment(Pos.TOP_LEFT);
        brandContent.setPadding(new Insets(46, 48, 40, 48));

        // ---- soft drifting light behind everything
        Circle orbA = new Circle(270);
        orbA.getStyleClass().add("login-orb-a");
        Circle orbB = new Circle(240);
        orbB.getStyleClass().add("login-orb-b");
        Circle orbC = new Circle(140);
        orbC.getStyleClass().add("login-orb-c");
        Circle r1 = new Circle(190), r2 = new Circle(280), r3 = new Circle(370);
        for (Circle r : new Circle[]{r1, r2, r3}) r.getStyleClass().add("login-ring");
        Pane glows = new Pane(orbA, orbB, orbC, r1, r2, r3);
        glows.setMouseTransparent(true);
        Motion.drift(orbA, -26, 34, 9);
        Motion.drift(orbB, 34, -26, 11);
        Motion.drift(orbC, -20, -30, 8);

        StackPane p = new StackPane(glows, brandContent);
        p.getStyleClass().add("login-brand");
        p.setMinWidth(430);
        p.setMaxWidth(700);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(p.widthProperty());
        clip.heightProperty().bind(p.heightProperty());
        p.setClip(clip);
        p.layoutBoundsProperty().addListener((o, a, b) -> {
            double w = b.getWidth(), h = b.getHeight();
            orbA.setCenterX(w * 0.92);
            orbA.setCenterY(h * 0.04);
            orbB.setCenterX(w * 0.02);
            orbB.setCenterY(h * 0.97);
            orbC.setCenterX(w * 0.80);
            orbC.setCenterY(h * 0.60);
            for (Circle r : new Circle[]{r1, r2, r3}) {
                r.setCenterX(w);
                r.setCenterY(h);
            }
        });
        // on a short window the preview card makes way instead of pushing things off screen
        preview.visibleProperty().bind(p.heightProperty().greaterThan(760));
        preview.managedProperty().bind(p.heightProperty().greaterThan(760));
        return p;
    }

    /** A little "this is what the product does" card: one photo, its three stages, its fingerprint. */
    private Node glassPreview() {
        StackPane thumb = new StackPane(Icons.of("camera", 20, "icon-white"));
        thumb.getStyleClass().add("glass-thumb");
        VBox names = new VBox(2, Ui.label("Dal Tadka", "glass-title"), Ui.label("Lunch  ·  Serving photo", "glass-sub"));
        HBox top = new HBox(12, thumb, names, Ui.grow(), Ui.badge("Approved", "ok"));
        top.setAlignment(Pos.CENTER_LEFT);

        HBox track = new HBox(8);
        track.setAlignment(Pos.CENTER_LEFT);
        String[] steps = {"Ingredients", "Cooking", "Serving"};
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                Region line = new Region();
                line.getStyleClass().add("glass-line");
                line.setMinWidth(12);
                HBox.setHgrow(line, Priority.ALWAYS);
                track.getChildren().add(line);
            }
            StackPane dot = new StackPane(Icons.of("check", 12, "icon-white"));
            dot.getStyleClass().add("glass-dot-ok");
            track.getChildren().addAll(dot, Ui.label(steps[i], "glass-step"));
        }

        HBox print = new HBox(6, Icons.of("shield", 14), Ui.label("SHA-256  9dc9b7b3e8dc3121…  matches", "glass-mono"));
        print.setAlignment(Pos.CENTER_LEFT);

        VBox glass = new VBox(16, top, track, print);
        glass.getStyleClass().add("glass-card");

        HBox chip1 = new HBox(6, Icons.of("check-circle", 16, "icon-ok"), Ui.label("Approved by mess head", "float-chip-text"));
        chip1.setAlignment(Pos.CENTER_LEFT);
        chip1.getStyleClass().add("float-chip");
        chip1.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(chip1, Pos.TOP_RIGHT);
        chip1.setTranslateX(12);
        chip1.setTranslateY(-22);

        HBox chip2 = new HBox(6, Icons.of("lock", 15, "icon-primary"), Ui.label("Tamper-proof", "float-chip-text"));
        chip2.setAlignment(Pos.CENTER_LEFT);
        chip2.getStyleClass().add("float-chip");
        chip2.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(chip2, Pos.BOTTOM_LEFT);
        chip2.setTranslateX(-8);
        chip2.setTranslateY(20);

        Motion.drift(chip1, 0, -7, 2.6);
        Motion.drift(chip2, 0, 7, 3.2);

        StackPane wrap = new StackPane(glass, chip1, chip2);
        wrap.setPadding(new Insets(14, 0, 14, 0));
        return wrap;
    }

    private VBox miniPoint(String icon, String title, String sub) {
        VBox v = new VBox(8, Ui.iconTile(icon, "glass"), Ui.label(title, "mini-point-title"), Ui.wrap(sub, "mini-point-sub"));
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setPrefWidth(10);
        return v;
    }

    // ---- register form fields
    private final TextField regName = new TextField();
    private final TextField regNo = new TextField();
    private final TextField regEmail = new TextField();
    private final ComboBox<Hostel> regHostel = new ComboBox<>();
    private final PasswordField regPass = new PasswordField();
    private final PasswordField regPass2 = new PasswordField();
    private final Label regError = Ui.wrap("", "field-error");

    private Node formPanel() {
        userField.setPromptText("Registration number or username");
        passField.setPromptText("Your password");
        error.setVisible(false);
        error.setManaged(false);

        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setDefaultButton(true);
        signIn.setOnAction(e -> doLogin());
        passField.setOnAction(e -> doLogin());

        VBox signInForm = new VBox(14);
        if (notice != null && !notice.isBlank()) {
            HBox n = new HBox(10, Icons.of("clock", 18, "icon-warn"), Ui.wrap(notice, "body"));
            n.setAlignment(Pos.CENTER_LEFT);
            n.getStyleClass().add("callout-warn");
            signInForm.getChildren().add(n);
        }
        passField.getStyleClass().add("pass-with-eye");
        TextField passShown = new TextField();
        passShown.getStyleClass().add("pass-with-eye");
        passShown.setPromptText("Your password");
        passShown.textProperty().bindBidirectional(passField.textProperty());
        passShown.setVisible(false);
        passShown.setManaged(false);
        passShown.setOnAction(e -> doLogin());
        Button eye = new Button();
        eye.setGraphic(Icons.of("eye", 18));
        eye.getStyleClass().add("login-eye");
        eye.setFocusTraversable(false);
        eye.setOnAction(e -> {
            boolean show = !passShown.isVisible();
            passShown.setVisible(show);
            passShown.setManaged(show);
            passField.setVisible(!show);
            passField.setManaged(!show);
            eye.setGraphic(Icons.of(show ? "eye-off" : "eye", 18));
            (show ? passShown : passField).requestFocus();
        });
        StackPane passStack = new StackPane(passField, passShown, eye);
        StackPane.setAlignment(eye, Pos.CENTER_RIGHT);

        signInForm.getChildren().addAll(
                Ui.label("Welcome back", "h1"),
                Ui.label("Sign in to your mess workspace.", "muted"),
                Ui.field("REGISTRATION NUMBER / USERNAME", userField),
                Ui.field("PASSWORD", passStack),
                error, signIn);

        HBox orRow = new HBox(10, Ui.grow(), Ui.label("OR TRY A DEMO ACCOUNT", "caps"), Ui.grow());
        orRow.setAlignment(Pos.CENTER);
        // one tidy list: role on the left, username and password in two aligned columns
        Label hRole = Ui.label("ROLE", "caps");
        Label hUser = Ui.label("USERNAME", "caps");
        Label hPass = Ui.label("PASSWORD", "caps");
        hUser.setMinWidth(DEMO_USER_W);
        hUser.setPrefWidth(DEMO_USER_W);
        hPass.setMinWidth(DEMO_PASS_W);
        hPass.setPrefWidth(DEMO_PASS_W);
        HBox head = new HBox(8, hRole, Ui.grow(), hUser, hPass);
        head.setPadding(new Insets(0, 14, 0, 14));
        VBox grid = new VBox(8, head,
                demoChip("camera", "primary", "Kitchen staff", "Ganga kitchen", "worker1", "worker123"),
                demoChip("shield", "warn", "Mess head", "Ganga hostel", "head1", "head123"),
                demoChip("check-square", "primary", "Warden", "Second review", "warden1", "warden1234"),
                demoChip("user", "ok", "Student", "Browse food", "student1", "student123"),
                demoChip("lock", "warn", "Admin", "All messes and staff", "admin", "admin123"));
        VBox demo = new VBox(10, orRow, grid);
        demo.setPadding(new Insets(6, 0, 0, 0));
        VBox signInPane = new VBox(22, signInForm, demo);

        VBox registerPane = registerForm();
        registerPane.setVisible(false);
        registerPane.setManaged(false);

        // ---- tabs
        ToggleGroup tabs = new ToggleGroup();
        ToggleButton tabIn = Ui.segment("Sign in");
        ToggleButton tabUp = Ui.segment("New student");
        tabIn.setToggleGroup(tabs);
        tabUp.setToggleGroup(tabs);
        tabIn.setSelected(true);
        tabs.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null && a != null) { a.setSelected(true); return; }
            boolean up = b == tabUp;
            signInPane.setVisible(!up);
            signInPane.setManaged(!up);
            registerPane.setVisible(up);
            registerPane.setManaged(up);
        });
        HBox tabRow = new HBox(8, tabIn, tabUp);

        VBox card = new VBox(20, tabRow, signInPane, registerPane);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(460);
        card.setMinWidth(380);
        loginCard = card;
        Label foot = Ui.label("SRM University AP  ·  MessLens 1.0", "login-foot");
        VBox.setMargin(foot, new Insets(18, 0, 0, 0));
        VBox wrap = new VBox(card, foot);
        wrap.setAlignment(Pos.CENTER);
        wrap.setPadding(new Insets(36));
        wrap.getStyleClass().add("login-side");

        ScrollPane sp = new ScrollPane(wrap);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("login-side");
        HBox.setHgrow(sp, Priority.ALWAYS);
        return sp;
    }

    private VBox registerForm() {
        regName.setPromptText("As on your ID card");
        regNo.setPromptText("e.g. AP23110010123");
        regEmail.setPromptText("yourname" + AuthService.EMAIL_DOMAIN);
        regHostel.setItems(FXCollections.observableArrayList(new HostelDao().findAll()));
        regHostel.setPromptText("Choose your hostel / mess");
        regHostel.setMaxWidth(Double.MAX_VALUE);
        regPass.setPromptText("At least 6 characters");
        regPass2.setPromptText("Type the password again");
        regError.setVisible(false);
        regError.setManaged(false);

        Button create = Ui.button("Create my account", "arrow-right", "btn-primary", "btn-large");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> doRegister());
        regPass2.setOnAction(e -> doRegister());

        return new VBox(12,
                Ui.label("Create your account", "h1"),
                Ui.wrap("For SRM AP students. You will sign in with your registration number.", "muted"),
                Ui.field("FULL NAME", regName),
                Ui.field("REGISTRATION NUMBER", regNo),
                Ui.field("COLLEGE EMAIL", regEmail),
                Ui.field("HOSTEL / MESS", regHostel),
                Ui.field("PASSWORD", Ui.passwordWithEye(regPass)),
                Ui.field("CONFIRM PASSWORD", Ui.passwordWithEye(regPass2)),
                regError, create,
                Ui.wrap("Kitchen staff and mess heads do not register here: the administrator creates their accounts.", "faint"));
    }

    private void doRegister() {
        regError.setVisible(false);
        regError.setManaged(false);
        try {
            Hostel h = regHostel.getValue();
            User u = authService.registerStudent(regName.getText(), regNo.getText(), regEmail.getText(),
                    h == null ? null : h.getId(), regPass.getText(), regPass2.getText());
            // sign in straight away
            User signedIn = authService.login(u.getUsername(), regPass.getText());
            AppNavigator.routeUser(signedIn);
        } catch (MessLensException ex) {
            regError.setText(ex.getMessage());
            regError.setVisible(true);
            regError.setManaged(true);
        } catch (Exception ex) {
            regError.setText("Something went wrong: " + ex.getMessage());
            regError.setVisible(true);
            regError.setManaged(true);
        }
    }

    private static final double DEMO_USER_W = 76;
    private static final double DEMO_PASS_W = 92;

    private Button demoChip(String icon, String tone, String role, String place, String user, String pass) {
        StackPane tile = Ui.iconTile(icon, tone);
        tile.setStyle("-fx-min-width: 34; -fx-pref-width: 34; -fx-max-width: 34; -fx-min-height: 34; -fx-pref-height: 34; -fx-max-height: 34;");
        Label r = Ui.label(role, "h3");
        Label u = Ui.label(user, "mono", "muted");
        Label pw = Ui.label(pass, "mono", "muted");
        u.setMinWidth(DEMO_USER_W);
        u.setPrefWidth(DEMO_USER_W);
        pw.setMinWidth(DEMO_PASS_W);
        pw.setPrefWidth(DEMO_PASS_W);
        HBox g = new HBox(8, tile, r, Ui.grow(), u, pw);
        g.setAlignment(Pos.CENTER_LEFT);
        Button b = new Button();
        b.setGraphic(g);
        g.prefWidthProperty().bind(b.widthProperty().subtract(28));   // fill the row so the columns line up
        b.getStyleClass().add("demo-grid-chip");
        b.setStyle("-fx-padding: 7 14 7 14;");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setTooltip(new javafx.scene.control.Tooltip(role + "  -  " + place + "  (click to sign in)"));
        b.setOnAction(e -> {
            userField.setText(user);
            passField.setText(pass);
            doLogin();
        });
        return b;
    }

    private void doLogin() {
        error.setVisible(false);
        error.setManaged(false);
        try {
            User u = authService.login(userField.getText().trim(), passField.getText());
            AppNavigator.routeUser(u);
        } catch (MessLensException ex) {
            error.setText(ex.getMessage());
            error.setVisible(true);
            error.setManaged(true);
        } catch (Exception ex) {
            error.setText("Something went wrong: " + ex.getMessage());
            error.setVisible(true);
            error.setManaged(true);
        }
    }

    @SuppressWarnings("unused")
    private static boolean devMode() {
        return Config.isDevMode();
    }
}
