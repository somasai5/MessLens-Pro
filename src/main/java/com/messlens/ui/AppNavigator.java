package com.messlens.ui;

import com.messlens.model.User;
import com.messlens.security.SessionManager;
import com.messlens.service.AuthService;
import com.messlens.util.AlertUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Switches between the login screen and the role-specific application shell.
 * Each role gets its own set of sidebar pages; Settings and Help are shared.
 */
public class AppNavigator {

    private static Stage primaryStage;
    private static AppShell currentShell;
    private static Timeline sessionWatch;
    /** The one Scene of the app. Screens swap its root, so full screen is never interrupted (a new Scene exits macOS full screen). */
    private static Scene scene;

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("MessLens");
        primaryStage.setMinWidth(1040);
        primaryStage.setMinHeight(620);
        primaryStage.setWidth(fitWidth(1366));
        primaryStage.setHeight(fitHeight(820));
        primaryStage.centerOnScreen();
    }

    public static AppShell getShell() {
        return currentShell;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void showLogin() {
        showLogin(null);
    }

    /** @param notice optional message shown above the sign-in form (for example "session expired") */
    public static void showLogin(String notice) {
        stopSessionWatch();
        cleanup();
        AuthService.logout();
        LoginView loginView = new LoginView(notice);
        display(loginView.getView(), "MessLens  -  Sign in");
    }

    /**
     * Shows a screen in the main window. The first call creates the Scene; later calls only replace its root
     * so a window that is in full screen stays in full screen (sign out, session timeout, sign in).
     */
    private static void display(javafx.scene.Parent root, String title) {
        boolean wasFullScreen = primaryStage.isFullScreen();
        if (scene == null || primaryStage.getScene() != scene) {
            scene = new Scene(root, primaryStage.getWidth(), primaryStage.getHeight());
            Theme.apply(scene);
            scene.addEventFilter(javafx.event.Event.ANY, e -> SessionManager.touch());
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        if (title != null) primaryStage.setTitle(title);
        Motion.screenIn(root);
        primaryStage.show();
        if (wasFullScreen && !primaryStage.isFullScreen()) {
            javafx.application.Platform.runLater(() -> primaryStage.setFullScreen(true));
        }
    }

    public static void routeUser(User user) {
        cleanup();
        if (user == null || user.getRole() == null) {
            showLogin();
            return;
        }
        if (user.isMustChangePassword()) {
            startSessionWatch();
            display(new ForcePasswordView(user).getView(), "MessLens  -  Set a new password");
            return;
        }
        String role = user.getRole().toUpperCase();
        AppShell shell = new AppShell(user);
        String first;
        switch (role) {
            case "WORKER":
                shell.addMainPage(new WorkerCapturePage(user, shell));
                shell.addMainPage(new WorkerSubmissionsPage(user, shell));
                shell.addMainPage(new MenuPage(user));
                first = "capture";
                break;
            case "ADMIN":
                shell.addMainPage(new AdminOverviewPage(user, shell));
                shell.addMainPage(new StaffPage(user));
                shell.addMainPage(new AuditPage(user));
                first = "admin";
                break;
            case "WARDEN":
                shell.addMainPage(new WardenOversightPage(user));
                shell.addMainPage(new HeadRecordsPage(user));
                shell.addMainPage(new MenuPage(user));
                shell.addMainPage(new AuditPage(user));
                first = "oversight";
                break;
            case "HEAD":
                shell.addMainPage(new HeadOverviewPage(user, shell));
                shell.addMainPage(new HeadReviewPage(user, shell));
                shell.addMainPage(new HeadRecordsPage(user));
                shell.addMainPage(new MenuPage(user));
                shell.addMainPage(new RatingsPage(user));
                shell.addMainPage(new ComplaintsPage(user, shell));
                shell.addMainPage(new StaffPage(user));
                first = "overview";
                break;
            case "STUDENT":
                shell.addMainPage(new StudentTimelinePage(user));
                shell.addMainPage(new StudentHostelsPage(user));
                shell.addMainPage(new MenuPage(user));
                shell.addMainPage(new RatingsPage(user));
                shell.addMainPage(new ComplaintsPage(user, shell));
                first = "timeline";
                break;
            default:
                AlertUtil.showError("Unknown role", "This account has no screen assigned: " + role);
                showLogin();
                return;
        }
        shell.addFooterPage(new SettingsPage(user));
        shell.addFooterPage(new HelpPage(user));

        currentShell = shell;
        startSessionWatch();
        display(shell, null);     // the shell sets the window title itself
        shell.start(first);
    }

    /**
     * NIST SP 800-63B session management: any mouse or keyboard activity keeps the session alive; the check
     * runs every 10 seconds and signs the person out after the idle limit or the absolute limit.
     */
    private static void startSessionWatch() {
        stopSessionWatch();
        sessionWatch = new Timeline(new KeyFrame(Duration.seconds(10), e -> {
            String reason = SessionManager.expiredReason();
            if (reason != null) {
                AuthService.expireSession(reason);
                showLogin("idle".equals(reason)
                        ? "You were signed out after a period of inactivity."
                        : "Your session reached its time limit. Please sign in again.");
            }
        }));
        sessionWatch.setCycleCount(Timeline.INDEFINITE);
        sessionWatch.play();
    }

    private static void stopSessionWatch() {
        if (sessionWatch != null) {
            sessionWatch.stop();
            sessionWatch = null;
        }
    }

    private static double fitWidth(double wanted) {
        return Math.min(wanted, Screen.getPrimary().getVisualBounds().getWidth() * 0.96);
    }

    private static double fitHeight(double wanted) {
        return Math.min(wanted, Screen.getPrimary().getVisualBounds().getHeight() - 40);
    }

    private static void cleanup() {
        if (currentShell != null) {
            currentShell.dispose();
            currentShell = null;
        }
    }
}
