package com.soldshort.dto;

public class MakePickRequest {
    private int    userId;
    private String ticker;

    public int    getUserId() { return userId; }
    public String getTicker() { return ticker; }
    public void setUserId(int v)    { this.userId = v; }
    public void setTicker(String v) { this.ticker = v; }
}
