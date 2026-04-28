package com.soldshort.ui;

import com.soldshort.api.ApiClient;
import com.soldshort.api.ApiClient.StockInfo;
import com.soldshort.models.DraftPick;
import com.soldshort.models.League;
import com.soldshort.models.User;
import com.soldshort.observer.LeaderboardEntry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

/**
 * Host Control Panel — exclusive to the league host.
 *
 * Displays all drafted tickers for the current round.
 * The host enters current prices for each ticker, then evaluates the round.
 * Prices are sent to the server via {@link ApiClient#evaluateRound} which
 * applies them and returns the ranked results.
 */
public class HostControlScreen {

    private final User   currentUser;
    private final League league;

    /** ticker → price input field */
    private final Map<String, TextField> priceFields = new LinkedHashMap<>();
    /** ticker → previous price (fetched from server once) */
    private final Map<String, StockInfo> stockInfoMap = new HashMap<>();

    public HostControlScreen(User currentUser, League league) {
        this.currentUser = currentUser;
        this.league      = league;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public Pane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildTopBar());

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        root.setCenter(scroll);

        return root;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.getStyleClass().add("top-bar");

        Label title = new Label("HOST CONTROL  —  " + league.getName()
                + "  |  Round " + league.getCurrentRound());
        title.getStyleClass().add("nav-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button menuBtn = new Button("← Menu");
        menuBtn.getStyleClass().add("secondary-btn");
        menuBtn.setOnAction(e -> MainApp.showMainMenu());

        bar.getChildren().addAll(title, spacer, menuBtn);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(24);
        content.setPadding(new Insets(32));

        Label instruction = new Label(
                "Enter the current market price for each drafted ticker below, then click Evaluate Round.");
        instruction.getStyleClass().add("hint-label");
        instruction.setWrapText(true);

        // Fetch picks + stock info from server
        List<DraftPick> picks    = ApiClient.get().getPicksForRound(
                league.getId(), league.getCurrentRound());
        List<StockInfo> allStocks = ApiClient.get().getAllStocks();
        for (StockInfo s : allStocks) stockInfoMap.put(s.ticker, s);

        GridPane grid = new GridPane();
        grid.setHgap(20); grid.setVgap(12);
        grid.setPadding(new Insets(0, 0, 8, 0));

        ColumnConstraints cc0 = new ColumnConstraints(120);
        ColumnConstraints cc1 = new ColumnConstraints(75);
        ColumnConstraints cc2 = new ColumnConstraints(200);
        ColumnConstraints cc3 = new ColumnConstraints(110);
        ColumnConstraints cc4 = new ColumnConstraints(130);
        ColumnConstraints cc5 = new ColumnConstraints(80);  // Fetch button
        ColumnConstraints cc6 = new ColumnConstraints(90);  // Lives
        grid.getColumnConstraints().addAll(cc0, cc1, cc2, cc3, cc4, cc5, cc6);

        grid.add(headerLabel("Player"),        0, 0);
        grid.add(headerLabel("Ticker"),        1, 0);
        grid.add(headerLabel("Company"),       2, 0);
        grid.add(headerLabel("Prev. Price"),   3, 0);
        grid.add(headerLabel("New Price ($)"), 4, 0);
        grid.add(headerLabel(""),              5, 0);  // fetch column — no header
        grid.add(headerLabel("Lives"),         6, 0);

        for (int i = 0; i < picks.size(); i++) {
            DraftPick pick     = picks.get(i);
            User      player   = ApiClient.get().getUserById(pick.getUserId());
            String    ticker   = pick.getTicker();
            StockInfo info     = stockInfoMap.getOrDefault(ticker,
                    new StockInfo(ticker, ticker, 0, 0));
            double    prev     = info.previousPrice;

            Label playerLbl  = new Label(player != null ? player.getUsername() : "?");
            playerLbl.setStyle("-fx-text-fill: #dde3ee;");

            Label tickerLbl  = new Label(ticker);
            tickerLbl.getStyleClass().add("ticker-label");

            Label companyLbl = new Label(info.companyName);
            companyLbl.setStyle("-fx-text-fill: #8892a4;");

            Label prevLbl    = new Label(String.format("$%.2f", prev));
            prevLbl.setStyle("-fx-text-fill: #8892a4;");

            TextField priceField = new TextField(String.format("%.2f", info.currentPrice));
            priceField.getStyleClass().add("text-field");
            priceFields.put(ticker, priceField);

            Button fetchBtn = buildFetchButton(ticker, priceField);

            // Lives — fetch from leaderboard (server handles this)
            Label livesLbl = new Label("…");
            livesLbl.getStyleClass().add("lives-label");

            grid.add(playerLbl,  0, i + 1);
            grid.add(tickerLbl,  1, i + 1);
            grid.add(companyLbl, 2, i + 1);
            grid.add(prevLbl,    3, i + 1);
            grid.add(priceField, 4, i + 1);
            grid.add(fetchBtn,   5, i + 1);
            grid.add(livesLbl,   6, i + 1);
        }

        // Fill in lives column asynchronously
        new Thread(() -> {
            List<LeaderboardEntry> lb = ApiClient.get().getLeaderboard(league.getId());
            Map<Integer, Integer> livesMap = new HashMap<>();
            for (LeaderboardEntry e : lb) livesMap.put(e.getUserId(), e.getLivesRemaining());

            Platform.runLater(() -> {
                for (int i = 0; i < picks.size(); i++) {
                    final int row = i + 1;
                    DraftPick pick  = picks.get(i);
                    int lives = livesMap.getOrDefault(pick.getUserId(), league.getStartingLives());
                    String hearts = LeaderboardEntry.heartsDisplay(lives, league.getStartingLives());
                    Label livesLbl = (Label) grid.getChildren().stream()
                            .filter(n -> GridPane.getColumnIndex(n) != null
                                    && GridPane.getColumnIndex(n) == 6
                                    && GridPane.getRowIndex(n) != null
                                    && GridPane.getRowIndex(n) == row)
                            .findFirst().orElse(null);
                    if (livesLbl != null) livesLbl.setText(hearts);
                }
            });
        }).start();

        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        Button evalBtn = new Button("Review & Evaluate →");
        evalBtn.getStyleClass().add("primary-btn");
        evalBtn.setOnAction(e -> handleReviewPrices(statusLabel, evalBtn));

        content.getChildren().addAll(instruction, grid, new Separator(), statusLabel, evalBtn);
        return content;
    }

    // ── Price Review Overlay ──────────────────────────────────────────────────

    /**
     * Validates the entered prices, then shows a confirmation overlay with a
     * before/after summary table.  The host must click "Confirm & Evaluate" to
     * actually commit the round.
     */
    private void handleReviewPrices(Label statusLabel, Button evalBtn) {
        // Validate first — same logic as handleEvaluate
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Map.Entry<String, TextField> entry : priceFields.entrySet()) {
            String ticker = entry.getKey();
            String rawVal = entry.getValue().getText().trim();
            try {
                double price = Double.parseDouble(rawVal.replace(",", ""));
                if (price <= 0) throw new NumberFormatException();
                prices.put(ticker, price);
            } catch (NumberFormatException ex) {
                statusLabel.setText("Invalid price for " + ticker + ": '" + rawVal + "'");
                statusLabel.getStyleClass().setAll("status-label", "error");
                return;
            }
        }

        // Build confirmation overlay
        VBox overlay = new VBox(16);
        overlay.getStyleClass().add("overlay-card");
        overlay.setMaxWidth(600);

        Label header = new Label("Confirm Prices  —  Round " + league.getCurrentRound());
        header.setStyle("-fx-text-fill: #dde3ee; -fx-font-size: 16px; -fx-font-weight: bold;");
        overlay.getChildren().add(header);

        Label hint = new Label(
                "Review the prices below before committing. "
                + "These will be applied to score Round " + league.getCurrentRound() + ".");
        hint.setStyle("-fx-text-fill: #8892a4; -fx-font-size: 13px;");
        hint.setWrapText(true);
        overlay.getChildren().add(hint);

        GridPane reviewGrid = new GridPane();
        reviewGrid.setHgap(20); reviewGrid.setVgap(10);
        reviewGrid.add(styledLabel("Ticker",      "#8892a4"), 0, 0);
        reviewGrid.add(styledLabel("Company",     "#8892a4"), 1, 0);
        reviewGrid.add(styledLabel("Prev. Price", "#8892a4"), 2, 0);
        reviewGrid.add(styledLabel("New Price",   "#8892a4"), 3, 0);
        reviewGrid.add(styledLabel("Change",      "#8892a4"), 4, 0);

        reviewGrid.getColumnConstraints().addAll(
                new ColumnConstraints(80),
                new ColumnConstraints(190),
                new ColumnConstraints(90),
                new ColumnConstraints(90),
                new ColumnConstraints(90));

        int row = 1;
        for (Map.Entry<String, Double> entry : prices.entrySet()) {
            String ticker   = entry.getKey();
            double newPrice = entry.getValue();
            StockInfo info  = stockInfoMap.getOrDefault(ticker,
                    new StockInfo(ticker, ticker, 0, 0));
            double prevPrice = info.previousPrice;
            double pct       = (prevPrice > 0) ? ((newPrice - prevPrice) / prevPrice) * 100.0 : 0.0;
            String pctStr    = (prevPrice > 0) ? String.format("%+.2f%%", pct) : "—";
            String pctColor  = (pct > 0) ? "#e94560" : (pct < 0 ? "#4caf50" : "#8892a4");

            reviewGrid.add(styledLabel(ticker,                           "#dde3ee"), 0, row);
            reviewGrid.add(styledLabel(info.companyName,                 "#8892a4"), 1, row);
            reviewGrid.add(styledLabel(String.format("$%.2f", prevPrice),"#8892a4"), 2, row);
            reviewGrid.add(styledLabel(String.format("$%.2f", newPrice), "#dde3ee"), 3, row);
            reviewGrid.add(styledLabel(pctStr,                           pctColor),  4, row);
            row++;
        }

        ScrollPane gridScroll = new ScrollPane(reviewGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.setFitToHeight(false);
        gridScroll.setPrefViewportHeight(Math.min(prices.size() * 32 + 44, 280));
        gridScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        overlay.getChildren().add(gridScroll);

        overlay.getChildren().add(new Separator());

        Button confirmBtn = new Button("Confirm & Evaluate →");
        confirmBtn.getStyleClass().add("primary-btn");

        Button backBtn = new Button("← Go Back");
        backBtn.getStyleClass().add("secondary-btn");
        backBtn.setOnAction(ev -> MainApp.hideOverlay());

        HBox btnRow = new HBox(12, confirmBtn, backBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        overlay.getChildren().add(btnRow);

        Label overlayStatus = new Label("");
        overlayStatus.getStyleClass().add("status-label");
        overlayStatus.setWrapText(true);
        overlay.getChildren().add(overlayStatus);

        confirmBtn.setOnAction(ev -> {
            confirmBtn.setDisable(true);
            backBtn.setDisable(true);
            overlayStatus.setText("Evaluating…");
            overlayStatus.getStyleClass().setAll("status-label");
            handleEvaluate(overlayStatus, confirmBtn, prices);
        });

        MainApp.showOverlay(overlay);
    }

    // ── Evaluation Handler ────────────────────────────────────────────────────

    /** Called from the confirmation overlay with pre-validated prices. */
    private void handleEvaluate(Label statusLabel, Button evalBtn, Map<String, Double> prices) {
        evalBtn.setDisable(true);
        statusLabel.setText("Evaluating…");
        statusLabel.getStyleClass().setAll("status-label");

        new Thread(() -> {
            // Guard: already evaluated?
            boolean alreadyDone = ApiClient.get().isRoundEvaluated(
                    league.getId(), league.getCurrentRound());

            List<LeaderboardEntry> results;
            if (alreadyDone) {
                results = ApiClient.get().getLeaderboard(league.getId());
            } else {
                results = ApiClient.get().evaluateRound(
                        league.getId(), currentUser.getId(), prices);
            }

            League fresh = ApiClient.get().getLeagueById(league.getId());

            Platform.runLater(() -> {
                evalBtn.setDisable(false);
                if (results.isEmpty()) {
                    statusLabel.setText("Evaluation failed. Check server connection.");
                    statusLabel.getStyleClass().setAll("status-label", "error");
                } else {
                    showResultsOverlay(results, fresh != null ? fresh : league, alreadyDone);
                }
            });
        }).start();
    }

    // ── Results Overlay ───────────────────────────────────────────────────────

    private void showResultsOverlay(List<LeaderboardEntry> results, League fresh,
                                    boolean wasAlreadyEvaluated) {
        boolean tournamentOver = fresh.getStatus() == League.Status.FINISHED;

        boolean isTie = results.size() >= 2
                && Math.abs(results.get(results.size() - 1).getPercentChange()
                          - results.get(results.size() - 2).getPercentChange()) < 0.0001;

        LeaderboardEntry roundLoser = (!isTie && !results.isEmpty())
                ? results.get(results.size() - 1) : null;

        // Find winner if tournament is over
        User winner = null;
        if (tournamentOver) {
            winner = results.stream()
                    .filter(e -> !e.isEliminated())
                    .map(e -> {
                        User u = new User();
                        u.setId(e.getUserId());
                        u.setUsername(e.getUsername());
                        return u;
                    })
                    .findFirst().orElse(null);
        }

        VBox content = new VBox(18);
        content.getStyleClass().add("overlay-card");
        content.setMaxWidth(640);

        Label header = new Label(tournamentOver
                ? "🏆  Tournament Over!"
                : "Round " + fresh.getCurrentRound() + " Results");
        header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        header.setStyle("-fx-text-fill: " + (tournamentOver ? "#ffd700" : "#e94560") + ";");
        content.getChildren().add(header);

        // Results grid
        GridPane grid = new GridPane();
        grid.setHgap(20); grid.setVgap(10);

        grid.getColumnConstraints().addAll(
                new ColumnConstraints(40),  // Rank
                new ColumnConstraints(130), // Player
                new ColumnConstraints(70),  // Ticker
                new ColumnConstraints(80),  // Before
                new ColumnConstraints(80),  // After
                new ColumnConstraints(80),  // Change
                new ColumnConstraints(90)); // Lives

        grid.add(styledLabel("Rank",   "#8892a4"), 0, 0);
        grid.add(styledLabel("Player", "#8892a4"), 1, 0);
        grid.add(styledLabel("Ticker", "#8892a4"), 2, 0);
        grid.add(styledLabel("Before", "#8892a4"), 3, 0);
        grid.add(styledLabel("After",  "#8892a4"), 4, 0);
        grid.add(styledLabel("Change", "#8892a4"), 5, 0);
        grid.add(styledLabel("Lives",  "#8892a4"), 6, 0);

        Separator headerSep = new Separator();
        grid.add(headerSep, 0, 1);
        GridPane.setColumnSpan(headerSep, 7);

        for (int i = 0; i < results.size(); i++) {
            LeaderboardEntry e      = results.get(i);
            boolean          isElim = e.isEliminated();
            String           ticker = e.getTicker();

            double beforePrice = ApiClient.get().getStockPrice(ticker, fresh.getId(), fresh.getCurrentRound() - 1);
            double afterPrice  = ApiClient.get().getStockPrice(ticker, fresh.getId(), fresh.getCurrentRound());
            String beforeStr   = beforePrice > 0 ? String.format("$%.2f", beforePrice) : "—";
            String afterStr    = afterPrice  > 0 ? String.format("$%.2f", afterPrice)  : "—";
            String changeStr   = String.format("%+.2f%%", e.getPercentChange());
            String rowColor    = isElim ? "#e94560" : (e.getPercentChange() < 0 ? "#4caf50" : "#dde3ee");
            String heartsStr   = LeaderboardEntry.heartsDisplay(
                    e.getLivesRemaining(), league.getStartingLives());

            grid.add(styledLabel("#" + e.getRank(), rowColor), 0, i + 2);
            grid.add(styledLabel(e.getUsername(),   rowColor), 1, i + 2);
            grid.add(styledLabel(ticker,            rowColor), 2, i + 2);
            grid.add(styledLabel(beforeStr,         "#8892a4"), 3, i + 2);
            grid.add(styledLabel(afterStr,          rowColor), 4, i + 2);
            grid.add(styledLabel(changeStr,         rowColor), 5, i + 2);
            grid.add(styledLabel(heartsStr,         rowColor), 6, i + 2);
        }

        ScrollPane gridScroll = new ScrollPane(grid);
        gridScroll.setFitToWidth(true);
        gridScroll.setFitToHeight(false);
        gridScroll.setPrefViewportHeight(Math.min(results.size() * 30 + 50, 320));
        gridScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        content.getChildren().add(gridScroll);

        if (isTie) {
            content.getChildren().add(new Separator());
            double worstPct = results.get(results.size() - 1).getPercentChange();
            StringBuilder tied = new StringBuilder();
            for (LeaderboardEntry e : results) {
                if (Math.abs(e.getPercentChange() - worstPct) < 0.0001) {
                    if (tied.length() > 0) tied.append(" & ");
                    tied.append(e.getUsername()).append(" (").append(e.getTicker()).append(")");
                }
            }
            Label tieMsg = new Label(
                    "🤝  Tie!  " + tied
                    + " picked stocks with identical market performance ("
                    + String.format("%+.2f%%", worstPct)
                    + ").  No one loses a life this round.");
            tieMsg.setStyle("-fx-text-fill: #a0b0cc; -fx-font-weight: bold; -fx-font-size: 13px;");
            tieMsg.setWrapText(true);
            content.getChildren().add(tieMsg);
        }

        if (roundLoser != null) {
            content.getChildren().add(new Separator());
            double pct    = roundLoser.getPercentChange();
            String pctStr = String.format("%+.2f%%", pct);
            String desc   = (pct >= 0)
                    ? roundLoser.getTicker() + " rose " + pctStr
                    : roundLoser.getTicker() + " fell only " + pctStr;

            if (roundLoser.isEliminated()) {
                Label msg = new Label("💀  " + roundLoser.getUsername() + " is eliminated!  " + desc);
                msg.setStyle("-fx-text-fill: #e94560; -fx-font-weight: bold; -fx-font-size: 13px;");
                msg.setWrapText(true);
                content.getChildren().add(msg);
            } else {
                int    livesLeft = roundLoser.getLivesRemaining();
                String hearts    = LeaderboardEntry.heartsDisplay(livesLeft, league.getStartingLives());
                Label msg = new Label("♥  " + roundLoser.getUsername() + " loses a life!  "
                        + desc + "  " + hearts + "  " + livesLeft + " "
                        + (livesLeft == 1 ? "life" : "lives") + " remaining.");
                msg.setStyle("-fx-text-fill: #ffd700; -fx-font-weight: bold; -fx-font-size: 13px;");
                msg.setWrapText(true);
                content.getChildren().add(msg);
            }
        }

        if (tournamentOver && winner != null) {
            Label winMsg = new Label("🏆  " + winner.getUsername() + " wins the tournament!");
            winMsg.setStyle("-fx-text-fill: #ffd700; -fx-font-weight: bold; -fx-font-size: 15px;");
            content.getChildren().add(winMsg);
        }

        Button lbBtn = new Button(tournamentOver ? "View Final Results" : "View Leaderboard →");
        lbBtn.getStyleClass().add("primary-btn");
        lbBtn.setOnAction(e -> {
            MainApp.hideOverlay();
            new Thread(() -> {
                League dest = ApiClient.get().getLeagueById(fresh.getId());
                Platform.runLater(() -> { if (dest != null) MainApp.showLeaderboard(dest); });
            }).start();
        });
        content.getChildren().add(lbBtn);

        MainApp.showOverlay(content);
    }

    // ── Fetch Button ──────────────────────────────────────────────────────────

    /**
     * Builds the "↻ Fetch" button for a single ticker row.
     *
     * On click: the button is disabled and shows "…" while the request runs on
     * a background thread.  On success the price field is populated and the
     * button briefly shows "✓" before resetting.  On failure it shows "✗" for
     * a moment so the host knows the lookup didn't work (market closed, bad
     * ticker, no server connectivity, etc.).
     */
    private Button buildFetchButton(String ticker, TextField priceField) {
        Button btn = new Button("↻ Fetch");
        btn.getStyleClass().add("secondary-btn");
        btn.setStyle(btn.getStyle() + "-fx-font-size: 11px; -fx-padding: 4 8 4 8;");

        btn.setOnAction(e -> {
            btn.setDisable(true);
            btn.setText("…");

            new Thread(() -> {
                double price = ApiClient.get().fetchLivePrice(ticker);

                Platform.runLater(() -> {
                    btn.setDisable(false);
                    if (price > 0) {
                        priceField.setText(String.format("%.2f", price));
                        btn.setText("✓");
                        // Reset label after 1.5 s
                        new Thread(() -> {
                            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                            Platform.runLater(() -> btn.setText("↻ Fetch"));
                        }).start();
                    } else {
                        btn.setText("✗ N/A");
                        new Thread(() -> {
                            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                            Platform.runLater(() -> btn.setText("↻ Fetch"));
                        }).start();
                    }
                });
            }, "FetchPrice-" + ticker).start();
        });

        return btn;
    }

    // ── Label Helpers ─────────────────────────────────────────────────────────

    private Label styledLabel(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px;");
        return l;
    }

    private Label headerLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("table-header");
        return l;
    }
}
