package com.messlens.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Non-blocking toast message shown at the bottom of the window. */
public final class UiFx {

    private UiFx() {}

    public static void toast(String message, boolean success) {
        Platform.runLater(() -> {
            Stage owner = AppNavigator.getPrimaryStage();
            if (owner == null || !owner.isShowing()) return;

            Label text = new Label(message);
            text.setWrapText(true);
            text.setMaxWidth(420);
            text.getStyleClass().add("toast-text");
            Label icon = new Label();
            icon.setGraphic(Icons.of(success ? "check-circle" : "alert", 20, "icon-white"));

            HBox box = new HBox(10, icon, text);
            box.setAlignment(Pos.CENTER_LEFT);
            box.getStyleClass().addAll("toast", success ? "toast-ok" : "toast-warn");

            Popup popup = new Popup();
            popup.getContent().add(box);
            popup.show(owner, owner.getX() + owner.getWidth() / 2 - 200, owner.getY() + owner.getHeight() - 120);
            // Popup scenes do not inherit the main stylesheet: attach it so the icon gets its colour.
            if (popup.getScene() != null) Theme.apply(popup.getScene());

            PauseTransition wait = new PauseTransition(Duration.millis(2800));
            wait.setOnFinished(e -> {
                FadeTransition out = new FadeTransition(Duration.millis(400), box);
                out.setToValue(0);
                out.setOnFinished(ev -> popup.hide());
                out.play();
            });
            wait.play();
        });
    }
}
