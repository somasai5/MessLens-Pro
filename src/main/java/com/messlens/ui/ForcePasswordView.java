package com.messlens.ui;

import com.messlens.exception.MessLensException;
import com.messlens.model.User;
import com.messlens.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shown right after sign-in when the account still uses a temporary password. */
public class ForcePasswordView {

    private final User user;
    private final PasswordField current = new PasswordField();
    private final PasswordField next = new PasswordField();
    private final PasswordField again = new PasswordField();
    private final Label error = Ui.wrap("", "field-error");

    public ForcePasswordView(User user) {
        this.user = user;
    }

    public Parent getView() {
        current.setPromptText("The temporary password you were given");
        next.setPromptText("At least 6 characters");
        again.setPromptText("Type the new password again");
        error.setVisible(false);
        error.setManaged(false);

        Button save = Ui.button("Save and continue", "arrow-right", "btn-primary", "btn-large");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setDefaultButton(true);
        save.setOnAction(e -> save());
        Button cancel = Ui.button("Sign out", "log-out", "btn-ghost");
        cancel.setOnAction(e -> AppNavigator.showLogin());

        StackPane mark = new StackPane(Icons.of("key", 26, "icon-white"));
        mark.getStyleClass().add("brand-mark");

        VBox card = new VBox(14, mark,
                Ui.label("Choose your own password", "h1"),
                Ui.wrap("Hi " + user.displayName() + ", your account was created with a temporary password. "
                        + "Set a new one to continue.", "muted"),
                Ui.field("TEMPORARY PASSWORD", Ui.passwordWithEye(current)),
                Ui.field("NEW PASSWORD", Ui.passwordWithEye(next)),
                Ui.field("CONFIRM NEW PASSWORD", Ui.passwordWithEye(again)),
                error, save, cancel);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(440);
        card.setMinWidth(380);

        StackPane root = new StackPane(card);
        root.getStyleClass().add("login-side");
        root.setPadding(new Insets(40));
        StackPane.setAlignment(card, Pos.CENTER);
        return root;
    }

    private void save() {
        error.setVisible(false);
        error.setManaged(false);
        try {
            if (!next.getText().equals(again.getText())) {
                throw new com.messlens.exception.ValidationException("The two new passwords do not match.");
            }
            new AuthService().changePassword(current.getText(), next.getText());
            AppNavigator.routeUser(AuthService.getCurrentUser());
        } catch (MessLensException ex) {
            error.setText(ex.getMessage());
            error.setVisible(true);
            error.setManaged(true);
        }
    }
}
