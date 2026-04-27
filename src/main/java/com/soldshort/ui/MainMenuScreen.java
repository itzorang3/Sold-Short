package com.soldshort.ui;

import com.soldshort.api.ApiClient;
import com.soldshort.models.League;
import com.soldshort.models.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Main Menu — shown after successful login.
 *
 * League creation, joining, and navigation are all delegated to the REST
 * server via {@link ApiClient}.  HTTP calls run on background threads.
 */
public class MainMenuScreen {

    private final User currentUser;

    public MainMenuScreen(User currentUser) {
        this.currentUser = currentUser;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public Pane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        // Top bar
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(16, 24, 16, 24));
        topBar.getStyleClass().add("top-bar");

        Label appTitle = new Label("SOLD SHORT");
        appTitle.getStyleClass().add("nav-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label("Logged in as: " + currentUser.getUsername());
        userLabel.getStyleClass().add("hint-label");

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary-btn");
        logoutBtn.setOnAction(e -> { MainApp.setCurrentUser(null); MainApp.showLoginScreen(); });

        topBar.getChildren().addAll(appTitle, spacer, userLabel,
                new Separator(javafx.geometry.Orientation.VERTICAL), logoutBtn);
        root.setTop(topBar);

        // Content
        HBox content = new HBox(24);
        content.setPadding(new Insets(32));
        content.setAlignment(Pos.TOP_CENTER);
        root.setCenter(content);

        // Left: action panel
        VBox actionPanel = new VBox(16);
        actionPanel.setMinWidth(260);
        actionPanel.getStyleClass().add("card");
        actionPanel.setPadding(new Insets(24));

        Label actionsTitle = new Label("Quick Actions");
        actionsTitle.getStyleClass().add("section-label");

        Button createBtn = new Button("+ Create New League");
        createBtn.getStyleClass().add("primary-btn");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> handleCreateLeague());

        Button joinBtn = new Button("Join League");
        joinBtn.getStyleClass().add("primary-btn");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setOnAction(e -> handleJoinLeague());

        Label hint = new Label("Share your league code with friends after creating a league.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        Label howTitle = new Label("How to Play");
        howTitle.getStyleClass().add("section-label");
        howTitle.setPadding(new Insets(8, 0, 2, 0));

        Label howText = new Label(
                "Each round, every player picks a stock they think will LOSE value.\n\n"
                + "The player whose stock performs BEST (rises the most) is the worst "
                + "short-seller and loses a life.\n\n"
                + "Survive all rounds — last one standing wins!");
        howText.getStyleClass().add("hint-label");
        howText.setWrapText(true);

        actionPanel.getChildren().addAll(actionsTitle, createBtn, joinBtn,
                new Separator(), hint, new Separator(), howTitle, howText);

        // Right: league list
        VBox leaguePanel = new VBox(12);
        leaguePanel.setPrefWidth(560);
        leaguePanel.getStyleClass().add("card");
        leaguePanel.setPadding(new Insets(24));

        Label leaguesTitle = new Label("My Leagues");
        leaguesTitle.getStyleClass().add("section-label");

        VBox leagueList = buildLeagueList();
        ScrollPane scroll = new ScrollPane(leagueList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        leaguePanel.getChildren().addAll(leaguesTitle, scroll);
        VBox.setVgrow(leaguePanel, Priority.ALWAYS);

        content.getChildren().addAll(actionPanel, leaguePanel);
        HBox.setHgrow(leaguePanel, Priority.ALWAYS);

        return root;
    }

    // ── League List ───────────────────────────────────────────────────────────

    private VBox buildLeagueList() {
        VBox list = new VBox(10);
        List<League> leagues = ApiClient.get().getLeaguesForUser(currentUser.getId());

        if (leagues.isEmpty()) {
            Label empty = new Label("You haven't joined any leagues yet.\nCreate one or enter a join code to get started.");
            empty.getStyleClass().add("hint-label");
            empty.setWrapText(true);
            empty.setTextAlignment(TextAlignment.CENTER);
            list.getChildren().add(empty);
            return list;
        }

        for (League league : leagues) {
            list.getChildren().add(buildLeagueCard(league));
        }
        return list;
    }

    private HBox buildLeagueCard(League league) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.getStyleClass().add("league-card");

        VBox info = new VBox(4);
        Label name = new Label(league.getName());
        name.getStyleClass().add("league-name-label");

        int memberCount = (league.getStatus() == League.Status.FORMING)
                ? ApiClient.get().getLeagueMembers(league.getId()).size() : 0;

        String statusText = switch (league.getStatus()) {
            case FORMING  -> memberCount + " / " + league.getMaxPlayers() + " players joined";
            case DRAFTING -> "Round " + league.getCurrentRound() + " — Drafting";
            case ACTIVE   -> "Round " + league.getCurrentRound() + " — Active";
            case FINISHED -> "Tournament Complete";
        };
        Label status = new Label(statusText);
        status.getStyleClass().add("hint-label");

        Label livesHint = new Label("Starting lives: " + league.getStartingLives());
        livesHint.getStyleClass().add("hint-label");

        info.getChildren().addAll(name, status, livesHint);

        if (league.getStatus() == League.Status.FORMING) {
            HBox codeRow = new HBox(8);
            codeRow.setAlignment(Pos.CENTER_LEFT);

            Label codeLabel = new Label("Code: " + league.getJoinCode());
            codeLabel.getStyleClass().add("ticker-label");

            Button copyBtn = new Button("📋 Copy");
            copyBtn.getStyleClass().add("secondary-btn");
            copyBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8 3 8;");
            copyBtn.setTooltip(new Tooltip("Copy join code to clipboard"));
            copyBtn.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(league.getJoinCode());
                Clipboard.getSystemClipboard().setContent(cc);
                copyBtn.setText("✓ Copied!");
                javafx.animation.PauseTransition pt =
                        new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                pt.setOnFinished(ev -> copyBtn.setText("📋 Copy"));
                pt.play();
            });

            codeRow.getChildren().addAll(codeLabel, copyBtn);
            info.getChildren().add(codeRow);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button enterBtn = buildEnterButton(league);

        HBox buttons = new HBox(8, enterBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        if (league.isHostedBy(currentUser.getId())) {
            Button removeBtn = new Button("✕");
            removeBtn.getStyleClass().add("remove-btn");
            removeBtn.setTooltip(new Tooltip("Remove this league"));
            removeBtn.setOnAction(e ->
                MainApp.showConfirmOverlay(
                        "Remove League",
                        "Remove \"" + league.getName() + "\"? This cannot be undone.",
                        () -> {
                            new Thread(() -> {
                                ApiClient.get().removeLeague(league.getId(), currentUser.getId());
                                Platform.runLater(MainApp::showMainMenu);
                            }).start();
                        }));
            buttons.getChildren().add(removeBtn);
        } else if (league.getStatus() != League.Status.FINISHED) {
            Button leaveBtn = new Button("Leave");
            leaveBtn.getStyleClass().add("remove-btn");
            leaveBtn.setTooltip(new Tooltip("Leave this league"));
            String warning = (league.getStatus() == League.Status.FORMING)
                    ? "Leave \"" + league.getName() + "\"?"
                    : "Leave \"" + league.getName() + "\"?  "
                      + "You will be eliminated mid-tournament and cannot rejoin.";
            leaveBtn.setOnAction(e ->
                MainApp.showConfirmOverlay("Leave League", warning, () -> {
                    new Thread(() -> {
                        ApiClient.get().leaveLeague(league.getId(), currentUser.getId());
                        Platform.runLater(MainApp::showMainMenu);
                    }).start();
                }));
            buttons.getChildren().add(leaveBtn);
        }

        card.getChildren().addAll(info, spacer, buttons);
        return card;
    }

    private Button buildEnterButton(League league) {
        boolean isHost = league.isHostedBy(currentUser.getId());

        String label = switch (league.getStatus()) {
            case FORMING  -> isHost ? "Start Draft" : "View Lobby";
            case DRAFTING -> "Pick Stock";
            case ACTIVE   -> isHost ? "Enter Prices" : "View Leaderboard";
            case FINISHED -> "View Results";
        };

        Button btn = new Button(label);
        btn.getStyleClass().add("secondary-btn");

        btn.setOnAction(e -> {
            btn.setDisable(true);
            new Thread(() -> {
                League fresh = ApiClient.get().getLeagueById(league.getId());
                Platform.runLater(() -> {
                    btn.setDisable(false);
                    if (fresh == null) return;

                    switch (fresh.getStatus()) {
                        case FORMING -> {
                            if (isHost) {
                                // Start the draft on the server
                                new Thread(() -> {
                                    League started = ApiClient.get().startDraft(
                                            fresh.getId(), currentUser.getId());
                                    Platform.runLater(() -> {
                                        if (started != null) {
                                            MainApp.showDraftScreen(started);
                                        } else {
                                            MainApp.showAlertOverlay("Cannot Start",
                                                    "Need at least 2 players to start the draft.");
                                        }
                                    });
                                }).start();
                            } else {
                                MainApp.showAlertOverlay("Waiting…",
                                        "Waiting for the host to start the draft.\n\nJoin Code: "
                                        + fresh.getJoinCode());
                            }
                        }
                        case DRAFTING -> MainApp.showDraftScreen(fresh);
                        case ACTIVE   -> {
                            if (isHost) MainApp.showHostScreen(fresh);
                            else        MainApp.showLeaderboard(fresh);
                        }
                        case FINISHED -> MainApp.showLeaderboard(fresh);
                    }
                });
            }).start();
        });

        return btn;
    }

    // ── Overlay Handlers ──────────────────────────────────────────────────────

    private void handleCreateLeague() {
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. The Big Shorts");
        nameField.getStyleClass().add("text-field");

        Spinner<Integer> maxPlayersSpinner = new Spinner<>(2, 16, 8);
        maxPlayersSpinner.setEditable(true);
        maxPlayersSpinner.setMaxWidth(100);
        addSpinnerValidation(maxPlayersSpinner, 2, 16, 8);

        Label maxPlayersHint = new Label("Allowed: 2 – 16 players");
        maxPlayersHint.getStyleClass().add("hint-label");

        Spinner<Integer> livesSpinner = new Spinner<>(1, 5, 3);
        livesSpinner.setEditable(true);
        livesSpinner.setMaxWidth(100);
        addSpinnerValidation(livesSpinner, 1, 5, 3);

        Label livesRangeHint = new Label("Allowed: 1 – 5 lives");
        livesRangeHint.getStyleClass().add("hint-label");

        Label livesHint = new Label(
                "Players lose one life for the worst performance each round.\n"
                + "Elimination occurs when lives reach zero.");
        livesHint.getStyleClass().add("hint-label");
        livesHint.setWrapText(true);

        Label errorLabel = new Label("");
        errorLabel.getStyleClass().addAll("status-label", "error");
        errorLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        grid.setPadding(new Insets(4, 0, 8, 0));
        grid.add(makeFormLabel("League Name:"),   0, 0);
        grid.add(nameField,                        1, 0);
        grid.add(makeFormLabel("Max Players:"),    0, 1);
        grid.add(maxPlayersSpinner,                1, 1);
        grid.add(maxPlayersHint,                   1, 2);
        grid.add(makeFormLabel("Starting Lives:"), 0, 3);
        grid.add(livesSpinner,                     1, 3);
        grid.add(livesRangeHint,                   1, 4);
        grid.add(livesHint,                        0, 5);
        GridPane.setColumnSpan(livesHint, 2);

        Button createBtn = new Button("Create League");
        createBtn.getStyleClass().add("primary-btn");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> MainApp.hideOverlay());

        HBox btnRow = new HBox(12, createBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        createBtn.setOnAction(e -> {
            String name  = nameField.getText().trim();
            int    max   = maxPlayersSpinner.getValue();
            int    lives = livesSpinner.getValue();

            if (name.isEmpty()) {
                errorLabel.setText("League name cannot be empty.");
                return;
            }

            createBtn.setDisable(true);
            errorLabel.setText("");
            new Thread(() -> {
                League league = ApiClient.get().createLeague(
                        currentUser.getId(), name, max, lives);
                Platform.runLater(() -> {
                    createBtn.setDisable(false);
                    if (league == null) {
                        errorLabel.setText("Failed to create league. Please try again.");
                    } else {
                        MainApp.hideOverlay();
                        MainApp.showAlertOverlay(
                                "League Created!",
                                "Share this join code with your friends:\n\n"
                                + league.getJoinCode()
                                + "\n\nEach player starts with " + lives + " "
                                + (lives == 1 ? "life" : "lives") + ".",
                                MainApp::showMainMenu);
                    }
                });
            }).start();
        });

        MainApp.showFormOverlay("Create New League",
                new VBox(10, grid, errorLabel, btnRow));
    }

    private void handleJoinLeague() {
        TextField codeField = new TextField();
        codeField.setPromptText("6-character code (e.g. ABC123)");
        codeField.getStyleClass().add("text-field");

        Label errorLabel = new Label("");
        errorLabel.getStyleClass().addAll("status-label", "error");
        errorLabel.setWrapText(true);

        Button joinBtn = new Button("Join League");
        joinBtn.getStyleClass().add("primary-btn");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> MainApp.hideOverlay());

        HBox btnRow = new HBox(12, joinBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        joinBtn.setOnAction(e -> {
            String code = codeField.getText().trim();
            if (code.isEmpty()) {
                errorLabel.setText("Please enter a join code.");
                return;
            }

            joinBtn.setDisable(true);
            errorLabel.setText("");
            new Thread(() -> {
                League league = ApiClient.get().joinLeague(code, currentUser.getId());
                Platform.runLater(() -> {
                    joinBtn.setDisable(false);
                    if (league == null) {
                        errorLabel.setText("Could not join league. Check the code and try again.");
                    } else {
                        MainApp.hideOverlay();
                        MainApp.showAlertOverlay(
                                "Joined!",
                                "Successfully joined '" + league.getName() + "'.",
                                MainApp::showMainMenu);
                    }
                });
            }).start();
        });

        codeField.setOnAction(e -> joinBtn.fire());

        MainApp.showFormOverlay("Join League", new VBox(12,
                makeFormLabel("Enter the 6-character join code:"),
                codeField, errorLabel, btnRow));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Label makeFormLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint-label");
        return l;
    }

    private void addSpinnerValidation(Spinner<Integer> spinner, int min, int max, int def) {
        spinner.getEditor().focusedProperty().addListener((obs, was, isFocused) -> {
            if (!isFocused) {
                try {
                    int v = Integer.parseInt(spinner.getEditor().getText().trim());
                    spinner.getValueFactory().setValue(Math.max(min, Math.min(max, v)));
                } catch (NumberFormatException ex) {
                    spinner.getValueFactory().setValue(def);
                }
            }
        });
    }
}
