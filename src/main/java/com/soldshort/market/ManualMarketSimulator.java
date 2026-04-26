package com.soldshort.market;

import java.util.*;

/**
 * Concrete Strategy: prices are entered manually by the league host.
 *
 * Pre-loads 64 well-known stock tickers with realistic seed prices across
 * technology, finance, healthcare, energy, consumer, and industrial sectors.
 * The host uses the HostControlScreen to update prices each round.
 * Previous prices are snapshotted at round boundaries via advanceRound().
 *
 * Design Pattern: Strategy
 * Role: ConcreteStrategy — replaces live API data with host-driven input,
 *       making Milestone 3 self-contained without external dependencies.
 *
 * This class is extended by LiveMarketProvider, which refreshes prices from
 * Yahoo Finance on startup.  The protected maps and setSeedPrice() helper
 * allow the subclass to update both current and previous prices atomically.
 */
public class ManualMarketSimulator implements MarketDataProvider {

    /** Ticker → current price (this round) */
    protected final Map<String, Double> currentPrices  = new LinkedHashMap<>();

    /** Ticker → previous price (prior round — used for % change) */
    protected final Map<String, Double> previousPrices = new LinkedHashMap<>();

    /** Ticker → human-readable company name */
    protected final Map<String, String> companyNames   = new LinkedHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public ManualMarketSimulator() {
        seedData();
    }

    /**
     * Populates the 64 pre-loaded tickers with seed prices.
     * Both current and previous start at the same value so the first
     * % change is 0% until the host enters actual prices (or LiveMarketProvider
     * refreshes them from Yahoo Finance).
     *
     * Sectors covered:
     *   Technology (15), Finance (10), Consumer (9), Industrial/Auto (5),
     *   Healthcare (7), Energy (5), Media/Telecom (5), Additional Tech (4),
     *   Additional Finance (4)
     */
    private void seedData() {
        Object[][] data = {
            // { ticker, companyName, seedPrice }
            // Seed prices — April 2026 estimates (updated live by LiveMarketProvider)

            // ── Technology ─────────────────────────────────────────────────────
            { "AAPL",  "Apple Inc.",                   260.48 },
            { "MSFT",  "Microsoft Corp.",              374.33 },
            { "GOOGL", "Alphabet Inc.",                317.24 },
            { "AMZN",  "Amazon.com Inc.",              238.38 },
            { "META",  "Meta Platforms Inc.",          629.86 },
            { "NVDA",  "NVIDIA Corp.",                 188.63 },
            { "AMD",   "Advanced Micro Devices",       231.82 },
            { "INTC",  "Intel Corp.",                   58.95 },
            { "ORCL",  "Oracle Corp.",                 143.66 },
            { "IBM",   "IBM Corp.",                    241.74 },
            { "CSCO",  "Cisco Systems Inc.",            83.70 },
            { "QCOM",  "Qualcomm Inc.",                127.51 },
            { "AVGO",  "Broadcom Inc.",                350.63 },
            { "TXN",   "Texas Instruments Inc.",       208.90 },
            { "TSLA",  "Tesla Inc.",                   348.95 },

            // ── Finance ────────────────────────────────────────────────────────
            { "JPM",   "JPMorgan Chase & Co.",         307.97 },
            { "BAC",   "Bank of America Corp.",         51.88 },
            { "GS",    "Goldman Sachs Group Inc.",     905.75 },
            { "WFC",   "Wells Fargo & Co.",             84.66 },
            { "C",     "Citigroup Inc.",               123.49 },
            { "V",     "Visa Inc.",                    308.96 },
            { "MA",    "Mastercard Inc.",              507.12 },
            { "PYPL",  "PayPal Holdings Inc.",          45.85 },
            { "AXP",   "American Express Co.",         252.80 },
            { "MS",    "Morgan Stanley",               109.30 },

            // ── Consumer / Retail ──────────────────────────────────────────────
            { "WMT",   "Walmart Inc.",                 127.26 },
            { "TGT",   "Target Corp.",                 123.12 },
            { "KO",    "The Coca-Cola Co.",             77.29 },
            { "PEP",   "PepsiCo Inc.",                 154.80 },
            { "MCD",   "McDonald's Corp.",             307.01 },
            { "SBUX",  "Starbucks Corp.",               97.21 },
            { "NKE",   "Nike Inc.",                     43.13 },
            { "COST",  "Costco Wholesale Corp.",        927.50 },
            { "HD",    "The Home Depot Inc.",          338.40 },

            // ── Industrial / Auto ──────────────────────────────────────────────
            { "BA",    "Boeing Co.",                   217.80 },
            { "CAT",   "Caterpillar Inc.",             771.58 },
            { "GE",    "GE Aerospace",                 308.06 },
            { "F",     "Ford Motor Co.",                12.18 },
            { "GM",    "General Motors Co.",            76.74 },

            // ── Healthcare ─────────────────────────────────────────────────────
            { "JNJ",   "Johnson & Johnson",            148.50 },
            { "PFE",   "Pfizer Inc.",                   23.80 },
            { "MRK",   "Merck & Co. Inc.",              83.20 },
            { "UNH",   "UnitedHealth Group Inc.",      486.50 },
            { "ABBV",  "AbbVie Inc.",                  168.90 },
            { "LLY",   "Eli Lilly and Co.",            736.40 },
            { "TMO",   "Thermo Fisher Scientific",     407.20 },

            // ── Energy ────────────────────────────────────────────────────────
            { "XOM",   "Exxon Mobil Corp.",            108.30 },
            { "CVX",   "Chevron Corp.",                140.50 },
            { "COP",   "ConocoPhillips",                93.80 },
            { "OXY",   "Occidental Petroleum Corp.",    43.20 },
            { "SLB",   "SLB (Schlumberger)",            36.40 },

            // ── Media / Telecom ────────────────────────────────────────────────
            { "NFLX",  "Netflix Inc.",                 103.01 },  // post 10:1 split (Nov 2025)
            { "DIS",   "The Walt Disney Co.",           99.18 },
            { "CMCSA", "Comcast Corp.",                 27.96 },
            { "T",     "AT&T Inc.",                    27.35  },
            { "VZ",    "Verizon Communications Inc.",   48.04 },

            // ── Emerging / High-Growth Tech ────────────────────────────────────
            { "UBER",  "Uber Technologies Inc.",        66.30 },
            { "SPOT",  "Spotify Technology S.A.",      527.80 },
            { "PLTR",  "Palantir Technologies Inc.",    82.40 },
            { "COIN",  "Coinbase Global Inc.",         181.60 },

            // ── Asset Management / Brokerage ──────────────────────────────────
            { "BLK",   "BlackRock Inc.",               851.40 },
            { "SCHW",  "Charles Schwab Corp.",          68.90 },
            { "DE",    "Deere & Company",              386.70 },
            { "LOW",   "Lowe's Companies Inc.",        227.30 }
        };

        for (Object[] row : data) {
            String ticker = (String) row[0];
            String name   = (String) row[1];
            double price  = (Double) row[2];
            companyNames.put(ticker, name);
            currentPrices.put(ticker, price);
            previousPrices.put(ticker, price);  // same seed — first change will be 0%
        }
    }

    /**
     * Updates BOTH the current and previous price for a ticker atomically.
     * Used by LiveMarketProvider to apply a fresh live price as the new baseline
     * so that % change starts at 0% until the host actually enters round prices.
     */
    protected void setSeedPrice(String ticker, double price) {
        if (companyNames.containsKey(ticker)) {
            currentPrices.put(ticker, price);
            previousPrices.put(ticker, price);
        }
    }

    // ── MarketDataProvider implementation ─────────────────────────────────────

    @Override
    public double getPrice(String ticker) {
        return currentPrices.getOrDefault(ticker, -1.0);
    }

    @Override
    public double getPreviousPrice(String ticker) {
        return previousPrices.getOrDefault(ticker, -1.0);
    }

    @Override
    public void setPrice(String ticker, double price) {
        if (currentPrices.containsKey(ticker)) {
            currentPrices.put(ticker, price);
        } else {
            System.err.println("ManualMarketSimulator: unknown ticker '" + ticker + "'");
        }
    }

    /**
     * Snapshots current prices into the previous-prices map.
     * Call this at the start of each new round, before the host enters new prices.
     */
    @Override
    public void advanceRound() {
        previousPrices.putAll(currentPrices);
    }

    @Override
    public List<String> getAvailableTickers() {
        return Collections.unmodifiableList(new ArrayList<>(currentPrices.keySet()));
    }

    @Override
    public Map<String, Double> getAllPrices() {
        return Collections.unmodifiableMap(currentPrices);
    }

    // ── Additional Helpers ────────────────────────────────────────────────────

    /** Returns the company name for a ticker, or the ticker itself if unknown. */
    public String getCompanyName(String ticker) {
        return companyNames.getOrDefault(ticker, ticker);
    }

    /**
     * Returns percent change from previous to current price for a ticker.
     * Delegates to Stock.getPercentChange() arithmetic.
     */
    public double getPercentChange(String ticker) {
        double cur  = getPrice(ticker);
        double prev = getPreviousPrice(ticker);
        if (prev <= 0) return 0.0;
        return ((cur - prev) / prev) * 100.0;
    }

    /** Returns all available tickers paired with their company names. */
    public Map<String, String> getCompanyNames() {
        return Collections.unmodifiableMap(companyNames);
    }
}
