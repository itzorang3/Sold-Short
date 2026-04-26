package com.soldshort.controllers;

import com.soldshort.data.DataManager;
import com.soldshort.market.ManualMarketSimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;

import java.math.BigDecimal;
import java.util.*;

/**
 * REST controller for market/stock data.
 *
 * GET /api/market/stocks               — all tickers with current + previous price
 * GET /api/market/price?ticker=X&leagueId=Y&round=N  — persisted price for a round
 * GET /api/market/live-price?ticker=X  — fetch live quote from Yahoo Finance
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final ManualMarketSimulator market;
    private final DataManager           dm = DataManager.getInstance();

    public MarketController(ManualMarketSimulator market) {
        this.market = market;
    }

    /**
     * Returns the full stock list with current and previous prices.
     * Each entry: { ticker, companyName, currentPrice, previousPrice }
     */
    @GetMapping("/stocks")
    public ResponseEntity<List<Map<String, Object>>> getAllStocks() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ticker : market.getAvailableTickers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ticker",        ticker);
            entry.put("companyName",   market.getCompanyName(ticker));
            entry.put("currentPrice",  market.getPrice(ticker));
            entry.put("previousPrice", market.getPreviousPrice(ticker));
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the persisted closing price for a ticker in a specific league round.
     * Used by the client to populate the round-history table on the leaderboard.
     */
    @GetMapping("/price")
    public ResponseEntity<Double> getPersistedPrice(
            @RequestParam String ticker,
            @RequestParam int    leagueId,
            @RequestParam int    round) {
        double price = dm.getStockPrice(ticker, leagueId, round);
        return ResponseEntity.ok(price); // -1.0 if not found
    }

    /**
     * Fetches a real-time quote for a single ticker via Yahoo Finance.
     *
     * Returns 200 + the price as a JSON double on success.
     * Returns 404 if the ticker is unknown or the API is unreachable.
     *
     * The host price-entry screen calls this endpoint when the "↻ Fetch" button
     * is clicked so the host doesn't have to look up prices manually.
     */
    @GetMapping("/live-price")
    public ResponseEntity<Double> getLivePrice(@RequestParam String ticker) {
        try {
            Stock stock = YahooFinance.get(ticker.toUpperCase());
            if (stock == null) return ResponseEntity.notFound().build();

            BigDecimal price = stock.getQuote(false).getPrice();
            if (price == null || price.doubleValue() <= 0)
                return ResponseEntity.notFound().build();

            return ResponseEntity.ok(price.doubleValue());
        } catch (Exception e) {
            System.err.println("MarketController.getLivePrice failed for " + ticker
                    + ": " + e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
