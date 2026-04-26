package com.soldshort.models;

/**
 * Represents a Sold Short tournament league.
 *
 * Lifecycle:  FORMING → DRAFTING → ACTIVE → FINISHED
 *   FORMING  : League created, waiting for players to join.
 *   DRAFTING : Players are selecting their stocks for the current round.
 *   ACTIVE   : Prices have been entered; evaluation is running / round complete.
 *   FINISHED : One player remains — they are declared the winner.
 */
public class League {

    /** Possible states the league can occupy during its lifecycle. */
    public enum Status {
        FORMING, DRAFTING, ACTIVE, FINISHED
    }

    private int    id;
    private String name;
    private String joinCode;
    private int    hostId;
    private int    maxPlayers;
    private Status status;
    private int    currentRound;
    private int    startingLives;   // lives each player starts with (default 3)

    public League() {
        this.status        = Status.FORMING;
        this.currentRound  = 0;
        this.maxPlayers    = 8;
        this.startingLives = 3;
    }

    public League(int id, String name, String joinCode, int hostId,
                  int maxPlayers, Status status, int currentRound) {
        this(id, name, joinCode, hostId, maxPlayers, status, currentRound, 3);
    }

    public League(int id, String name, String joinCode, int hostId,
                  int maxPlayers, Status status, int currentRound, int startingLives) {
        this.id            = id;
        this.name          = name;
        this.joinCode      = joinCode;
        this.hostId        = hostId;
        this.maxPlayers    = maxPlayers;
        this.status        = status;
        this.currentRound  = currentRound;
        this.startingLives = startingLives;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int    getId()             { return id; }
    public String getName()           { return name; }
    public String getJoinCode()       { return joinCode; }
    public int    getHostId()         { return hostId; }
    public int    getMaxPlayers()     { return maxPlayers; }
    public Status getStatus()         { return status; }
    public int    getCurrentRound()   { return currentRound; }
    public int    getStartingLives()  { return startingLives; }

    public boolean isHostedBy(int userId) { return hostId == userId; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(int id)                     { this.id            = id; }
    public void setName(String name)              { this.name          = name; }
    public void setJoinCode(String joinCode)      { this.joinCode      = joinCode; }
    public void setHostId(int hostId)             { this.hostId        = hostId; }
    public void setMaxPlayers(int maxPlayers)     { this.maxPlayers    = maxPlayers; }
    public void setStatus(Status status)          { this.status        = status; }
    public void setCurrentRound(int round)        { this.currentRound  = round; }
    public void setStartingLives(int lives)       { this.startingLives = lives; }

    @Override
    public String toString() {
        return "League{id=" + id + ", name='" + name + "', code='" + joinCode
                + "', status=" + status + ", round=" + currentRound
                + ", lives=" + startingLives + "}";
    }
}
