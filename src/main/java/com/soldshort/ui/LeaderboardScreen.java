package com.soldshort.ui;

import com.soldshort.api.ApiClient;
import com.soldshort.api.ApiClient.StockInfo;
import com.soldshort.models.DraftPick;
import com.soldshort.models.League;
import com.soldshort.models.User;
import com.soldshort.observer.LeaderboardEntry;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/**
 * Leaderboard screen — displays current standings for a league.
 *
 * All data is fetched from the server via {@link ApiClient}.
 * Host-only "Start Next Round" button is shown when the league is ACTIVE.
 */
public class LeaderboardScreen {

    private final League league;

    public LeaderboardScreen(League league) {
        this.league = league;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public Pane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildTopBar());
        root.setCenter(buildLeaderboardPanel());
        return root;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.getStyleClass().add("top-bar");

        String statusText = switch (league.getStatus()) {
            case FORMING  -> "Forming";
            case DRAFTING -> "Round " + league.getCurrentRound() + " — Drafting";
            case ACTIVE   -> "Round " + league.getCurrentRound() + " — Active";
            case FINISHED -> "Tournament Complete";
        };

        Label title = new Label("LEADERBOARD  —  " + league.getName() + "  |  " + statusText);
        title.getStyleClass().add("nav-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> {
            new Thread(() -> {
                League fresh = ApiClient.get().getLeagueById(league.getId());
                Platform.runLater(() -> { if (fresh != null) MainApp.showLeaderboard(fresh); });
            }).start();
        });

        Button menuBtn = new Button("← Menu");
        menuBtn.getStyleClass().add("secondary-btn");
        menuBtn.setOnAction(e -> MainApp.showMainMenu());

        bar.getChildren().addAll(title, spacer, refreshBtn, menuBtn);

        // Host-only: "Start Next Round" when ACTIVE
        User currentUser = MainApp.getCurrentUser();
        if (league.getStatus() == League.Status.ACTIVE
                && currentUser != null
                && league.isHostedBy(currentUser.getId())) {

            Button nextRoundBtn = new Button("Start Next Round →");
            nextRoundBtn.getStyleClass().add("primary-btn");
            nextRoundBtn.setOnAction(e -> {
                nextRoundBtn.setDisable(true);
                new Thread(() -> {
                    League next = ApiClient.get().startNextRound(
                            league.getId(), currentUser.getId());
                    Platform.runLater(() -> {
                        nextRoundBtn.setDisable(false);
                        if (next == null) return;
                        if (next.getStatus() == League.Status.DRAFTING) {
                            MainApp.showDraftScreen(next);
                        } else {
                            // Tournament ended — refresh leaderboard in FINISHED state
                            MainApp.showLeaderboard(next);
                        }
                    });
                }).start();
            });
            bar.getChildren().add(nextRoundBtn);
        }

        return bar;
    }

    @SuppressWarnings("unchecked")
    private VBox buildLeaderboardPanel() {
        VBox panel = new VBox(16);
        panel.setPadding(new Insets(32));

        List<LeaderboardEntry> entries  = ApiClient.get().getLeaderboard(league.getId());
        int                    maxLives = league.getStartingLives();

        // Winner banner
        if (league.getStatus() == League.Status.FINISHED) {
            String winnerName = entries.stream()
                    .filter(e -> !e.isEliminated())
                    .map(LeaderboardEntry::getUsername)
                    .findFirst().orElse("—");
            Label winnerLabel = new Label("🏆  Tournament Winner: " + winnerName);
            winnerLabel.getStyleClass().add("winner-label");
            panel.getChildren().add(winnerLabel);
        }

        TableView<LeaderboardEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("stock-table");

        TableColumn<LeaderboardEntry, String> rankCol = new TableColumn<>("Rank");
        rankCol.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getRank())));
        rankCol.setMinWidth(55); rankCol.setPrefWidth(55);

        TableColumn<LeaderboardEntry, String> playerCol = new TableColumn<>("Player");
        playerCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUsername()));
        playerCol.setMinWidth(120);

        TableColumn<LeaderboardEntry, String> tickerCol = new TableColumn<>("Ticker");
        tickerCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTicker()));
        tickerCol.setMinWidth(80); tickerCol.setPrefWidth(85);

        TableColumn<LeaderboardEntry, String> changeCol = new TableColumn<>("% Change");
        changeCol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFormattedChange()));
        changeCol.setMinWidth(100); changeCol.setPrefWidth(110);
        changeCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty && item != null) {
                    setStyle(!item.startsWith("-")
                            ? "-fx-text-fill: #e94560;" : "-fx-text-fill: #4caf50;");
                } else setStyle("");
            }
        });

        TableColumn<LeaderboardEntry, String> livesCol = new TableColumn<>("Lives");
        livesCol.setCellValueFactory(c -> {
            LeaderboardEntry e = c.getValue();
            String hearts = e.isEliminated()
                    ? "☠" : LeaderboardEntry.heartsDisplay(e.getLivesRemaining(), maxLives);
            return new SimpleStringProperty(hearts);
        });
        livesCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty && item != null) {
                    if (item.contains("☠"))    setStyle("-fx-text-fill: #e94560;");
                    else if (item.contains("♡")) setStyle("-fx-text-fill: #ffd700;");
                    else                          setStyle("-fx-text-fill: #4caf50;");
                } else setStyle("");
            }
        });
        livesCol.setMinWidth(90); livesCol.setPrefWidth(100);

        TableColumn<LeaderboardEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> {
            LeaderboardEntry e = c.getValue();
            String text;
            if (e.isEliminated())              text = "Eliminated (Rd " + e.getEliminationRound() + ")";
            else if (e.getEliminationRound() == -2) text = "Winner 🏆";
            else                               text = "Active";
            return new SimpleStringProperty(text);
        });
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty && item != null) {
                    if (item.contains("Winner")) setStyle("-fx-text-fill: #ffd700; -fx-font-weight: bold;");
                    else if (item.contains("Elim")) setStyle("-fx-text-fill: #e94560;");
                    else setStyle("-fx-text-fill: #4caf50;");
                } else setStyle("");
            }
        });
        statusCol.setMinWidth(160); statusCol.setPrefWidth(180);

        table.getColumns().addAll(rankCol, playerCol, tickerCol, changeCol, livesCol, statusCol);
        table.getItems().addAll(entries);

        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(LeaderboardEntry item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(!empty && item != null && item.isEliminated()
                        ? "-fx-opacity: 0.5;" : "");
            }
        });

        VBox.setVgrow(table, Priority.ALWAYS);
        panel.getChildren().add(table);

        Label legend = new Label(
                "📉 Green % = stock fell (good for short sellers)   •   "
                + "📈 Red % = stock rose (bad — this player loses a life)   •   ❤ = lives remaining");
        legend.getStyleClass().add("hint-label");
        panel.getChildren().add(legend);

        if (league.getStatus() == League.Status.FINISHED) {
            panel.getChildren().addAll(new Separator(), buildHistoryPanel());
        }

        return panel;
    }

    // ── Round History Panel ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private VBox buildHistoryPanel() {
        VBox section = new VBox(10);

        Label title = new Label("📋  Round History — All Picks");
        title.getStyleClass().add("section-label");
        section.getChildren().add(title);

        // Fetch stock info for company name lookups
        List<StockInfo> allStocks = ApiClient.get().getAllStocks();
        Map<String, StockInfo> stockMap = new HashMap<>();
        for (StockInfo s : allStocks) stockMap.put(s.ticker, s);

        List<DraftPick> allPicks = ApiClient.get().getAllPicksForLeague(league.getId());
        allPicks.sort(Comparator.comparingInt(DraftPick::getRoundNumber).reversed());

        List<String[]> rows = new ArrayList<>();
        for (DraftPick pick : allPicks) {
            User   player  = ApiClient.get().getUserById(pick.getUserId());
            String name    = (player != null) ? player.getUsername() : "?";
            String ticker  = pick.getTicker();
            int    round   = pick.getRoundNumber();
            double before  = ApiClient.get().getStockPrice(ticker, league.getId(), round - 1);
            double after   = ApiClient.get().getStockPrice(ticker, league.getId(), round);
            String bStr    = before > 0 ? String.format("$%.2f", before) : "—";
            String aStr    = after  > 0 ? String.format("$%.2f", after)  : "—";
            double pct     = (before > 0 && after > 0)
                             ? ((after - before) / before) * 100.0 : 0.0;
            String pctStr  = (before > 0 && after > 0)
                             ? String.format("%+.2f%%", pct) : "—";
            String company = stockMap.containsKey(ticker)
                             ? stockMap.get(ticker).companyName : ticker;

            rows.add(new String[]{ String.valueOf(round), name, ticker,
                    company, bStr, aStr, pctStr, String.valueOf(pct) });
        }

        TableView<String[]> histTable = new TableView<>();
        histTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        histTable.getStyleClass().add("stock-table");

        TableColumn<String[], String> roundCol = new TableColumn<>("Rd");
        roundCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        roundCol.setMinWidth(40); roundCol.setPrefWidth(45);

        TableColumn<String[], String> playerCol = new TableColumn<>("Player");
        playerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        playerCol.setMinWidth(100);

        TableColumn<String[], String> tickerCol = new TableColumn<>("Ticker");
        tickerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        tickerCol.setMinWidth(70); tickerCol.setPrefWidth(75);

        TableColumn<String[], String> companyCol = new TableColumn<>("Company");
        companyCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));
        companyCol.setMinWidth(160);

        TableColumn<String[], String> beforeCol = new TableColumn<>("Before");
        beforeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[4]));
        beforeCol.setMinWidth(80); beforeCol.setPrefWidth(90);

        TableColumn<String[], String> afterCol = new TableColumn<>("After");
        afterCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[5]));
        afterCol.setMinWidth(80); afterCol.setPrefWidth(90);

        TableColumn<String[], String> changeCol = new TableColumn<>("% Change");
        changeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[6]));
        changeCol.setMinWidth(90); changeCol.setPrefWidth(100);
        changeCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (!empty && item != null && !item.equals("—")) {
                    setStyle(item.startsWith("+")
                            ? "-fx-text-fill: #e94560;" : "-fx-text-fill: #4caf50;");
                } else setStyle("");
            }
        });

        histTable.getColumns().addAll(roundCol, playerCol, tickerCol, companyCol,
                beforeCol, afterCol, changeCol);
        histTable.getItems().addAll(rows);
        histTable.setPrefHeight(Math.min(rows.size() * 28 + 44, 400));

        section.getChildren().add(histTable);
        return section;
    }
}
