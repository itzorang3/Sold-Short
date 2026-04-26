package com.soldshort.models;

/**
 * Represents a tradable stock with its current and previous prices.
 * Used by the Market layer and the PortfolioEvaluationEngine to compute
 * round-over-round percent change.
 */
public class Stock {

    private String ticker;
    private String companyName;
    private double currentPrice;
    private double previousPrice;

    public Stock() {}

    public Stock(String ticker, String companyName, double currentPrice, double previousPrice) {
        this.ticker        = ticker;
        this.companyName   = companyName;
        this.currentPrice  = currentPrice;
        this.previousPrice = previousPrice;
    }

    /**
     * Returns the percent change from previousPrice to currentPrice.
     * A negative value means the stock fell (good for Sold Short players).
     * A positive value means the stock rose (bad — risks elimination).
     */
    public double getPercentChange() {
        if (previousPrice == 0) return 0.0;
        return ((currentPrice - previousPrice) / previousPrice) * 100.0;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getTicker()        { return ticker; }
    public String getCompanyName()   { return companyName; }
    public double getCurrentPrice()  { return currentPrice; }
    public double getPreviousPrice() { return previousPrice; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setTicker(String t)          { this.ticker        = t; }
    public void setCompanyName(String n)     { this.companyName   = n; }
    public void setCurrentPrice(double p)    { this.currentPrice  = p; }
    public void setPreviousPrice(double p)   { this.previousPrice = p; }

    @Override
    public String toString() {
        return String.format("Stock{%s (%s) %.2f → %.2f | %.2f%%}",
                ticker, companyName, previousPrice, currentPrice, getPercentChange());
    }
}
