package com.messlens;

import com.messlens.dao.DatabaseManager;
import com.messlens.ui.AppNavigator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Main application entry point for MessLens.
 * Initializes the database schema (MySQL), seeds default accounts and hostels,
 * and launches the JavaFX desktop UI.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Initialize the database and seed initial data
        DatabaseManager.initializeDatabase();

        // Setup primary stage and show login view
        AppNavigator.setPrimaryStage(primaryStage);
        AppNavigator.showLogin();

        // Clean exit on window close
        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}

