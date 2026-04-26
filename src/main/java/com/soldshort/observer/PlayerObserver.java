package com.soldshort.observer;

import java.util.List;

/**
 * Observer interface for the Sold Short tournament event system.
 *
 * Any class that needs to react to round completions or player eliminations
 * should implement this interface and register with the PortfolioEvaluationEngine.
 *
 * Design Pattern: Observer
 * Role: Observer (subscriber)
 */
public interface PlayerObserver {

    /**
     * Called when a player is eliminated at the end of a round.
     *
     * @param userId    the ID of the eliminated player
     * @param leagueId  the league in which the elimination occurred
     * @param round     the round number that caused the elimination
     * @param ticker    the stock ticker the player held when eliminated
     */
    void onPlayerEliminated(int userId, int leagueId, int round, String ticker);

    /**
     * Called after every round evaluation with the updated standings.
     *
     * @param leagueId   the league whose standings were just recalculated
     * @param round      the round that just completed
     * @param standings  ranked list of all players (active and eliminated)
     */
    void onRoundComplete(int leagueId, int round, List<LeaderboardEntry> standings);
}
