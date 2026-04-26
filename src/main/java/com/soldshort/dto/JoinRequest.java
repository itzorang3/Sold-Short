package com.soldshort.dto;

public class JoinRequest {
    private int    userId;
    private String joinCode; // used only for join-by-code endpoint

    public int    getUserId()  { return userId; }
    public String getJoinCode() { return joinCode; }
    public void setUserId(int v)     { this.userId   = v; }
    public void setJoinCode(String v) { this.joinCode = v; }
}
