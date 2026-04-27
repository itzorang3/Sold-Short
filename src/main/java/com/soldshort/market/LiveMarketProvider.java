package com.soldshort.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete Strategy: extends ManualMarketSimulator with live Yahoo Finance prices.
 *
 * On construction this class immediately fetches real-time prices for every
 * ticker via Yahoo Finance's v8 REST API, BLOCKING until the fetch completes
 * (or times out).  This means the server only becomes ready to accept requests
 * once real prices are loaded — no window of stale hardcoded values.
 *
 * If the fetch fails for any reason (no network, rate-limit, bad ticker) the
 * hardcoded seed prices in ManualMarketSimulator are kept as a fallback, so
 * the server always starts successfully.
 *
 * Fetches in batches of {@value #BATCH_SIZE} with a {@value #BATCH_DELAY_MS} ms
 * pause between batches to stay within Yahoo Finance's rate limit.
 *
 * Design Pattern: Strategy (ConcreteStrategy extending ManualMarketSimulator)
 */
public class LiveMarketProvider extends ManualMarketSimulator {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Yahoo Finance v8 chart API — one call per ticker, no auth required. */
    private static final String YF_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d";

    /** Fallback URL if query1 is rate-limited. */
    private static final String YF_URL_ALT =
            "https://query2.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d";

    /** Tickers per batch — prevents 429s on the v8 endpoint. */
    private static final int BATCH_SIZE = 10;

    /** Pause between batches in milliseconds. */
    private static final long BATCH_DELAY_MS = 1_200;

    /** Per-request timeout. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    /** User-Agent header — bare Java clients get blocked without one. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Loads seed prices from the superclass, then immediately performs a
     * blocking fetch of all tickers from Yahoo Finance before returning.
     * The Spring context will not finish starting up until this constructor
     * completes, so the server only accepts requests once real prices are ready.
     */
    public LiveMarketProvider() {
        super();          // load hardcoded seed prices as immediate fallback
        fetchAllPrices(); // BLOCKING — overwrites seeds with live prices
    }

    // ── Fetch Logic ───────────────────────────────────────────────────────────

    private void fetchAllPrices() {
        List<String> tickers = getAvailableTickers();
        if (tickers.isEmpty()) return;

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        ObjectMapper json = new ObjectMapper();
        int updated = 0;
        int failed  = 0;

        System.out.printf(
                "LiveMarketProvider: fetching %d tickers from Yahoo Finance v8 API "
                + "in batches of %d (this blocks startup — ~%d s)…%n",
                tickers.size(), BATCH_SIZE,
                (tickers.size() / BATCH_SIZE) * (int)(BATCH_DELAY_MS / 1000 + 1));

        List<List<String>> batches = partition(tickers, BATCH_SIZE);
        for (int b = 0; b < batches.size(); b++) {
            List<String> batch = batches.get(b);

            for (String ticker : batch) {
                Double price = fetchPrice(http, json, ticker, false);
                if (price == null) {
                    // Retry once on the alternate Yahoo host
                    price = fetchPrice(http, json, ticker, true);
                }
                if (price != null) {
                    setSeedPrice(ticker, price);
                    updated++;
                } else {
                    failed++;
                }
            }

            // Pause between batches — skip after the last one
            if (b < batches.size() - 1) {
                try { Thread.sleep(BATCH_DELAY_MS); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        System.out.printf(
                "LiveMarketProvider: ready — updated %d / %d tickers  "
                + "(%d kept seed price)%n",
                updated, tickers.size(), failed);
    }

    /**
     * Fetches the latest closing price for a single ticker from Yahoo Finance v8.
     *
     * @param useAlt  true → use query2.finance.yahoo.com (retry host)
     * @return the price, or null if unavailable
     */
    private Double fetchPrice(HttpClient http, ObjectMapper json,
                               String ticker, boolean useAlt) {
        String url = String.format(useAlt ? YF_URL_ALT : YF_URL, ticker);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 429) {
                System.err.printf("LiveMarketProvider: 429 rate-limit on %s%s%n",
                        ticker, useAlt ? " (alt host)" : "");
                return null;
            }
            if (resp.statusCode() != 200) {
                System.err.printf("LiveMarketProvider: HTTP %d for %s%n",
                        resp.statusCode(), ticker);
                return null;
            }

            // Parse:  chart.result[0].meta.regularMarketPrice
            JsonNode root      = json.readTree(resp.body());
            JsonNode meta      = root.path("chart").path("result").path(0).path("meta");
            JsonNode priceNode = meta.path("regularMarketPrice");

            if (priceNode.isMissingNode() || priceNode.isNull()) return null;
            double price = priceNode.asDouble();
            return price > 0 ? price : null;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.err.printf("LiveMarketProvider: error fetching %s — %s: %s%n",
                    ticker, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }
}
