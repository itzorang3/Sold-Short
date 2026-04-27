package com.soldshort.ui;

import com.soldshort.api.ApiClient;
import com.soldshort.models.League;
import com.soldshort.models.User;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Entry point for the Sold Short JavaFX application.
 *
 * All game-state now lives on the Spring Boot server.  This client connects
 * to the server URL configured in SERVER_URL below and communicates via the
 * REST API through {@link ApiClient}.
 *
 * Uses a persistent StackPane scene root with two layers:
 *   1. contentPane  — the current full-screen view (swapped on navigation)
 *   2. overlayLayer — in-window modal cards (shown over content, hidden by default)
 */
public class MainApp extends Application {

    // ── Server URL ────────────────────────────────────────────────────────────

    /**
     * Change this to your Railway URL once deployed.
     * For local testing: "http://localhost:8080"
     * Reads SERVER_URL env var first, falls back to localhost.
     */
    private static final String SERVER_URL =
            System.getenv().getOrDefault("SERVER_URL", "http://localhost:8080");

    // ── Application-level singletons ─────────────────────────────────────────

    private static Stage  primaryStage;
    private static User   currentUser;
    private static League activeLeague;

    // ── Overlay infrastructure ────────────────────────────────────────────────

    private static StackPane rootStack;
    private static StackPane contentPane;
    private static StackPane overlayLayer;

    // ── JavaFX lifecycle ─────────────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // Configure the HTTP client — all screens use ApiClient.get()
        ApiClient.configure(SERVER_URL);

        // Build persistent scene structure
        contentPane  = new StackPane();
        overlayLayer = new StackPane();
        overlayLayer.setVisible(false);
        overlayLayer.setMouseTransparent(true);

        rootStack = new StackPane(contentPane, overlayLayer);

        Scene scene = new Scene(rootStack, 900, 640);
        applyStylesheet(scene);

        stage.setTitle("Sold Short — Fantasy Stock Trading");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> Platform.exit());

        showLoginScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ── Screen Navigation ────────────────────────────────────────────────────

    public static void showLoginScreen() {
        setContent(new LoginScreen().build(), 900, 640);
    }

    public static void showMainMenu() {
        setContent(new MainMenuScreen(currentUser).build(), 900, 640);
    }

    public static void showDraftScreen(League league) {
        activeLeague = league;
        setContent(new DraftScreen(currentUser, league).build(), 1060, 720);
    }

    public static void showHostScreen(League league) {
        activeLeague = league;
        setContent(new HostControlScreen(currentUser, league).build(), 1060, 740);
    }

    public static void showLeaderboard(League league) {
        activeLeague = league;
        setContent(new LeaderboardScreen(league).build(), 900, 640);
    }

    // ── Overlay API ───────────────────────────────────────────────────────────

    public static void showOverlay(Node content) {
        overlayLayer.getChildren().setAll(content);
        overlayLayer.setStyle("-fx-background-color: rgba(0,0,0,0.62);");
        StackPane.setAlignment(content, Pos.CENTER);
        overlayLayer.setMouseTransparent(false);
        overlayLayer.setVisible(true);
    }

    public static void hideOverlay() {
        overlayLayer.setVisible(false);
        overlayLayer.setMouseTransparent(true);
        overlayLayer.getChildren().clear();
    }

    public static void showAlertOverlay(String title, String message, Runnable onClose) {
        VBox card = buildCard(title, message);
        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("primary-btn");
        okBtn.setOnAction(e -> { hideOverlay(); if (onClose != null) onClose.run(); });
        card.getChildren().add(okBtn);
        showOverlay(card);
    }

    public static void showAlertOverlay(String title, String message) {
        showAlertOverlay(title, message, null);
    }

    public static void showConfirmOverlay(String title, String message, Runnable onYes) {
        VBox card = buildCard(title, message);
        Button yesBtn = new Button("Yes");
        yesBtn.getStyleClass().add("primary-btn");
        yesBtn.setOnAction(e -> { hideOverlay(); if (onYes != null) onYes.run(); });
        Button noBtn = new Button("No");
        noBtn.getStyleClass().add("secondary-btn");
        noBtn.setOnAction(e -> hideOverlay());
        HBox btns = new HBox(12, yesBtn, noBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(btns);
        showOverlay(card);
    }

    public static void showFormOverlay(String title, Node formContent) {
        VBox card = new VBox(18);
        card.getStyleClass().add("overlay-card");
        card.setMaxWidth(500);
        if (title != null && !title.isBlank()) {
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("section-label");
            card.getChildren().add(titleLabel);
            card.getChildren().add(new Separator());
        }
        card.getChildren().add(formContent);
        showOverlay(card);
    }

    // ── Shared State Accessors ────────────────────────────────────────────────

    public static User   getCurrentUser()  { return currentUser; }
    public static void   setCurrentUser(User u) { currentUser = u; }
    public static League getActiveLeague() { return activeLeague; }
    public static Stage  getPrimaryStage() { return primaryStage; }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private static void setContent(Pane pane, double width, double height) {
        hideOverlay();
        contentPane.getChildren().setAll(pane);
        if (!primaryStage.isFullScreen() && !primaryStage.isMaximized()) {
            primaryStage.setWidth(width);
            primaryStage.setHeight(height);
        }
    }

    private static VBox buildCard(String title, String message) {
        VBox card = new VBox(14);
        card.getStyleClass().add("overlay-card");
        card.setMaxWidth(440);
        if (title != null && !title.isBlank()) {
            Label t = new Label(title);
            t.getStyleClass().add("section-label");
            card.getChildren().add(t);
        }
        if (message != null && !message.isBlank()) {
            Label m = new Label(message);
            m.getStyleClass().add("subtitle-label");
            m.setWrapText(true);
            m.setMaxWidth(400);
            card.getChildren().add(m);
        }
        return card;
    }

    private static void applyStylesheet(Scene scene) {
        var url = MainApp.class.getResource("/com/soldshort/ui/styles.css");
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    }
}
