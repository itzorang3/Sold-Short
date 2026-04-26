package com.soldshort.engine;

import com.soldshort.data.DataManager;
import com.soldshort.market.MarketDataProvider;
import com.soldshort.models.DraftPick;
import com.soldshort.models.League;
import com.soldshort.models.User;
import com.soldshort.observer.LeaderboardEntry;
import com.soldshort.observer.PlayerObserver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core game-logic engine for Sold Short.
 *
 * After every round the host enters updated stock prices. This engine:
 *   1. Fetches every active player's pick for the round.
 *   2. Computes percent change (current vs previous price) per player.
 *   3. Sorts players worst-to-best (highest % change = stock went up = bad).
 *   4. Deducts one life from the worst performer.
 *      - If lives reach 0, the player is fully eliminated from the tournament.
 *      - Otherwise they survive with one fewer life.
 *   5. Persists elimination and price records.
 *   6. Notifies all registered PlayerObservers.
 *   7. Returns the ranked leaderboard for the UI to display.
 *
 * Design Patterns:
 *   - Observer: maintains a list of PlayerObserver subscribers and fires
 *               onPlayerEliminated() and onRoundComplete() after each evaluation.
 *   - Strategy: accepts any MarketDataProvider, making the price source swappable.
 */
public class PortfolioEvaluationEngine {

    private final DataManager           dataManager;
    private final List<PlayerObserver>  observers;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PortfolioEvaluationEngine() {
        this.dataManager = DataManager.getInstance();
        this.observers   = new ArrayList<>();
    }

    // ── Observer Registration ─────────────────────────────────────────────────

    /** Registers a subscriber that will be notified after every round evaluation. */
    public void addObserver(PlayerObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /** Removes a subscriber from the notification list. */
    public void removeObserver(PlayerObserver observer) {
        observers.remove(observer);
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /**
     * Evaluates the completed round for a league.
     *
     * @param league  the league being evaluated
     * @param round   the round number just completed
     * @param market  the MarketDataProvider holding updated prices
     * @return ranked list of LeaderboardEntry (best performer first, worst last)
     */
    public List<LeaderboardEntry> evaluateRound(League league, int round,
                                                MarketDataProvider market) {
        int leagueId = league.getId();

        // Step 1: get all active players and their picks for this round
        List<User>      activePlayers = dataManager.getActiveMembers(leagueId);
        List<DraftPick> roundPicks    = dataManager.getPicksForRound(leagueId, round);

        // Step 2: build unsorted entries with percent change
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (User player : activePlayers) {
            DraftPick pick = roundPicks.stream()
                    .filter(p -> p.getUserId() == player.getId())
                    .findFirst()
                    .orElse(null);

            if (pick == null) {
                System.err.println("evaluateRound: no pick for user " + player.getId()
                        + " in round " + round + ". Treating as 0%.");
                int lives = dataManager.getLives(leagueId, player.getId());
                entries.add(new LeaderboardEntry(
                        player.getId(), player.getUsername(), "—", 0.0, 0, false, -1, Math.max(lives, 0)));
                continue;
            }

            String ticker        = pick.getTicker();
            double currentPrice  = market.getPrice(ticker);
            double previousPrice = market.getPreviousPrice(ticker);

            // Persist current price for this round
            if (currentPrice > 0) {
                dataManager.saveStockPrice(ticker, currentPrice, leagueId, round);
            }
            // Persist previous price as round-1 so % change survives app restarts
            if (previousPrice > 0) {
                double existing = dataManager.getStockPrice(ticker, leagueId, round - 1);
                if (existing < 0) {
                    dataManager.saveStockPrice(ticker, previousPrice, leagueId, round - 1);
                }
            }

            double pctChange = (previousPrice > 0)
                    ? ((currentPrice - previousPrice) / previousPrice) * 100.0
                    : 0.0;

            int lives = dataManager.getLives(leagueId, player.getId());
            entries.add(new LeaderboardEntry(
                    player.getId(), player.getUsername(), ticker, pctChange, 0, false, -1,
                    Math.max(lives, 0)));
        }

        // Step 3: sort ascending by percent change
        // Lowest (most negative = fell the most) is BEST. Highest = WORST.
        entries.sort(Comparator.comparingDouble(LeaderboardEntry::getPercentChange));

        // Step 4: assign ranks (1 = best performer)
        List<LeaderboardEntry> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            ranked.add(new LeaderboardEntry(
                    e.getUserId(), e.getUsername(), e.getTicker(),
                    e.getPercentChange(), i + 1, false, -1, e.getLivesRemaining()));
        }

        // Step 5: check for a tie at the bottom before deducting any life.
        // If two or more players share the exact worst % change, no one loses a life this round.
        boolean tiedRound = false;
        if (ranked.size() >= 2) {
            double worst       = ranked.get(ranked.size() - 1).getPercentChange();
            double secondWorst = ranked.get(ranked.size() - 2).getPercentChange();
            tiedRound = Math.abs(worst - secondWorst) < 0.0001;
        }

        // Deduct one life from the worst performer (only when there is no tie).
        LeaderboardEntry roundLoser = null;
        if (!tiedRound && !ranked.isEmpty()) {
            LeaderboardEntry worst = ranked.get(ranked.size() - 1);
            int livesLeft = dataManager.deductLife(leagueId, worst.getUserId(), round);
            boolean fullyEliminated = (livesLeft == 0);

            roundLoser = new LeaderboardEntry(
                    worst.getUserId(), worst.getUsername(), worst.getTicker(),
                    worst.getPercentChange(), worst.getRank(),
                    fullyEliminated,
                    fullyEliminated ? round : -1,
                    livesLeft);

            ranked.set(ranked.size() - 1, roundLoser);
        }

        // Step 6: transition league to ACTIVE; check if tournament is over
        dataManager.updateLeagueStatus(leagueId, League.Status.ACTIVE);

        List<User> survivors = dataManager.getActiveMembers(leagueId);
        if (survivors.size() <= 1) {
            dataManager.updateLeagueStatus(leagueId, League.Status.FINISHED);
        }

        // Step 7: notify observers
        if (roundLoser != null && roundLoser.isEliminated()) {
            final LeaderboardEntry finalElim = roundLoser;
            for (PlayerObserver obs : observers) {
                obs.onPlayerEliminated(
                        finalElim.getUserId(), leagueId, round, finalElim.getTicker());
            }
        }
        final List<LeaderboardEntry> finalRanked = ranked;
        for (PlayerObserver obs : observers) {
            obs.onRoundComplete(leagueId, round, finalRanked);
        }

        return ranked;
    }

    /**
     * Returns the current leaderboard for a league without triggering evaluation.
     */
    public List<LeaderboardEntry> getLeaderboard(int leagueId) {
        League league = dataManager.getLeagueById(leagueId);
        if (league == null) return new ArrayList<>();
        return dataManager.getLeaderboard(leagueId, league.getCurrentRound());
    }

    /** Returns true if only 0 or 1 active members remain. */
    public boolean isTournamentOver(int leagueId) {
        return dataManager.getActiveMembers(leagueId).size() <= 1;
    }

    /** Returns the winner (last remaining active player), or null. */
    public User getWinner(int leagueId) {
        List<User> survivors = dataManager.getActiveMembers(leagueId);
        return (survivors.size() == 1) ? survivors.get(0) : null;
    }
}
