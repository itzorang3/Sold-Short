package com.soldshort.market;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for any market data source used in Sold Short.
 *
 * Design Pattern: Strategy (interface)
 * Role: Strategy — different concrete implementations provide prices
 *       through different mechanisms (manual host entry, live API, etc.)
 *       without changing how the rest of the application consumes data.
 *
 * Concrete implementations:
 *   - ManualMarketSimulator : host enters prices manually (Milestone 3)
 *   - ChartingAPIProvider   : stub for future live API integration
 */
public interface MarketDataProvider {

    /**
     * Returns the current simulated or fetched price for a single ticker.
     *
     * @param ticker the stock symbol (e.g. "AAPL")
     * @return current price in USD, or -1.0 if the ticker is not available
     */
    double getPrice(String ticker);

    /**
     * Returns the price recorded in the previous round for a given ticker.
     * Used by the evaluation engine to compute percent change.
     *
     * @param ticker the stock symbol
     * @return previous price in USD, or -1.0 if unavailable
     */
    double getPreviousPrice(String ticker);

    /**
     * Updates the stored price for a ticker.
     * Called by the host control panel when entering new round prices.
     *
     * @param ticker the stock symbol
     * @param price  the new price in USD
     */
    void setPrice(String ticker, double price);

    /**
     * Snapshots current prices as the "previous" prices for the next round.
     * Should be called at the start of each new round.
     */
    void advanceRound();

    /**
     * Returns all ticker symbols available in this provider.
     *
     * @return unmodifiable list of ticker strings
     */
    List<String> getAvailableTickers();

    /**
     * Returns prices for every ticker in one call.
     *
     * @return map of ticker → current price
     */
    Map<String, Double> getAllPrices();
}
