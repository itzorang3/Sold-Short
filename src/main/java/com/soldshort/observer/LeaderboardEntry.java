package com.soldshort.observer;

/**
 * Immutable data transfer object representing a single player's standing
 * at the conclusion of a tournament round.
 *
 * Instances are produced by PortfolioEvaluationEngine and passed to all
 * registered PlayerObservers via onRoundComplete().
 */
public class LeaderboardEntry {

    private final int     userId;
    private final String  username;
    private final String  ticker;
    private final double  percentChange;
    private final int     rank;
    private final boolean eliminated;
    private final int     eliminationRound;   // -1 if active, -2 if winner
    private final int     livesRemaining;     // 0 if eliminated, 1+ if still alive

    /** Full constructor (used by evaluation engine). */
    public LeaderboardEntry(int userId, String username, String ticker,
                            double percentChange, int rank,
                            boolean eliminated, int eliminationRound,
                            int livesRemaining) {
        this.userId           = userId;
        this.username         = username;
        this.ticker           = ticker;
        this.percentChange    = percentChange;
        this.rank             = rank;
        this.eliminated       = eliminated;
        this.eliminationRound = eliminationRound;
        this.livesRemaining   = livesRemaining;
    }

    /** Backward-compatible constructor (livesRemaining defaults to 0). */
    public LeaderboardEntry(int userId, String username, String ticker,
                            double percentChange, int rank,
                            boolean eliminated, int eliminationRound) {
        this(userId, username, ticker, percentChange, rank, eliminated, eliminationRound, 0);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int    getUserId()           { return userId; }
    public String getUsername()         { return username; }
    public String getTicker()           { return ticker; }
    public double getPercentChange()    { return percentChange; }
    public int    getRank()             { return rank; }
    public boolean isEliminated()       { return eliminated; }
    public int    getEliminationRound() { return eliminationRound; }
    public int    getLivesRemaining()   { return livesRemaining; }

    /** Convenience: formatted percent change string, e.g. "-3.42%" */
    public String getFormattedChange() {
        return String.format("%.2f%%", percentChange);
    }

    /**
     * Returns a hearts string like "❤❤♡" for {@code remaining} lives out of {@code total}.
     * Falls back to a skull (☠) when no lives remain.
     */
    public static String heartsDisplay(int remaining, int total) {
        if (total <= 0) total = 3;          // guard against un-migrated DB rows
        if (remaining <= 0) return "\u2620"; // ☠
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            sb.append(i < remaining ? "\u2665" : "\u2661"); // ❤ / ♡
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format(
                "LeaderboardEntry{rank=%d, user='%s', ticker=%s, change=%.2f%%, eliminated=%b, lives=%d}",
                rank, username, ticker, percentChange, eliminated, livesRemaining);
    }
}
