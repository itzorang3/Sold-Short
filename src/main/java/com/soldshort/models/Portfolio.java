package com.soldshort.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all of a player's DraftPicks within a specific league.
 * Provides a summary of their cumulative performance across rounds.
 */
public class Portfolio {

    private int            userId;
    private int            leagueId;
    private List<DraftPick> picks;

    public Portfolio(int userId, int leagueId) {
        this.userId   = userId;
        this.leagueId = leagueId;
        this.picks    = new ArrayList<>();
    }

    /** Adds a DraftPick to this portfolio. */
    public void addPick(DraftPick pick) {
        picks.add(pick);
    }

    /** Returns every pick recorded for this player in this league. */
    public List<DraftPick> getPicks() {
        return picks;
    }

    /** Retrieves the pick for a specific round, or null if not found. */
    public DraftPick getPickForRound(int round) {
        return picks.stream()
                .filter(p -> p.getRoundNumber() == round)
                .findFirst()
                .orElse(null);
    }

    /** Returns the total number of rounds this player has participated in. */
    public int getRoundsPlayed() {
        return picks.size();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int getUserId()   { return userId; }
    public int getLeagueId() { return leagueId; }

    @Override
    public String toString() {
        return "Portfolio{userId=" + userId + ", leagueId=" + leagueId
                + ", rounds=" + getRoundsPlayed() + "}";
    }
}
