package com.soldshort.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soldshort.models.DraftPick;
import com.soldshort.models.League;
import com.soldshort.models.User;
import com.soldshort.observer.LeaderboardEntry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Sold Short REST API.
 *
 * All game-state operations (login, league CRUD, draft picks, evaluation)
 * route through this class instead of calling DataManager or LeagueDraftManager
 * directly.  The server URL is configured once via {@link #configure(String)}.
 *
 * Usage:
 *   // In MainApp.start():
 *   ApiClient.configure("https://your-server.up.railway.app");
 *
 *   // Then anywhere in the UI:
 *   User user = ApiClient.get().login("alice", "password");
 *
 * Design Pattern: Singleton (mirrors the original DataManager pattern).
 */
public class ApiClient {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static ApiClient instance;

    public static ApiClient get() {
        if (instance == null) throw new IllegalStateException("ApiClient not configured yet.");
        return instance;
    }

    public static void configure(String serverUrl) {
        instance = new ApiClient(serverUrl.replaceAll("/$", ""));
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String     baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Stock info helper class ───────────────────────────────────────────────

    /** Returned by getStocks() and getAvailableTickers(). */
    public static class StockInfo {
        public final String ticker;
        public final String companyName;
        public final double currentPrice;
        public final double previousPrice;

        public StockInfo(String ticker, String companyName,
                         double currentPrice, double previousPrice) {
            this.ticker        = ticker;
            this.companyName   = companyName;
            this.currentPrice  = currentPrice;
            this.previousPrice = previousPrice;
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /** Logs in; returns the User on success, null on bad credentials. */
    public User login(String username, String password) {
        String body = json("username", username, "password", password);
        try {
            HttpResponse<String> r = post("/api/auth/login", body);
            if (r.statusCode() != 200) return null;
            return parseUser(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("login", e); return null; }
    }

    /** Registers a new account; returns the User, or null if username taken. */
    public User register(String username, String password, String email) {
        String body = json("username", username, "password", password, "email",
                email == null ? "" : email);
        try {
            HttpResponse<String> r = post("/api/auth/register", body);
            if (r.statusCode() != 200) return null;
            return parseUser(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("register", e); return null; }
    }

    /** Checks whether a username already exists on the server. */
    public boolean usernameExists(String username) {
        try {
            HttpResponse<String> r = get("/api/users?username=" + encode(username));
            return r.statusCode() == 200;
        } catch (Exception e) { logErr("usernameExists", e); return false; }
    }

    /** Resets a user's password; returns true on success. */
    public boolean updatePassword(String username, String newPassword) {
        String body = json("username", username, "newPassword", newPassword);
        try {
            HttpResponse<String> r = post("/api/users/password", body);
            return r.statusCode() == 200;
        } catch (Exception e) { logErr("updatePassword", e); return false; }
    }

    /** Fetches a user by username; returns null if not found. */
    public User getUserByUsername(String username) {
        try {
            HttpResponse<String> r = get("/api/users?username=" + encode(username));
            if (r.statusCode() != 200) return null;
            return parseUser(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getUserByUsername", e); return null; }
    }

    /** Fetches a user by ID; returns null if not found. */
    public User getUserById(int id) {
        try {
            HttpResponse<String> r = get("/api/users/" + id);
            if (r.statusCode() != 200) return null;
            return parseUser(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getUserById", e); return null; }
    }

    // ── Leagues ───────────────────────────────────────────────────────────────

    /** Returns all leagues the user belongs to. */
    public List<League> getLeaguesForUser(int userId) {
        try {
            HttpResponse<String> r = get("/api/leagues?userId=" + userId);
            if (r.statusCode() != 200) return List.of();
            return parseLeagues(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getLeaguesForUser", e); return List.of(); }
    }

    /** Fetches a single league by ID; returns null if not found. */
    public League getLeagueById(int id) {
        try {
            HttpResponse<String> r = get("/api/leagues/" + id);
            if (r.statusCode() != 200) return null;
            return parseLeague(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getLeagueById", e); return null; }
    }

    /** Creates a league; returns the new League or null on failure. */
    public League createLeague(int hostId, String name, int maxPlayers, int startingLives) {
        String body = String.format(
                "{\"hostId\":%d,\"name\":%s,\"maxPlayers\":%d,\"startingLives\":%d}",
                hostId, quoted(name), maxPlayers, startingLives);
        try {
            HttpResponse<String> r = post("/api/leagues", body);
            if (r.statusCode() != 200) return null;
            return parseLeague(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("createLeague", e); return null; }
    }

    /** Joins a league by join code; returns the League or null on failure. */
    public League joinLeague(String joinCode, int userId) {
        String body = String.format("{\"userId\":%d,\"joinCode\":%s}",
                userId, quoted(joinCode));
        // We need the leagueId — look up by code first via a placeholder league endpoint
        // Using leagueId=0 forces the server to match by joinCode in the body
        try {
            // POST to /api/leagues/0/join — server resolves by joinCode field
            HttpResponse<String> r = post("/api/leagues/0/join", body);
            if (r.statusCode() != 200) return null;
            return parseLeague(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("joinLeague", e); return null; }
    }

    /** Removes a league (host only). */
    public boolean removeLeague(int leagueId, int hostId) {
        try {
            HttpResponse<String> r = delete("/api/leagues/" + leagueId + "?hostId=" + hostId);
            return r.statusCode() == 200;
        } catch (Exception e) { logErr("removeLeague", e); return false; }
    }

    /** Leaves a league. */
    public boolean leaveLeague(int leagueId, int userId) {
        String body = "{\"userId\":" + userId + "}";
        try {
            HttpResponse<String> r = post("/api/leagues/" + leagueId + "/leave", body);
            return r.statusCode() == 200;
        } catch (Exception e) { logErr("leaveLeague", e); return false; }
    }

    /** Returns all members of a league. */
    public List<User> getLeagueMembers(int leagueId) {
        try {
            HttpResponse<String> r = get("/api/leagues/" + leagueId + "/members");
            if (r.statusCode() != 200) return List.of();
            return parseUsers(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getLeagueMembers", e); return List.of(); }
    }

    /** Returns only active (non-eliminated) members. */
    public List<User> getActiveMembers(int leagueId) {
        try {
            HttpResponse<String> r = get("/api/leagues/" + leagueId + "/members/active");
            if (r.statusCode() != 200) return List.of();
            return parseUsers(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getActiveMembers", e); return List.of(); }
    }

    // ── Draft ─────────────────────────────────────────────────────────────────

    /** Host starts round 1 draft; returns updated League or null. */
    public League startDraft(int leagueId, int hostId) {
        String body = "{\"userId\":" + hostId + "}";
        try {
            HttpResponse<String> r = post("/api/leagues/" + leagueId + "/draft/start", body);
            if (r.statusCode() != 200) return null;
            return parseLeague(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("startDraft", e); return null; }
    }

    /** Host starts the next round after evaluation; returns updated League or null. */
    public League startNextRound(int leagueId, int hostId) {
        String body = "{\"userId\":" + hostId + "}";
        try {
            HttpResponse<String> r = post("/api/leagues/" + leagueId + "/round/next", body);
            if (r.statusCode() != 200) return null;
            return parseLeague(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("startNextRound", e); return null; }
    }

    /**
     * Submits a player's stock pick.
     * Returns the saved DraftPick, or null if the pick was rejected.
     */
    public DraftPick submitPick(int leagueId, int userId, String ticker) {
        String body = String.format("{\"userId\":%d,\"ticker\":%s}",
                userId, quoted(ticker));
        try {
            HttpResponse<String> r = post("/api/leagues/" + leagueId + "/draft/pick", body);
            if (r.statusCode() != 200) return null;
            return parsePick(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("submitPick", e); return null; }
    }

    /** Returns all picks for a given round. */
    public List<DraftPick> getPicksForRound(int leagueId, int round) {
        try {
            HttpResponse<String> r = get(
                    "/api/leagues/" + leagueId + "/draft/picks?round=" + round);
            if (r.statusCode() != 200) return List.of();
            return parsePicks(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getPicksForRound", e); return List.of(); }
    }

    /** Returns one player's pick for a round, or null. */
    public DraftPick getPickForUser(int leagueId, int userId, int round) {
        try {
            HttpResponse<String> r = get(
                    "/api/leagues/" + leagueId
                    + "/draft/picks/user?userId=" + userId + "&round=" + round);
            if (r.statusCode() != 200) return null;
            return parsePick(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getPickForUser", e); return null; }
    }

    /** Returns all picks across all rounds for a league. */
    public List<DraftPick> getAllPicksForLeague(int leagueId) {
        try {
            HttpResponse<String> r = get("/api/leagues/" + leagueId + "/draft/picks/all");
            if (r.statusCode() != 200) return List.of();
            return parsePicks(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getAllPicksForLeague", e); return List.of(); }
    }

    /** Returns available tickers (with price info) for the current round. */
    public List<StockInfo> getAvailableTickers(int leagueId, int round) {
        try {
            HttpResponse<String> r = get(
                    "/api/leagues/" + leagueId + "/draft/tickers?round=" + round);
            if (r.statusCode() != 200) return List.of();
            return parseStocks(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getAvailableTickers", e); return List.of(); }
    }

    /** Returns true if all active players have submitted picks for the round. */
    public boolean allPicksSubmitted(int leagueId, int round) {
        try {
            HttpResponse<String> r = get(
                    "/api/leagues/" + leagueId + "/draft/all-submitted?round=" + round);
            if (r.statusCode() != 200) return false;
            return Boolean.parseBoolean(r.body());
        } catch (Exception e) { logErr("allPicksSubmitted", e); return false; }
    }

    /** Returns true if prices have been recorded for the round (evaluation done). */
    public boolean isRoundEvaluated(int leagueId, int round) {
        try {
            HttpResponse<String> r = get(
                    "/api/leagues/" + leagueId + "/draft/round-evaluated?round=" + round);
            if (r.statusCode() != 200) return false;
            return Boolean.parseBoolean(r.body());
        } catch (Exception e) { logErr("isRoundEvaluated", e); return false; }
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /**
     * Sends host-entered prices to the server and triggers round evaluation.
     * Returns the ranked leaderboard, or empty list on failure.
     */
    public List<LeaderboardEntry> evaluateRound(int leagueId, int hostId,
                                                Map<String, Double> prices) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"hostId\":").append(hostId).append(",\"prices\":{");
            boolean first = true;
            for (Map.Entry<String, Double> e : prices.entrySet()) {
                if (!first) sb.append(",");
                sb.append(quoted(e.getKey())).append(":").append(e.getValue());
                first = false;
            }
            sb.append("}}");

            HttpResponse<String> r = post("/api/leagues/" + leagueId + "/evaluate",
                    sb.toString());
            if (r.statusCode() != 200) return List.of();
            return parseLeaderboard(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("evaluateRound", e); return List.of(); }
    }

    /** Returns the current leaderboard for a league. */
    public List<LeaderboardEntry> getLeaderboard(int leagueId) {
        try {
            HttpResponse<String> r = get("/api/leagues/" + leagueId + "/leaderboard");
            if (r.statusCode() != 200) return List.of();
            return parseLeaderboard(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getLeaderboard", e); return List.of(); }
    }

    // ── Market ────────────────────────────────────────────────────────────────

    /**
     * Asks the server for a live Yahoo Finance quote for the given ticker.
     *
     * Returns the price on success, or -1.0 if the ticker is unknown,
     * the market is closed, or the server cannot reach Yahoo Finance.
     * Always call off the JavaFX thread.
     */
    public double fetchLivePrice(String ticker) {
        try {
            HttpResponse<String> r = get("/api/market/live-price?ticker=" + encode(ticker));
            if (r.statusCode() != 200) return -1.0;
            return Double.parseDouble(r.body().trim());
        } catch (Exception e) { logErr("fetchLivePrice", e); return -1.0; }
    }

    /** Returns all stocks with current and previous prices. */
    public List<StockInfo> getAllStocks() {
        try {
            HttpResponse<String> r = get("/api/market/stocks");
            if (r.statusCode() != 200) return List.of();
            return parseStocks(mapper.readTree(r.body()));
        } catch (Exception e) { logErr("getAllStocks", e); return List.of(); }
    }

    /**
     * Returns the persisted price for a ticker in a specific league round.
     * Returns -1.0 if not found.
     */
    public double getStockPrice(String ticker, int leagueId, int round) {
        try {
            HttpResponse<String> r = get(
                    "/api/market/price?ticker=" + encode(ticker)
                    + "&leagueId=" + leagueId + "&round=" + round);
            if (r.statusCode() != 200) return -1.0;
            return Double.parseDouble(r.body());
        } catch (Exception e) { logErr("getStockPrice", e); return -1.0; }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    /** Builds a simple flat JSON object from alternating key/value strings. */
    private String json(String... kvPairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kvPairs.length; i += 2) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(kvPairs[i]).append("\":");
            sb.append("\"").append(escape(kvPairs[i + 1])).append("\"");
        }
        return sb.append("}").toString();
    }

    private String quoted(String s) {
        return "\"" + escape(s) + "\"";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void logErr(String method, Exception e) {
        System.err.println("ApiClient." + method + " failed: " + e.getMessage());
    }

    // ── JSON → Model parsers ──────────────────────────────────────────────────

    private User parseUser(JsonNode n) {
        return new User(n.path("id").asInt(), n.path("username").asText(),
                n.path("password").asText(""), n.path("email").asText(null));
    }

    private List<User> parseUsers(JsonNode arr) {
        List<User> list = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> list.add(parseUser(n)));
        return list;
    }

    private League parseLeague(JsonNode n) {
        League l = new League();
        l.setId(n.path("id").asInt());
        l.setName(n.path("name").asText());
        l.setJoinCode(n.path("joinCode").asText());
        l.setHostId(n.path("hostId").asInt());
        l.setMaxPlayers(n.path("maxPlayers").asInt(8));
        l.setCurrentRound(n.path("currentRound").asInt(0));
        l.setStartingLives(n.path("startingLives").asInt(3));
        String statusStr = n.path("status").asText("FORMING");
        try { l.setStatus(League.Status.valueOf(statusStr)); }
        catch (Exception ignored) { l.setStatus(League.Status.FORMING); }
        return l;
    }

    private List<League> parseLeagues(JsonNode arr) {
        List<League> list = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> list.add(parseLeague(n)));
        return list;
    }

    private DraftPick parsePick(JsonNode n) {
        return new DraftPick(n.path("id").asInt(), n.path("leagueId").asInt(),
                n.path("userId").asInt(), n.path("ticker").asText(),
                n.path("roundNumber").asInt());
    }

    private List<DraftPick> parsePicks(JsonNode arr) {
        List<DraftPick> list = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> list.add(parsePick(n)));
        return list;
    }

    private LeaderboardEntry parseEntry(JsonNode n) {
        return new LeaderboardEntry(
                n.path("userId").asInt(),
                n.path("username").asText(),
                n.path("ticker").asText("—"),
                n.path("percentChange").asDouble(0),
                n.path("rank").asInt(0),
                n.path("eliminated").asBoolean(false),
                n.path("eliminationRound").asInt(-1),
                n.path("livesRemaining").asInt(0));
    }

    private List<LeaderboardEntry> parseLeaderboard(JsonNode arr) {
        List<LeaderboardEntry> list = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> list.add(parseEntry(n)));
        return list;
    }

    private StockInfo parseStock(JsonNode n) {
        return new StockInfo(
                n.path("ticker").asText(),
                n.path("companyName").asText(),
                n.path("currentPrice").asDouble(0),
                n.path("previousPrice").asDouble(0));
    }

    private List<StockInfo> parseStocks(JsonNode arr) {
        List<StockInfo> list = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> list.add(parseStock(n)));
        return list;
    }
}
