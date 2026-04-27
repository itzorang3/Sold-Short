package com.soldshort.models;

/**
 * Records one player's stock selection for a specific round within a league.
 * A single league round generates one DraftPick per active player.
 */
public class DraftPick {

    private int    id;
    private int    leagueId;
    private int    userId;
    private String ticker;
    private int    roundNumber;

    public DraftPick() {}

    public DraftPick(int id, int leagueId, int userId, String ticker, int roundNumber) {
        this.id          = id;
        this.leagueId    = leagueId;
        this.userId      = userId;
        this.ticker      = ticker;
        this.roundNumber = roundNumber;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int    getId()          { return id; }
    public int    getLeagueId()    { return leagueId; }
    public int    getUserId()      { return userId; }
    public String getTicker()      { return ticker; }
    public int    getRoundNumber() { return roundNumber; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(int id)               { this.id          = id; }
    public void setLeagueId(int leagueId)   { this.leagueId    = leagueId; }
    public void setUserId(int userId)       { this.userId      = userId; }
    public void setTicker(String ticker)    { this.ticker      = ticker; }
    public void setRoundNumber(int round)   { this.roundNumber = round; }

    @Override
    public String toString() {
        return "DraftPick{league=" + leagueId + ", user=" + userId
                + ", ticker='" + ticker + "', round=" + roundNumber + "}";
    }
}
