package com.soldshort.market;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Concrete Strategy: extends ManualMarketSimulator with live Yahoo Finance prices.
 *
 * On construction the seed prices from ManualMarketSimulator are loaded first,
 * then a background daemon thread fetches real-time quotes for every ticker using
 * the YahooFinanceAPI library (com.yahoofinance-api:YahooFinanceAPI:3.17.0).
 *
 * If the fetch fails for any reason (no network, API rate-limit, bad ticker) the
 * seed prices remain unchanged — the app always starts successfully.
 *
 * Design Pattern: Strategy (subtype of ManualMarketSimulator ConcreteStrategy)
 */
public class LiveMarketProvider extends ManualMarketSimulator {

    // ── Constructor ───────────────────────────────────────────────────────────

    public LiveMarketProvider() {
        super();  // loads the 64-ticker seed data immediately
        Thread fetchThread = new Thread(this::fetchLivePrices, "LiveMarketProvider-Fetch");
        fetchThread.setDaemon(true);   // won't prevent JVM shutdown
        fetchThread.start();
    }

    // ── Live Fetch ────────────────────────────────────────────────────────────

    /**
     * Downloads real-time quotes from Yahoo Finance for every ticker in the pool.
     * Runs entirely in a background thread to keep the UI responsive.
     *
     * Successful prices overwrite both current AND previous seed values via
     * {@link ManualMarketSimulator#setSeedPrice} so the first-round % change
     * starts at 0% from the live baseline rather than the hardcoded estimate.
     */
    private void fetchLivePrices() {
        List<String> tickers = getAvailableTickers();
        if (tickers.isEmpty()) return;

        try {
            System.out.println("LiveMarketProvider: fetching live prices for "
                    + tickers.size() + " tickers…");

            // Batch-fetch all tickers in a single HTTP round-trip
            String[] tickerArray = tickers.toArray(new String[0]);
            Map<String, Stock> stocks = YahooFinance.get(tickerArray);

            int updated = 0;
            int failed  = 0;

            for (String ticker : tickers) {
                Stock s = stocks.get(ticker);
                if (s == null) {
                    failed++;
                    continue;
                }
                try {
                    BigDecimal price = s.getQuote(false).getPrice();
                    if (price != null && price.doubleValue() > 0) {
                        setSeedPrice(ticker, price.doubleValue());
                        updated++;
                    } else {
                        failed++;
                    }
                } catch (Exception inner) {
                    failed++;
                    // Individual ticker failure — keep seed price, continue loop
                }
            }

            System.out.printf("LiveMarketProvider: updated %d / %d tickers  (%d kept seed price)%n",
                    updated, tickers.size(), failed);

        } catch (Exception e) {
            // Network error, API change, firewall block, etc.
            System.err.println("LiveMarketProvider: live fetch failed — using seed prices. ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }
}
