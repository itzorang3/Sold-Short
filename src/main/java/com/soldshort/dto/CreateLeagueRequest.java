package com.soldshort.dto;

public class CreateLeagueRequest {
    private int    hostId;
    private String name;
    private int    maxPlayers;
    private int    startingLives;

    public int    getHostId()        { return hostId; }
    public String getName()          { return name; }
    public int    getMaxPlayers()    { return maxPlayers; }
    public int    getStartingLives() { return startingLives; }
    public void setHostId(int v)        { this.hostId        = v; }
    public void setName(String v)       { this.name          = v; }
    public void setMaxPlayers(int v)    { this.maxPlayers    = v; }
    public void setStartingLives(int v) { this.startingLives = v; }
}
