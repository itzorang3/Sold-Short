package com.soldshort.ui;

import com.soldshort.api.ApiClient;
import com.soldshort.api.ApiClient.StockInfo;
import com.soldshort.models.DraftPick;
import com.soldshort.models.League;
import com.soldshort.models.User;
import com.soldshort.observer.LeaderboardEntry;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.List;

/**
 * Draft Picking screen — shown to all players during the DRAFTING phase.
 *
 * All draft state is fetched from the server via {@link ApiClient} so every
 * device sees the same data.  A background polling thread checks for changes
 * every 4 seconds and updates the UI on the JavaFX thread.
 */
public class DraftScreen {

    private final User   currentUser;
    private final League league;

    public DraftScreen(User currentUser, League league) {
        this.currentUser = currentUser;
        this.league      = league;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public Pane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildTopBar());

        // Fetch stock data once for the session (company names + prices)
        List<StockInfo> stocks = ApiClient.get().getAvailableTickers(
                league.getId(), league.getCurrentRound());

        VBox[] statusRef = { buildStatusPanel(stocks) };
        root.setLeft(statusRef[0]);
        root.setCenter(buildPickingPanel(stocks));

        boolean isHost = league.isHostedBy(currentUser.getId());

        Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(4), ev -> {
            new Thread(() -> {
                League fresh = ApiClient.get().getLeagueById(league.getId());
                if (fresh == null) return;

                List<StockInfo> freshStocks = ApiClient.get().getAvailableTickers(
                        fresh.getId(), fresh.getCurrentRound());
                boolean allDone = ApiClient.get().allPicksSubmitted(
                        fresh.getId(), fresh.getCurrentRound());

                Platform.runLater(() -> {
                    root.setLeft(buildStatusPanel(freshStocks));

                    if (allDone) {
                        root.setCenter(buildPickingPanel(freshStocks));

                        if (isHost) {
                            ((Timeline) ev.getSource()).stop();
                        } else {
                            boolean evaluated = ApiClient.get().isRoundEvaluated(
                                    fresh.getId(), fresh.getCurrentRound());
                            if (fresh.getStatus() == League.Status.FINISHED || evaluated) {
                                ((Timeline) ev.getSource()).stop();
                                MainApp.showLeaderboard(fresh);
                            }
                        }
                    }
                });
            }).start();
        }));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);

        boolean picksPending = !ApiClient.get().allPicksSubmitted(
                league.getId(), league.getCurrentRound());
        if (picksPending || !isHost) autoRefresh.play();

        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) autoRefresh.stop();
        });

        return root;
    }

    // ── Top Bar ───────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.getStyleClass().add("top-bar");

        Label title = new Label("SOLD SHORT  —  " + league.getName()
                + "  |  Round " + league.getCurrentRound() + " Draft");
        title.getStyleClass().add("nav-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> {
            new Thread(() -> {
                League fresh = ApiClient.get().getLeagueById(league.getId());
                Platform.runLater(() -> {
                    if (fresh != null) MainApp.showDraftScreen(fresh);
                });
            }).start();
        });

        Button backBtn = new Button("← Menu");
        backBtn.getStyleClass().add("secondary-btn");
        backBtn.setOnAction(e -> MainApp.showMainMenu());

        bar.getChildren().addAll(title, spacer, refreshBtn, backBtn);
        return bar;
    }

    // ── Status Panel ──────────────────────────────────────────────────────────

    private VBox buildStatusPanel(List<StockInfo> stocks) {
        VBox panel = new VBox(12);
        panel.setPrefWidth(230);
        panel.setPadding(new Insets(24, 16, 24, 24));
        panel.getStyleClass().add("side-panel");

        Label title = new Label("Draft Board");
        title.getStyleClass().add("section-label");
        panel.getChildren().add(title);

        int round = league.getCurrentRound();
        List<User>      activeMembers = ApiClient.get().getActiveMembers(league.getId());
        List<DraftPick> roundPicks    = ApiClient.get().getPicksForRound(league.getId(), round);

        int totalPicked = roundPicks.size();
        int totalActive = activeMembers.size();

        for (User member : activeMembers) {
            DraftPick pick = roundPicks.stream()
                    .filter(p -> p.getUserId() == member.getId())
                    .findFirst().orElse(null);

            boolean hasPicked = (pick != null);
            boolean isMe      = member.getId() == currentUser.getId();

            VBox card = new VBox(3);
            card.setPadding(new Insets(8, 10, 8, 10));
            card.getStyleClass().add(hasPicked ? "draft-card-done" : "draft-card-waiting");

            HBox nameRow = new HBox(6);
            nameRow.setAlignment(Pos.CENTER_LEFT);

            Label indicator = new Label(hasPicked ? "✓" : "○");
            indicator.getStyleClass().add(hasPicked ? "indicator-done" : "indicator-wait");

            Label nameLabel = new Label(member.getUsername() + (isMe ? " (you)" : ""));
            nameLabel.getStyleClass().add(hasPicked ? "turn-name" : "hint-label");
            nameLabel.setStyle(isMe ? "-fx-font-weight: bold;" : "");

            nameRow.getChildren().addAll(indicator, nameLabel);

            if (hasPicked) {
                String ticker  = pick.getTicker();
                String company = stocks.stream()
                        .filter(s -> s.ticker.equals(ticker))
                        .map(s -> s.companyName).findFirst().orElse(ticker);

                Label tickerLabel  = new Label(ticker);
                tickerLabel.getStyleClass().add("ticker-label");

                Label companyLabel = new Label(company);
                companyLabel.getStyleClass().add("hint-label");
                companyLabel.setWrapText(true);

                card.getChildren().addAll(nameRow, tickerLabel, companyLabel);
            } else {
                Label waiting = new Label("choosing...");
                waiting.getStyleClass().add("hint-label");
                waiting.setStyle("-fx-font-style: italic;");
                card.getChildren().addAll(nameRow, waiting);
            }

            panel.getChildren().add(card);
        }

        Separator sep = new Separator();
        sep.setPadding(new Insets(4, 0, 0, 0));
        Label progress = new Label(totalPicked + " of " + totalActive + " picks submitted");
        progress.getStyleClass().add("hint-label");
        panel.getChildren().addAll(sep, progress);

        return panel;
    }

    // ── Picking Panel ─────────────────────────────────────────────────────────

    private VBox buildPickingPanel(List<StockInfo> stocks) {
        VBox panel = new VBox(16);
        panel.setPadding(new Insets(24));

        int     round        = league.getCurrentRound();

        // Check elimination first — eliminated players are spectators only
        List<User> activeMembers = ApiClient.get().getActiveMembers(league.getId());
        boolean isEliminated = activeMembers.stream()
                .noneMatch(u -> u.getId() == currentUser.getId());
        if (isEliminated) {
            Label title = new Label("Round " + round + "  —  You Have Been Eliminated");
            title.getStyleClass().add("section-label");
            Label msg = new Label(
                    "You were eliminated and cannot pick this round. "
                    + "Watch the remaining players compete!");
            msg.getStyleClass().add("hint-label");
            msg.setWrapText(true);
            panel.getChildren().addAll(title, msg);
            return panel;
        }

        boolean alreadyPicked = ApiClient.get().getPickForUser(
                league.getId(), currentUser.getId(), round) != null;
        boolean allDone      = ApiClient.get().allPicksSubmitted(league.getId(), round);

        Label title = new Label("Round " + round + "  —  Pick a Stock to Short");
        title.getStyleClass().add("section-label");

        Label gameHint = new Label(
                "📉  Remember: you WANT your stock to FALL.  "
                + "The player whose stock rises the most (worst short) loses a life.");
        gameHint.getStyleClass().add("hint-label");
        gameHint.setWrapText(true);

        panel.getChildren().addAll(title, gameHint);

        if (allDone) {
            panel.getChildren().add(buildAllDoneSection());
            return panel;
        }

        if (alreadyPicked) {
            DraftPick myPick = ApiClient.get().getPickForUser(
                    league.getId(), currentUser.getId(), round);
            Label confirmed = new Label("Your pick: "
                    + (myPick != null ? myPick.getTicker() : "—")
                    + " — waiting for other players to pick...");
            confirmed.getStyleClass().addAll("status-label", "success");
            confirmed.setWrapText(true);
            panel.getChildren().add(confirmed);

            Label hint2 = new Label("Refresh the screen to check status.");
            hint2.getStyleClass().add("hint-label");
            panel.getChildren().add(hint2);
            return panel;
        }

        if (round > 1) buildLastRoundRecap(round).ifPresent(panel.getChildren()::add);

        Label instruction = new Label(
                "Select the stock you predict will fall the most this round. "
                + "Each ticker can only be picked once per round.");
        instruction.getStyleClass().add("hint-label");
        instruction.setWrapText(true);
        panel.getChildren().add(instruction);

        TextField search = new TextField();
        search.setPromptText("Search ticker or company...");
        search.getStyleClass().add("text-field");

        TableView<StockInfo> table = buildTickerTable();
        populateTickerTable(table, stocks, "");
        search.textProperty().addListener((obs, old, val) ->
                populateTickerTable(table, stocks, val.toLowerCase()));

        Button pickBtn = new Button("Confirm Pick");
        pickBtn.getStyleClass().add("primary-btn");
        pickBtn.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> pickBtn.setDisable(sel == null));

        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        pickBtn.setOnAction(e -> {
            StockInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            pickBtn.setDisable(true);
            search.setDisable(true);
            table.setDisable(true);

            new Thread(() -> {
                DraftPick pick = ApiClient.get().submitPick(
                        league.getId(), currentUser.getId(), selected.ticker);
                Platform.runLater(() -> {
                    if (pick != null) {
                        statusLabel.setText("Pick confirmed: " + selected.ticker
                                + "  —  Waiting for other players...");
                        statusLabel.getStyleClass().setAll("status-label", "success");

                        boolean done = ApiClient.get().allPicksSubmitted(
                                league.getId(), round);
                        if (done) {
                            panel.getChildren().clear();
                            panel.getChildren().addAll(title, buildAllDoneSection());
                        }
                    } else {
                        statusLabel.setText(
                                "That ticker was just taken. Please choose another.");
                        statusLabel.getStyleClass().setAll("status-label", "error");
                        search.setDisable(false);
                        table.setDisable(false);
                        // Refresh available tickers
                        List<StockInfo> fresh = ApiClient.get().getAvailableTickers(
                                league.getId(), round);
                        populateTickerTable(table, fresh, "");
                        pickBtn.setDisable(true);
                    }
                });
            }).start();
        });

        panel.getChildren().addAll(search, table, statusLabel, pickBtn);
        VBox.setVgrow(table, Priority.ALWAYS);
        return panel;
    }

    // ── All-Done Section ──────────────────────────────────────────────────────

    private VBox buildAllDoneSection() {
        VBox section = new VBox(12);

        Label done = new Label("✅  All picks are in for Round " + league.getCurrentRound() + "!");
        done.getStyleClass().add("section-label");

        if (league.isHostedBy(currentUser.getId())) {
            Label hostHint = new Label("As host, you can now enter the current prices and evaluate the round.");
            hostHint.getStyleClass().add("hint-label");
            hostHint.setWrapText(true);

            Button hostBtn = new Button("Enter Prices & Evaluate →");
            hostBtn.getStyleClass().add("primary-btn");
            hostBtn.setOnAction(e -> {
                new Thread(() -> {
                    League fresh = ApiClient.get().getLeagueById(league.getId());
                    Platform.runLater(() -> {
                        if (fresh != null) MainApp.showHostScreen(fresh);
                    });
                }).start();
            });
            section.getChildren().addAll(done, hostHint, hostBtn);
        } else {
            Label waitHint = new Label(
                    "⏳  Host is entering prices and evaluating the round…  "
                    + "You'll be taken to the leaderboard automatically when results are in.");
            waitHint.getStyleClass().add("hint-label");
            waitHint.setStyle("-fx-text-fill: #a0b0cc;");
            waitHint.setWrapText(true);

            Button lbBtn = new Button("View Leaderboard Now");
            lbBtn.getStyleClass().add("secondary-btn");
            lbBtn.setOnAction(e -> {
                new Thread(() -> {
                    League fresh = ApiClient.get().getLeagueById(league.getId());
                    Platform.runLater(() -> { if (fresh != null) MainApp.showLeaderboard(fresh); });
                }).start();
            });
            section.getChildren().addAll(done, waitHint, lbBtn);
        }

        return section;
    }

    // ── Last-Round Recap ──────────────────────────────────────────────────────

    private java.util.Optional<Label> buildLastRoundRecap(int currentRound) {
        int prevRound = currentRound - 1;
        List<DraftPick> prevPicks = ApiClient.get().getPicksForRound(league.getId(), prevRound);
        if (prevPicks.isEmpty()) return java.util.Optional.empty();

        DraftPick worstPick = null;
        double    worstPct  = Double.NEGATIVE_INFINITY;

        for (DraftPick p : prevPicks) {
            double before = ApiClient.get().getStockPrice(p.getTicker(), league.getId(), prevRound - 1);
            double after  = ApiClient.get().getStockPrice(p.getTicker(), league.getId(), prevRound);
            if (before > 0 && after > 0) {
                double pct = ((after - before) / before) * 100.0;
                if (pct > worstPct) { worstPct = pct; worstPick = p; }
            }
        }

        if (worstPick == null) return java.util.Optional.empty();

        final DraftPick finalWorstPick = worstPick;
        User   loser    = ApiClient.get().getUserById(worstPick.getUserId());
        String loserName = (loser != null) ? loser.getUsername() : "?";
        String pctStr   = String.format("%+.2f%%", worstPct);

        // Check whether the loser was actually eliminated in the previous round
        boolean wasEliminated = ApiClient.get().getLeaderboard(league.getId())
                .stream()
                .filter(e -> e.getUserId() == finalWorstPick.getUserId())
                .findFirst()
                .map(e -> e.getEliminationRound() == prevRound)
                .orElse(false);

        String outcomeText = wasEliminated
                ? loserName + " was eliminated"
                : loserName + " lost a life";

        Label recap = new Label(
                "🔙  Last round: " + outcomeText + " — "
                + worstPick.getTicker() + " was the worst short at " + pctStr + ".");
        recap.getStyleClass().add("hint-label");
        recap.setStyle(
                "-fx-text-fill: #e94560; "
                + "-fx-background-color: rgba(233,69,96,0.08); "
                + "-fx-background-radius: 6; "
                + "-fx-padding: 6 12 6 12;");
        recap.setWrapText(true);

        return java.util.Optional.of(recap);
    }

    // ── Ticker Table ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<StockInfo> buildTickerTable() {
        TableView<StockInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("stock-table");

        TableColumn<StockInfo, String> tickerCol = new TableColumn<>("Ticker");
        tickerCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().ticker));
        tickerCol.setMinWidth(80); tickerCol.setPrefWidth(90);

        TableColumn<StockInfo, String> nameCol = new TableColumn<>("Company Name");
        nameCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().companyName));
        nameCol.setMinWidth(180);

        TableColumn<StockInfo, String> priceCol = new TableColumn<>("Current Price");
        priceCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        String.format("$%.2f", c.getValue().currentPrice)));
        priceCol.setMinWidth(110); priceCol.setPrefWidth(120);

        table.getColumns().addAll(tickerCol, nameCol, priceCol);
        return table;
    }

    private void populateTickerTable(TableView<StockInfo> table,
                                     List<StockInfo> stocks, String filter) {
        table.getItems().clear();
        for (StockInfo s : stocks) {
            if (filter.isEmpty()
                    || s.ticker.toLowerCase().contains(filter)
                    || s.companyName.toLowerCase().contains(filter)) {
                table.getItems().add(s);
            }
        }
    }
}
