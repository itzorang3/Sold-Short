package com.soldshort.dto;

import java.util.Map;

public class EvaluateRequest {
    /** The user ID of the host submitting evaluation (for auth guard). */
    private int hostId;

    /** ticker → new price entered by the host. */
    private Map<String, Double> prices;

    public int                 getHostId() { return hostId; }
    public Map<String, Double> getPrices() { return prices; }
    public void setHostId(int v)                   { this.hostId  = v; }
    public void setPrices(Map<String, Double> v)   { this.prices  = v; }
}
