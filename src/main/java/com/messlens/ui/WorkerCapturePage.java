package com.messlens.ui;

import com.messlens.dao.HostelDao;
import com.messlens.exception.DuplicateEvidenceException;
import com.messlens.exception.ValidationException;
import com.messlens.model.Hostel;
import com.messlens.model.Upload;
import com.messlens.model.User;
import com.messlens.service.UploadService;
import com.messlens.util.AlertUtil;
import com.messlens.util.Config;
import com.messlens.util.MacCamera;
import com.messlens.util.WebcamService;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Worker "Capture" page: choose meal, dish and stage, take (or pick) a photo, send it for approval.
 * Everything needed to finish a submission is visible without scrolling.
 */
public class WorkerCapturePage implements Page {

    private static final String[] STAGES = {"Ingredients", "Cooking", "Serving"};
    private static final String[] MEALS = {"Breakfast", "Lunch", "Dinner"};
    private static final String[] COMMON_DISHES = {"Rice", "Chapati", "Dal Tadka", "Sambar", "Idli", "Dosa",
            "Poha", "Upma", "Veg Biryani", "Paneer Butter Masala", "Mixed Veg Curry", "Curd Rice"};
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");

    private final User user;
    private final AppShell shell;
    private final UploadService uploads = new UploadService();
    private final WebcamService webcam = new WebcamService();
    private final String hostelName;
    private final Node view;

    // form
    private final ToggleGroup mealGroup = new ToggleGroup();
    private final ToggleGroup stageGroup = new ToggleGroup();
    private final ComboBox<String> dishBox = new ComboBox<>();
    private final Map<String, ToggleButton> stageTiles = new HashMap<>();
    private final Map<String, StackPane> stageDots = new HashMap<>();
    private final Map<String, Label> stageStatus = new HashMap<>();
    private Label readyHint;
    private Button submitBtn;

    // ---- quality checklist (rebuilt for the selected stage)
    private final VBox checklistBox = new VBox(8);
    private final java.util.Map<String, javafx.scene.control.CheckBox> checkAnswers = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, javafx.scene.control.TextField> numberAnswers = new java.util.LinkedHashMap<>();

    /** Shows the standards to confirm for the selected stage: tick what is true, type the temperatures. */
    private void rebuildChecklist() {
        checklistBox.getChildren().clear();
        checkAnswers.clear();
        numberAnswers.clear();
        checklistBox.getChildren().add(Ui.wrap("Tick each point that is true. Leave a box empty if the standard is not met; the mess head will see it.", "muted"));
        for (com.messlens.model.QualityChecklist.Item it : com.messlens.model.QualityChecklist.items(selectedStage())) {
            if (it.number()) {
                javafx.scene.control.TextField tf = new javafx.scene.control.TextField();
                tf.setPromptText(it.required() ? "required" : "optional");
                tf.setPrefWidth(90);
                tf.setMaxWidth(90);
                numberAnswers.put(it.key(), tf);
                Label unit = Ui.label(it.unit(), "muted");
                Label name = Ui.wrap(it.label(), "body");
                HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
                name.setMaxWidth(Double.MAX_VALUE);
                HBox row = new HBox(8, name, tf, unit);
                row.setAlignment(Pos.CENTER_LEFT);
                checklistBox.getChildren().add(row);
            } else {
                javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(it.label());
                cb.setWrapText(true);
                checkAnswers.put(it.key(), cb);
                checklistBox.getChildren().add(cb);
            }
        }
    }

    /** The answers as stored with the photo, or null when the stage has no checklist. */
    private String checklistAnswers() {
        java.util.Map<String, String> a = new java.util.LinkedHashMap<>();
        for (var e : checkAnswers.entrySet()) a.put(e.getKey(), e.getValue().isSelected() ? "1" : "0");
        for (var e : numberAnswers.entrySet()) a.put(e.getKey(), e.getValue().getText() == null ? "" : e.getValue().getText().trim());
        return com.messlens.model.QualityChecklist.encode(a);
    }

    // camera
    private StackPane cameraFrame;
    private ImageView preview;
    private VBox placeholder;
    private Label placeholderTitle;
    private Label placeholderText;
    private Label liveBadge;
    private Label clockLabel;
    private Label stageOverlay;
    private Button takeBtn;
    private Button retakeBtn;
    private Button chooseBtn;
    private Label cameraNote;
    private Timeline clock;
    private MacCamera.Live macLive;
    private boolean macLiveFailed;

    // state
    private byte[] imageBytes;
    private boolean hasPhoto;
    private boolean gotFirstFrame;
    private boolean cameraOpen;   // the camera is only on after the worker presses "Take a photo"
    private List<Upload> myUploads = new ArrayList<>();

    public WorkerCapturePage(User user, AppShell shell) {
        this.user = user;
        this.shell = shell;

        String h = "your kitchen";
        if (user.getHostelId() != null) {
            Hostel hostel = new HostelDao().findById(user.getHostelId());
            if (hostel != null) h = hostel.getName() + " kitchen";
        }
        this.hostelName = h;

        this.view = Ui.scroll(build());
        startClock();
    }

    @Override public String id() { return "capture"; }
    @Override public String navLabel() { return "Capture"; }
    @Override public String icon() { return "camera"; }
    @Override public String title() { return "Capture evidence"; }
    @Override public String subtitle() { return "Photograph each stage of a dish for " + hostelName + ". The mess head reviews every photo."; }
    @Override public Node view() { return view; }

    @Override
    public void onShow() {
        reloadUploads();
        applyInputMode();
        refreshSubmitState();
    }

    @Override
    public void onHide() {
        if (!hasPhoto) closeCamera();     // never leave the camera running on another page
    }

    @Override
    public void dispose() {
        webcam.stop();
        stopMacLive();
        if (clock != null) clock.stop();
    }

    // ================================================================ layout

    private Node build() {
        Node cameraCard = buildCameraCard();
        Node formCard = buildFormCard();

        HBox.setHgrow(cameraCard, Priority.ALWAYS);
        HBox row = new HBox(20, cameraCard, formCard);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(4, 32, 28, 32));
        return row;
    }

    private Node buildCameraCard() {
        cameraFrame = new StackPane();
        cameraFrame.getStyleClass().add("camera-frame");
        cameraFrame.setMinHeight(300);
        cameraFrame.setMaxHeight(540);
        cameraFrame.prefHeightProperty().bind(cameraFrame.widthProperty().multiply(0.66));

        preview = Ui.fittedImage(cameraFrame, 8);

        placeholderTitle = Ui.label("", "h2");
        placeholderTitle.setStyle("-fx-text-fill: white;");
        placeholderText = Ui.wrap("", "muted");
        placeholderText.setStyle("-fx-text-fill: #94a3b8; -fx-text-alignment: center;");
        placeholderText.setMaxWidth(320);
        placeholder = new VBox(10, Icons.of("camera", 44, "icon-faint"), placeholderTitle, placeholderText);
        placeholder.setAlignment(Pos.CENTER);

        liveBadge = Ui.label("LIVE", "overlay-pill", "overlay-live");
        clockLabel = Ui.label("", "overlay-pill", "mono");
        stageOverlay = Ui.label("", "overlay-pill");

        BorderPane overlay = new BorderPane();
        overlay.setPadding(new Insets(12));
        overlay.setMouseTransparent(true);
        HBox top = new HBox(liveBadge);
        HBox bottom = new HBox(8, clockLabel, Ui.grow(), stageOverlay);
        overlay.setTop(top);
        overlay.setBottom(bottom);

        cameraFrame.getChildren().addAll(placeholder, preview, overlay);

        takeBtn = Ui.button("Take a photo", "camera", "btn-primary", "btn-large");
        takeBtn.setOnAction(e -> handleTake());
        retakeBtn = Ui.button("Retake", "refresh", "btn-secondary", "btn-large");
        retakeBtn.setOnAction(e -> onRetakeClicked());
        chooseBtn = Ui.button("Choose a photo from this computer", "image", "btn-secondary", "btn-large");
        chooseBtn.setOnAction(e -> handleChooseFile());

        FlowPane buttons = new FlowPane(10, 10, takeBtn, retakeBtn, chooseBtn);

        cameraNote = Ui.wrap("", "muted");

        VBox card = Ui.card(14, cameraFrame, buttons, cameraNote);
        return card;
    }

    private Node buildFormCard() {
        // ---- meal
        HBox meals = new HBox(8);
        for (String m : MEALS) {
            ToggleButton t = Ui.segment(m);
            t.setToggleGroup(mealGroup);
            t.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(t, Priority.ALWAYS);
            meals.getChildren().add(t);
        }
        selectMeal(mealForNow());
        keepOneSelected(mealGroup);
        mealGroup.selectedToggleProperty().addListener((o, a, b) -> {
            updateStageProgress();
            refreshSubmitState();
        });

        // ---- dish
        dishBox.setEditable(true);
        dishBox.setPromptText("Type or pick a dish");
        dishBox.setMaxWidth(Double.MAX_VALUE);
        dishBox.getItems().setAll(COMMON_DISHES);
        dishBox.getEditor().textProperty().addListener((o, a, b) -> {
            updateStageProgress();
            refreshSubmitState();
        });

        // ---- stage tiles
        VBox stageBox = new VBox(8);
        int i = 1;
        for (String st : STAGES) {
            StackPane dot = new StackPane(Ui.label(String.valueOf(i++), "step-dot-text"));
            dot.getStyleClass().add("step-dot");
            Label name = Ui.label(st, "h3");
            Label status = Ui.label("Not sent yet", "faint");
            VBox text = new VBox(1, name, status);
            HBox g = new HBox(12, dot, text);
            g.setAlignment(Pos.CENTER_LEFT);

            ToggleButton tile = new ToggleButton();
            tile.setGraphic(g);
            tile.getStyleClass().add("stage-tile");
            tile.setToggleGroup(stageGroup);
            tile.setMaxWidth(Double.MAX_VALUE);
            tile.setUserData(st);
            stageTiles.put(st, tile);
            stageDots.put(st, dot);
            stageStatus.put(st, status);
            stageBox.getChildren().add(tile);
        }
        stageGroup.selectToggle(stageTiles.get(STAGES[0]));
        keepOneSelected(stageGroup);
        stageGroup.selectedToggleProperty().addListener((o, a, b) -> {
            stageOverlay.setText("STAGE: " + selectedStage().toUpperCase());
            rebuildChecklist();
            refreshSubmitState();
        });
        rebuildChecklist();
        stageOverlay.setText("STAGE: " + STAGES[0].toUpperCase());

        // ---- submit
        readyHint = Ui.wrap("", "muted");
        submitBtn = Ui.button("Send for approval", "check", "btn-primary", "btn-large");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setOnAction(e -> handleSubmit());

        VBox card = Ui.card(14,
                Ui.label("New submission", "h2"),
                Ui.field("1.  MEAL", meals),
                Ui.field("2.  DISH NAME", dishBox),
                Ui.field("3.  PHOTO STAGE", stageBox),
                Ui.field("4.  QUALITY CHECKLIST", checklistBox),
                Ui.divider(),
                readyHint, submitBtn);
        card.setPrefWidth(390);
        card.setMinWidth(370);
        card.setMaxWidth(420);
        // little "caps" look for the numbered labels
        card.lookupAll(".field-label");
        return card;
    }

    private static void keepOneSelected(ToggleGroup g) {
        g.selectedToggleProperty().addListener((o, oldT, newT) -> {
            if (newT == null && oldT != null) oldT.setSelected(true);
        });
    }

    // ================================================================ camera

    /** Opens the camera. Called only when the worker presses "Take a photo" (or Retake). */
    private void openCamera() {
        switch (camMode()) {
            case WEBCAM:
                cameraOpen = true;
                gotFirstFrame = false;
                webcam.start(img -> {
                    if (!hasPhoto && cameraOpen) {
                        preview.setImage(img);
                        if (!gotFirstFrame) {
                            gotFirstFrame = true;
                            placeholder.setVisible(false);
                            setBadge("LIVE", "overlay-live", true);
                        }
                    }
                });
                break;
            case MAC:
                if (MacCamera.findTool() == null) {
                    AlertUtil.showInfo("One-time setup for the Mac camera",
                            "To use your Mac camera, open Terminal and run:\n\n"
                                    + "    brew install imagesnap\n\n"
                                    + "Then come back and press Take a photo again.\n"
                                    + "(Until then you can choose a photo from this computer.)");
                    return;
                }
                cameraOpen = true;
                macLiveFailed = false;
                preview.setImage(null);
                startMacLive();
                break;
            default:
                AlertUtil.showInfo("No camera found", "No camera was found on this computer. Please choose a photo from this computer.");
                return;
        }
        applyInputMode();
    }

    /** Switches the camera off and goes back to the idle "Take a photo" state. */
    private void closeCamera() {
        webcam.stop();
        stopMacLive();
        cameraOpen = false;
        gotFirstFrame = false;
        if (!hasPhoto) preview.setImage(null);
        applyInputMode();
    }

    private void onRetakeClicked() {
        if (hasPhoto) {                    // Retake: throw the photo away and open the camera again
            resetPhoto();
            openCamera();
        } else if (cameraOpen) {           // Close camera
            closeCamera();
        }
    }

    private void startClock() {
        clockLabel.setText(LocalDateTime.now().format(CLOCK_FMT));
        clock = new Timeline(new KeyFrame(Duration.seconds(1),
                e -> clockLabel.setText(LocalDateTime.now().format(CLOCK_FMT))));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private enum CamMode { WEBCAM, MAC, NONE }

    private CamMode camMode() {
        if (webcam.isWebcamAvailable()) return CamMode.WEBCAM;
        if (MacCamera.isMac()) return CamMode.MAC;
        return CamMode.NONE;
    }

    /** Idle (camera off) / camera open / photo taken: shows the right texts and buttons. */
    private void applyInputMode() {
        CamMode mode = camMode();
        boolean picker = Config.isDevMode() || mode != CamMode.WEBCAM;

        takeBtn.setVisible(mode != CamMode.NONE);
        takeBtn.setManaged(mode != CamMode.NONE);
        chooseBtn.setVisible(picker);
        chooseBtn.setManaged(picker);
        chooseBtn.getStyleClass().removeAll("btn-primary", "btn-secondary");
        chooseBtn.getStyleClass().add(mode == CamMode.NONE ? "btn-primary" : "btn-secondary");

        if (hasPhoto) return;

        if (cameraOpen) {
            boolean live = mode == CamMode.WEBCAM
                    ? gotFirstFrame
                    : (macLive != null && macLive.isRunning() && preview.getImage() != null);
            placeholder.setVisible(!live);
            if (mode == CamMode.MAC && macLiveFailed) {
                placeholderTitle.setText("Live view is not available");
                placeholderText.setText("Press Capture photo and the Mac camera will take a picture.");
            } else {
                placeholderTitle.setText("Opening the camera...");
                placeholderText.setText(mode == CamMode.MAC
                        ? "If macOS asks, allow camera access. The live view appears in a moment."
                        : "If nothing appears, check that no other app is using the camera.");
            }
            setBadge("LIVE", "overlay-live", live);
            setNote("Line up the food inside the frame, then press Capture photo.", false);
            takeBtn.setText("Capture photo");
            takeBtn.setDisable(false);
            retakeBtn.setText("Close camera");
            retakeBtn.setDisable(false);
            return;
        }

        // idle: camera is off
        placeholder.setVisible(true);
        setBadge("", "overlay-live", false);
        switch (mode) {
            case NONE:
                placeholderTitle.setText("No camera found");
                placeholderText.setText("Use \"Choose a photo from this computer\" to pick a JPG or PNG instead.");
                setNote("This computer has no camera, so photos are chosen from a file.", false);
                break;
            case MAC:
                if (MacCamera.findTool() == null) {
                    placeholderTitle.setText("One-time setup for the Mac camera");
                    placeholderText.setText("Open Terminal and run:  brew install imagesnap\nThen press Take a photo. Until then, choose a photo from this computer.");
                    setNote("Mac camera needs the free \"imagesnap\" tool (see Help).", false);
                    break;
                }
                // fall through
            default:
                placeholderTitle.setText("Camera is off");
                placeholderText.setText("Press Take a photo to open the camera.");
                setNote("The camera opens only when you press Take a photo.", false);
        }
        takeBtn.setText("Take a photo");
        takeBtn.setDisable(false);
        retakeBtn.setText("Retake");
        retakeBtn.setDisable(true);
    }

    private void setNote(String text, boolean warning) {
        cameraNote.setText(text);
        cameraNote.getStyleClass().removeAll("muted", "field-error");
        cameraNote.getStyleClass().add(warning ? "field-error" : "muted");
    }

    private void setBadge(String text, String variantClass, boolean visible) {
        liveBadge.getStyleClass().removeAll("overlay-live", "overlay-frozen", "overlay-file");
        liveBadge.getStyleClass().add(variantClass);
        liveBadge.setText(text);
        liveBadge.setVisible(visible);
    }

    /** Common tail of every way of getting a photo: remember it and lock the form state. */
    private void acceptPhoto(byte[] bytes, Image shown, String badge, String badgeClass, String note, boolean warn) {
        imageBytes = bytes;
        hasPhoto = true;
        cameraOpen = false;
        webcam.stop();
        stopMacLive();
        if (shown != null) {
            preview.setImage(shown);
            placeholder.setVisible(false);
        }
        takeBtn.setDisable(true);
        takeBtn.setText("Take a photo");
        retakeBtn.setText("Retake");
        retakeBtn.setDisable(false);
        setBadge(badge, badgeClass, true);
        setNote(note, warn);
        refreshSubmitState();
    }

    private void handleTake() {
        if (!cameraOpen) {                 // step 1: open the camera
            openCamera();
            return;
        }
        switch (camMode()) {               // step 2: capture what is on screen
            case WEBCAM:
                if (!gotFirstFrame) {
                    AlertUtil.showInfo("Camera is starting", "Wait a second until you can see the picture, then press Capture photo.");
                    return;
                }
                try {
                    byte[] bytes = webcam.captureFrameBytes();
                    if (bytes == null || bytes.length == 0) {
                        AlertUtil.showWarning("Could not take the photo",
                                "The camera did not return a picture yet. Wait a second and try again, or choose a photo from this computer.");
                        return;
                    }
                    acceptPhoto(bytes, null, "PHOTO TAKEN", "overlay-frozen",
                            "Check that the photo is clear. Press Retake if it is not, or send it for approval.", false);
                } catch (Exception e) {
                    AlertUtil.showError("Camera problem", "Could not take the photo: " + e.getMessage());
                }
                break;
            case MAC:
                if (macLive != null && macLive.isRunning() && macLive.latestFrame() != null) {
                    byte[] bytes = macLive.latestFrame();
                    stopMacLive();
                    acceptPhoto(bytes, new Image(new ByteArrayInputStream(bytes)),
                            "PHOTO TAKEN", "overlay-frozen",
                            "Check that the photo is clear. Press Retake if it is not, or send it for approval.", false);
                } else {
                    stopMacLive();
                    takeWithMacCamera();
                }
                break;
            default:
                AlertUtil.showInfo("No camera found", "No camera was found on this computer. Please choose a photo from this computer.");
        }
    }

    /** Built-in Mac camera through the free imagesnap tool; runs in the background so the window stays responsive. */
    private void takeWithMacCamera() {
        if (MacCamera.findTool() == null) {
            AlertUtil.showInfo("One-time setup for the Mac camera",
                    "To take photos with your Mac camera, open Terminal and run:\n\n"
                            + "    brew install imagesnap\n\n"
                            + "Then come back and press Take a photo again.\n"
                            + "(Until then you can choose a photo from this computer.)");
            return;
        }
        takeBtn.setDisable(true);
        takeBtn.setText("Taking photo...");
        Thread t = new Thread(() -> {
            try {
                byte[] bytes = MacCamera.capture();
                Platform.runLater(() -> acceptPhoto(bytes, new Image(new ByteArrayInputStream(bytes)),
                        "PHOTO TAKEN", "overlay-frozen",
                        "Taken just now with the Mac camera. Press Retake if it is not clear.", false));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    takeBtn.setDisable(false);
                    takeBtn.setText("Capture photo");
                    AlertUtil.showWarning("Could not take the photo", ex.getMessage());
                });
            }
        }, "MessLens-MacCamera");
        t.setDaemon(true);
        t.start();
    }

    private void handleChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a food photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Photos (JPG, PNG)", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(AppNavigator.getPrimaryStage());
        if (file == null) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError() || image.getWidth() < 1) {
                AlertUtil.showWarning("Not a photo", "That file could not be opened as a photo. Please choose a JPG or PNG.");
                return;
            }
            long ageMin = java.time.Duration.between(Instant.ofEpochMilli(file.lastModified()), Instant.now()).toMinutes();
            int limit = Config.getMaxPhotoAgeMinutes();
            if (limit > 0 && ageMin > limit) {
                AlertUtil.showWarning("Photo is too old",
                        "This photo file was saved " + describeAge(ageMin) + " ago. Photos older than "
                                + limit + " minutes are not accepted. Please take a fresh photo.");
                return;
            }
            boolean old = ageMin > 120;
            String note = file.getName() + "  -  file saved " + describeAge(ageMin) + " ago."
                    + (old ? "\nThis photo looks old. Make sure it shows the food being prepared now." : "");
            acceptPhoto(bytes, image, "FROM FILE", "overlay-file", note, old);
        } catch (IOException e) {
            AlertUtil.showError("Could not open the photo", e.getMessage());
        }
    }

    private static String describeAge(long minutes) {
        if (minutes < 2) return "just now";
        if (minutes < 120) return minutes + " minutes";
        if (minutes < 60 * 48) return (minutes / 60) + " hours";
        return (minutes / (60 * 24)) + " days";
    }

    /** Live view for the Mac camera (imagesnap time-lapse); safe to call repeatedly. */
    private void startMacLive() {
        if (hasPhoto || !cameraOpen || macLiveFailed || (macLive != null && macLive.isRunning())) return;
        if (MacCamera.findTool() == null) return;
        macLive = new MacCamera.Live();
        try {
            macLive.start(bytes -> {
                Image img = new Image(new ByteArrayInputStream(bytes));
                Platform.runLater(() -> {
                    if (hasPhoto || !cameraOpen) return;
                    preview.setImage(img);
                    placeholder.setVisible(false);
                    setBadge("LIVE", "overlay-live", true);
                });
            }, () -> Platform.runLater(() -> {
                macLiveFailed = true;
                stopMacLive();
                applyInputMode();
            }));
        } catch (IOException e) {
            macLiveFailed = true;
            macLive = null;
        }
    }

    private void stopMacLive() {
        if (macLive != null) { macLive.stop(); macLive = null; }
    }

    /** Forgets the current photo and returns to the idle state (camera off). */
    private void resetPhoto() {
        hasPhoto = false;
        imageBytes = null;
        cameraOpen = false;
        gotFirstFrame = false;
        webcam.stop();
        stopMacLive();
        preview.setImage(null);
        applyInputMode();
        refreshSubmitState();
    }

    private void handleRetake() {
        resetPhoto();
    }

    // ================================================================ form state

    private void selectMeal(String meal) {
        for (Object n : mealGroup.getToggles()) {
            ToggleButton t = (ToggleButton) n;
            if (t.getText().equals(meal)) {
                t.setSelected(true);
                return;
            }
        }
    }

    private String selectedMeal() {
        ToggleButton t = (ToggleButton) mealGroup.getSelectedToggle();
        return t == null ? MEALS[1] : t.getText();
    }

    private String selectedStage() {
        ToggleButton t = (ToggleButton) stageGroup.getSelectedToggle();
        return t == null ? STAGES[0] : (String) t.getUserData();
    }

    private String dishText() {
        String s = dishBox.getEditor().getText();
        return s == null ? "" : s.trim();
    }

    private static String mealForNow() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 11) return "Breakfast";
        if (hour < 16) return "Lunch";
        return "Dinner";
    }

    private void reloadUploads() {
        myUploads = uploads.getWorkerUploads(user);   // newest first
        Set<String> names = new LinkedHashSet<>();
        for (Upload u : myUploads) names.add(u.getDishName());
        for (String d : COMMON_DISHES) names.add(d);
        String keep = dishBox.getEditor().getText();
        dishBox.getItems().setAll(names);
        dishBox.getEditor().setText(keep == null ? "" : keep);
        updateStageProgress();
    }

    /** Colours the three stage tiles by what has already been sent today for the dish being typed. */
    private void updateStageProgress() {
        String dish = dishText();
        String meal = selectedMeal();
        int i = 1;
        for (String st : STAGES) {
            String status = null;
            int count = 0;
            if (!dish.isEmpty()) {
                for (Upload u : myUploads) {
                    if (st.equalsIgnoreCase(u.getStage()) && dish.equalsIgnoreCase(u.getDishName())
                            && meal.equals(u.getMeal()) && Fmt.isToday(u.getUploadedAt())) {
                        if (status == null) status = u.getStatus();   // newest photo decides the colour
                        count++;
                    }
                }
            }
            String more = count > 1 ? "  \u00b7  " + count + " photos" : "";
            StackPane dot = stageDots.get(st);
            Label text = stageStatus.get(st);
            dot.getChildren().clear();
            dot.getStyleClass().removeAll("step-dot-ok", "step-dot-warn", "step-dot-bad");
            text.getStyleClass().removeAll("faint", "field-ok", "field-error");

            if ("APPROVED".equalsIgnoreCase(status)) {
                dot.getStyleClass().add("step-dot-ok");
                dot.getChildren().add(Icons.of("check", 15, "icon-white"));
                text.setText("Approved" + more);
                text.getStyleClass().add("field-ok");
            } else if ("REJECTED".equalsIgnoreCase(status)) {
                dot.getStyleClass().add("step-dot-bad");
                dot.getChildren().add(Icons.of("x", 15, "icon-white"));
                text.setText("Rejected - please retake" + more);
                text.getStyleClass().add("field-error");
            } else if ("PENDING".equalsIgnoreCase(status)) {
                dot.getStyleClass().add("step-dot-warn");
                dot.getChildren().add(Icons.of("clock", 15, "icon-white"));
                text.setText("Sent - waiting for review" + more);
                text.getStyleClass().add("faint");
            } else {
                dot.getChildren().add(Ui.label(String.valueOf(i), "step-dot-text"));
                text.setText("Not sent yet");
                text.getStyleClass().add("faint");
            }
            i++;
        }
    }

    private void refreshSubmitState() {
        if (submitBtn == null) return;
        String dish = dishText();
        if (dish.isEmpty()) {
            readyHint.setText("Next: type or pick the dish name.");
            submitBtn.setDisable(true);
        } else if (!hasPhoto) {
            readyHint.setText("Next: take or choose the " + selectedStage().toLowerCase() + " photo.");
            submitBtn.setDisable(true);
        } else {
            readyHint.setText("Ready to send:  " + selectedMeal() + "  ·  " + dish + "  ·  " + selectedStage());
            submitBtn.setDisable(false);
        }
    }

    // ================================================================ submit

    private void handleSubmit() {
        String dish = dishText();
        String meal = selectedMeal();
        String stage = selectedStage();
        if (dish.isEmpty() || imageBytes == null || imageBytes.length == 0) {
            refreshSubmitState();
            return;
        }
        int hostelId = user.getHostelId() != null ? user.getHostelId() : 1;
        try {
            uploads.submitUpload(user, hostelId, meal, dish, stage, imageBytes, checklistAnswers());

            int idx = java.util.Arrays.asList(STAGES).indexOf(stage);
            String next;
            if (idx >= 0 && idx < STAGES.length - 1) {
                stageGroup.selectToggle(stageTiles.get(STAGES[idx + 1]));
                next = "Next: the " + STAGES[idx + 1].toLowerCase() + " photo.";
            } else {
                next = "All three stages of " + dish + " are sent.";
            }
            handleRetake();
            reloadUploads();
            rebuildChecklist();
            if (idx == STAGES.length - 1) {
                // dish is complete - get ready for the next dish
                dishBox.getEditor().clear();
                stageGroup.selectToggle(stageTiles.get(STAGES[0]));
            }
            refreshSubmitState();
            shell.refreshBadges();
            UiFx.toast(stage + " photo sent for approval. " + next
                    + " Need more photos of " + stage.toLowerCase() + "? Tap that stage again.", true);

        } catch (DuplicateEvidenceException e) {
            AlertUtil.showWarning("This photo was already sent",
                    "This exact picture has been uploaded before. Please take a new photo of this stage.");
        } catch (ValidationException e) {
            AlertUtil.showWarning("Please check the form", e.getMessage());
        } catch (Exception e) {
            AlertUtil.showError("Could not send the photo", e.getMessage());
        }
    }
}
