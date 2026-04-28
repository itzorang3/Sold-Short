# Sold Short — Technical Reference Guide

**Team The Big Shorts · CSC 3380 · Spring 2026**

---

## 1. What Is Sold Short?

Sold Short is a multiplayer fantasy-sports-style tournament where players draft stocks they believe will *fall* the most each week. The player whose stock performs worst as a short (i.e., it went *up* the most) loses a life each round. The last player with lives remaining wins.

---

## 2. Tech Stack — Full Breakdown

| Technology | Version | Role |
|---|---|---|
| Java | 17 | Core application language (both client and server) |
| JavaFX | 17.0.6 | Client GUI framework |
| Spring Boot | 3.x | REST API server |
| Jackson | 2.17.1 | JSON serialization/deserialization |
| SQLite + Xerial JDBC | — | Server-side database |
| Maven | — | Build system, dependency management |
| Railway | — | Cloud hosting for the Spring Boot server + SQLite DB |
| Yahoo Finance API (v8) | — | Live stock price data |

**Why Java 17?** It's the course language and provides strong OOP features. Java 17 is an LTS release with modern syntax like text blocks (used in the DDL statements) and switch expressions (used in `MainMenuScreen`).

**Why JavaFX instead of Swing?** JavaFX is the modern Java GUI toolkit with CSS styling support, scene graph architecture, and better layout containers. The `javafx-maven-plugin` lets us run with `mvn javafx:run`.

**Why Spring Boot for the server?** It provides auto-configured REST endpoints with minimal boilerplate. The `@RestController` annotation wires HTTP routes directly to methods. Jackson handles JSON automatically.

**Why SQLite?** Lightweight, file-based, no separate DB server to run. The database file (`soldshort.db`) lives on Railway alongside the server JAR. Xerial's JDBC driver (`sqlite-jdbc`) lets Java talk to it directly.

**Why Railway?** Free-tier cloud deployment that supports JAR-based Java apps. It reads the `Procfile` or Maven config to start the Spring Boot server. Railway assigns a persistent URL that the client reads from the `SERVER_URL` environment variable.

---

## 3. Project Structure

```
CSC3380 Project - Sold Short/       ← Client repo (main branch)
├── src/main/java/com/soldshort/
│   ├── Launcher.java               ← Fat-JAR entry point
│   ├── api/
│   │   └── ApiClient.java          ← HTTP singleton (all server calls)
│   ├── models/
│   │   ├── User.java
│   │   ├── League.java
│   │   └── DraftPick.java
│   ├── observer/
│   │   └── LeaderboardEntry.java   ← Immutable DTO from evaluation
│   └── ui/
│       ├── MainApp.java            ← JavaFX Application + navigation + overlay
│       ├── LoginScreen.java
│       ├── MainMenuScreen.java
│       ├── DraftScreen.java
│       ├── HostControlScreen.java
│       └── LeaderboardScreen.java
├── pom.xml                         ← Maven build (javafx-maven-plugin + maven-shade)
└── server/                         ← Server repo (master branch, Railway)
    └── src/main/java/com/soldshort/
        ├── ServerApp.java
        ├── config/AppConfig.java
        ├── controllers/
        │   ├── AuthController.java
        │   ├── LeagueController.java
        │   └── MarketController.java
        ├── data/
        │   ├── DataManager.java    ← Singleton DB facade
        │   └── DatabaseConnection.java
        ├── draft/
        │   ├── Draft.java
        │   └── LeagueDraftManager.java
        ├── dto/                    ← Request body POJOs
        ├── engine/
        │   └── PortfolioEvaluationEngine.java
        ├── market/
        │   ├── MarketDataProvider.java   ← Strategy interface
        │   ├── ManualMarketSimulator.java ← ConcreteStrategy (seed prices)
        │   └── LiveMarketProvider.java   ← ConcreteStrategy (Yahoo Finance)
        ├── models/                 ← Server-side domain models
        └── observer/
            ├── PlayerObserver.java ← Observer interface
            └── LeaderboardEntry.java
```

---

## 4. Client Architecture

### 4.1 Entry Points — Launcher vs. MainApp

There are two entry point classes:

- **`MainApp`** extends `javafx.application.Application` — the real JavaFX entry point, registered with the `javafx-maven-plugin` for `mvn javafx:run`.
- **`Launcher`** is a plain Java class that just calls `MainApp.main()`. This is needed for the **fat JAR** because the JVM can't directly launch a class that extends `Application` from a shaded JAR. The `maven-shade-plugin` sets `Launcher` as the JAR's `Main-Class`.

### 4.2 MainApp — Scene Architecture

`MainApp` manages the entire window lifecycle with a **persistent two-layer StackPane**:

```
Stage → Scene → StackPane (rootStack)
                 ├── contentPane   (current full-screen view)
                 └── overlayLayer  (modal dialogs rendered IN-WINDOW)
```

**Why this design?** Standard OS dialogs (`Alert`, `Stage`) are separate windows and feel clunky. Instead, all dialogs (alerts, confirmations, forms) are rendered as cards centered over a semi-transparent black overlay (`rgba(0,0,0,0.62)`) — all within the same scene. This is what we mean by "in-app overlay UI."

Navigation works by calling static methods like `MainApp.showDraftScreen(league)`, which calls `setContent()` to swap the `contentPane`'s children. The `overlayLayer` is hidden (invisible + mouse-transparent) between dialogs.

### 4.3 Screen Navigation Flow

```
LoginScreen
    ↓ (on login/register)
MainMenuScreen
    ↓ (host: Start Draft)           ↓ (player: Pick Stock)
HostControlScreen              DraftScreen
    ↓ (after evaluation)            ↓ (all picks in)
LeaderboardScreen ←────────────────┘
```

Each screen class has a single `build()` method that returns a `Pane`. That pane is handed to `setContent()` in `MainApp`.

### 4.4 Threading Model

All HTTP calls are run on **background threads** using `new Thread(() -> { ... }).start()`. When the result comes back, `Platform.runLater()` is used to update UI components on the JavaFX Application Thread. This is mandatory — JavaFX will throw exceptions if you touch UI nodes from a non-JavaFX thread.

Example pattern used throughout the codebase:
```java
new Thread(() -> {
    User user = ApiClient.get().login(username, password);
    Platform.runLater(() -> {
        if (user == null) { /* show error */ }
        else { MainApp.setCurrentUser(user); MainApp.showMainMenu(); }
    });
}).start();
```

`DraftScreen` also uses a **JavaFX `Timeline`** for polling — it fires a background check every 4 seconds to detect when all players have submitted picks or when the round advances.

### 4.5 ApiClient — The HTTP Singleton

`ApiClient` is the **Singleton pattern** on the client side. It is configured once in `MainApp.start()` with `ApiClient.configure(SERVER_URL)`, then accessed everywhere via `ApiClient.get()`.

Internally it uses **Java 11's built-in `HttpClient`** (no third-party HTTP library needed). It builds requests manually, sends them synchronously (blocking the background thread), and parses JSON responses using Jackson's `ObjectMapper`.

The server URL is read from an environment variable: `System.getenv().getOrDefault("SERVER_URL", "http://localhost:8080")`. This means the same client binary works locally (pointing to localhost) and in production (pointing to Railway).

---

## 5. Server Architecture

### 5.1 Spring Boot Controllers

Three REST controllers handle all API traffic:

**`AuthController`** (`/api/auth/`, `/api/users/`)
- `POST /api/auth/login` — verifies credentials, returns User JSON
- `POST /api/auth/register` — creates account, returns User JSON
- `GET  /api/users?username=` — checks if username exists
- `POST /api/users/password` — resets password

**`LeagueController`** (`/api/leagues/`)
- Full CRUD for leagues: create, get by ID, get by user, delete
- Join/leave: `POST /api/leagues/0/join` (server resolves by joinCode), `POST /{id}/leave`
- Draft flow: `POST /{id}/draft/start`, `POST /{id}/draft/pick`, `POST /{id}/round/next`
- Picks: `GET /{id}/draft/picks?round=`, `GET /{id}/draft/picks/all`
- Availability checks: `GET /{id}/draft/all-submitted`, `GET /{id}/draft/round-evaluated`
- Evaluation: `POST /{id}/evaluate` — triggers `PortfolioEvaluationEngine`
- Leaderboard: `GET /{id}/leaderboard`

**`MarketController`** (`/api/market/`)
- `GET /api/market/stocks` — all tickers with prices
- `GET /api/market/live-price?ticker=` — single live Yahoo Finance quote on demand
- `GET /api/market/price?ticker=&leagueId=&round=` — persisted historical price

### 5.2 AppConfig — Spring Wiring

`AppConfig` is the Spring `@Configuration` class. It instantiates `LiveMarketProvider` as a `@Bean`, which Spring injects into the controllers. This is where the market strategy is selected — swap `LiveMarketProvider` for `ManualMarketSimulator` in this one file to switch price sources.

### 5.3 DataManager — The DB Singleton

`DataManager` is the **Singleton pattern** on the server. `getInstance()` is `synchronized` for thread safety. It holds all SQL logic — every DB operation in the app routes through this class.

**Why a single class for all SQL?** It keeps the persistence layer in one place and ensures only one `Connection` object is used, which is important for SQLite (which has write locking).

The schema is initialized in `initializeDatabase()` using `CREATE TABLE IF NOT EXISTS`, so it's safe to run on every startup. Migration code adds new columns (like `starting_lives`) to existing databases that were created before those columns existed.

### 5.4 Database Schema

Five tables in SQLite:

```sql
users           (id, username UNIQUE, password, email)
leagues         (id, name, join_code UNIQUE, host_id, max_players, status, current_round, starting_lives)
league_members  (league_id, user_id, is_eliminated, elimination_round, lives)  ← composite PK
draft_picks     (id, league_id, user_id, ticker, round_number)
stock_prices    (id, ticker, price, league_id, round_number, recorded_at)
```

`league_members` has a **composite primary key** `(league_id, user_id)` — one row per player per league, tracking their lives and elimination status. This is how the lives system persists.

`stock_prices` records closing prices per ticker per round per league. This means the evaluation engine can reconstruct results even after a restart.

---

## 6. OOP Design Patterns

### 6.1 Strategy Pattern — `MarketDataProvider`

**What it is:** Defines a family of interchangeable algorithms behind a common interface.

**Interface:** `MarketDataProvider` declares `getPrice()`, `getPreviousPrice()`, `setPrice()`, `advanceRound()`, `getAvailableTickers()`, `getAllPrices()`.

**Concrete strategies:**
- `ManualMarketSimulator` — pre-loads 64 tickers with April 2026 seed prices. The host enters actual prices via the UI. Previous prices are snapshotted via `advanceRound()`.
- `LiveMarketProvider` — extends `ManualMarketSimulator`, then overrides the seed prices by fetching live data from Yahoo Finance in its constructor. It calls `fetchAllPrices()` BLOCKING before the Spring context finishes starting, so the server only accepts requests once real prices are loaded.

**Where the swap happens:** `AppConfig.java`. Change one line from `new LiveMarketProvider()` to `new ManualMarketSimulator()` and no other file changes.

**Why it matters for the course:** `PortfolioEvaluationEngine.evaluateRound()` takes a `MarketDataProvider market` parameter — it calls `market.getPrice()` and `market.getPreviousPrice()` without knowing or caring which implementation it got. This is the textbook definition of the Strategy pattern.

### 6.2 Observer Pattern — `PlayerObserver`

**What it is:** Defines a one-to-many dependency so that when one object changes state, all its dependents are notified automatically.

**Subject:** `PortfolioEvaluationEngine` maintains a `List<PlayerObserver> observers`. Any class can call `addObserver()` to subscribe.

**Observer interface:** `PlayerObserver` declares two methods:
- `onPlayerEliminated(userId, leagueId, round, ticker)` — fired when a player's lives reach 0
- `onRoundComplete(leagueId, round, standings)` — fired after every evaluation with the ranked leaderboard

**How it fires:** At the end of `evaluateRound()`, the engine loops through all registered observers and calls both methods. The UI layer registers as an observer during the host control flow and auto-updates the leaderboard without polling.

**Why it matters:** The evaluation engine and the UI have zero direct coupling. The engine doesn't import any JavaFX class, and the UI doesn't contain any game-logic code.

### 6.3 Singleton Pattern — `DataManager` and `ApiClient`

**What it is:** Ensures a class has exactly one instance and provides a global access point to it.

**`DataManager.getInstance()`** — `synchronized` method, server side. Guarantees one shared SQLite connection across all controllers and all request threads. SQLite has write locking, so one shared connection is both safer and simpler.

**`ApiClient.get()`** — client side. Configured once with the server URL, then reused for every HTTP call across all screens. Holds one shared `HttpClient` instance.

**Why `synchronized` on `DataManager` but not `ApiClient`?** The server handles concurrent HTTP requests from multiple clients. `DataManager.getInstance()` could be called by two threads simultaneously during startup if Spring wires beans concurrently, so synchronization is required. `ApiClient` is configured from the JavaFX Application Thread (single-threaded at startup) before any background threads run, so no synchronization is needed.

---

## 7. Core Algorithms

### 7.1 Round Evaluation (PortfolioEvaluationEngine)

Step by step:

1. Fetch all **active players** (not yet eliminated) for the league.
2. Fetch all **draft picks** for the current round.
3. For each player, compute `percentChange = ((current - previous) / previous) × 100`.
4. **Sort ascending** — the player with the *lowest* percent change (stock fell the most) is the best short-seller (rank 1). The player with the *highest* percent change (stock rose the most) is the worst.
5. **Tie check** — if the two bottom players have the same percent change (within 0.0001 tolerance), it's a tied round and no one loses a life.
6. Otherwise, call `dataManager.deductLife(leagueId, worstUserId, round)`. If `livesLeft == 0`, the player is fully eliminated.
7. Check if only one (or zero) active members remain — if so, set league status to `FINISHED`.
8. Fire `onPlayerEliminated()` and `onRoundComplete()` to all registered observers.
9. Return the ranked `List<LeaderboardEntry>`.

### 7.2 Join Code Generation (LeagueDraftManager)

Uses `SecureRandom` (cryptographically secure, not predictable like `Random`) to pick characters from the charset `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. Notice the charset deliberately **omits** `I`, `O`, `0`, and `1` to avoid visual ambiguity (1 vs l, 0 vs O). Tries up to 10 times and checks `dataManager.joinCodeExists()` each time to guarantee uniqueness.

### 7.3 Available Tickers Logic (LeagueDraftManager)

Two exclusion rules applied when fetching available tickers for a round:
1. Tickers already picked **this round** by any player are locked out (prevents two players drafting the same stock).
2. Tickers picked in **any previous round** of the league are permanently retired.

Rule 2 keeps the draft pool fresh across rounds and prevents repeated match-ups. Both rules are enforced by a single DB query (`getDraftedTickersAllRounds`) that uses `DISTINCT` — since it covers all rounds including the current one, rule 1 is automatically covered.

### 7.4 Yahoo Finance Fetch (LiveMarketProvider)

Calls `https://query1.finance.yahoo.com/v8/finance/chart/{TICKER}?interval=1d&range=1d`. Parses `chart.result[0].meta.regularMarketPrice` from the JSON response.

Batches of 10 tickers with a 1,200ms sleep between batches to avoid rate-limit 429s. On a 429, retries once on `query2.finance.yahoo.com`. Requires a real browser `User-Agent` header — bare Java clients without one get blocked.

If any ticker fails, the seed price from `ManualMarketSimulator` is kept as a fallback. The server always starts successfully regardless of Yahoo Finance availability.

---

## 8. Client–Server Communication

### 8.1 How a Typical Call Works

Example: player submits a stock pick.

1. `DraftScreen` calls `ApiClient.get().submitPick(leagueId, userId, ticker)` on a background thread.
2. `ApiClient` builds a JSON body: `{"userId":5,"ticker":"TSLA"}`.
3. `ApiClient` sends `POST /api/leagues/3/draft/pick` with `Content-Type: application/json`.
4. `LeagueController.makePick()` on the server receives the request, delegates to `LeagueDraftManager.submitPick()`.
5. The draft manager validates (no duplicate pick, ticker exists, ticker not taken), calls `DataManager.saveDraftPick()`.
6. Server returns the saved `DraftPick` as JSON with HTTP 200, or an error status.
7. `ApiClient` parses the JSON into a `DraftPick` object using `parsePick()` and returns it to the caller.
8. `Platform.runLater()` updates the UI.

### 8.2 Error Handling Convention

`ApiClient` returns `null` (for single objects) or an empty list on any failure — HTTP error status, network timeout, or JSON parse error. It never throws exceptions to callers. The UI checks for null and shows an overlay error message. Server-side errors are logged to stdout/stderr on Railway.

### 8.3 No Auth Token / Session

There is no JWT or session cookie. The server is effectively stateless — every request includes the relevant IDs (`userId`, `leagueId`, etc.) in the URL or request body. This is intentional for the scope of the project; the server trusts the client-provided IDs.

---

## 9. League Lifecycle State Machine

```
FORMING
  ↓  (host calls startDraft — min 2 players required)
DRAFTING
  ↓  (host calls evaluateRound — all picks submitted)
ACTIVE
  ↓  (host calls startNextRound — if >1 active player)
DRAFTING  ←── repeats until one player survives
  ↓  (evaluateRound → only 1 active member remains)
FINISHED
```

The `League.Status` enum is `FORMING | DRAFTING | ACTIVE | FINISHED`. `MainMenuScreen` uses a `switch` expression on this status to determine the label and behavior of each league's "Enter" button.

---

## 10. Build & Run

### Development (client)
```bash
mvn javafx:run
```
Requires Java 17 and Maven. Connects to `http://localhost:8080` by default.

### Fat JAR (distributable)
```bash
mvn clean package -DskipTests
java -jar target/sold-short-fat.jar
```
The `maven-shade-plugin` bundles JavaFX, Jackson, and all dependencies into one JAR. The entry point is `Launcher` (not `MainApp`) because the JVM can't directly launch a `javafx.application.Application` subclass from a shaded JAR.

### Server
```bash
cd server
mvn spring-boot:run              # local
# Railway runs:  java -jar target/sold-short-server.jar
```

---

## 11. Common Technical Questions

**Q: Why is there a `Launcher` class separate from `MainApp`?**
A: JavaFX requires the main class to extend `Application`. The JVM's module system prevents loading such a class as the manifest `Main-Class` in a fat (shaded) JAR. `Launcher` is a plain Java class with no JavaFX dependency that just delegates to `MainApp.main()`, bypassing this restriction.

**Q: How do you prevent two players from picking the same stock?**
A: Three-stage validation in `LeagueDraftManager.submitPick()`: (1) DB check that this player hasn't already picked this round, (2) check the ticker exists in the market simulator, (3) DB check that no other player has picked this ticker this round. All checks are against the database, not in-memory state, so they work correctly across multiple clients sharing the same server.

**Q: What happens if Yahoo Finance is down?**
A: `LiveMarketProvider` loads seed prices from `ManualMarketSimulator` first (in the `super()` call), then overwrites them with live prices. If Yahoo Finance is unreachable, the seed prices remain and the server starts normally. The `MarketController` also exposes a `fetchLivePrice` endpoint that the host screen can call on demand — it returns -1.0 if unavailable, and the UI falls back to the last known price.

**Q: How does the lives system work under the hood?**
A: `league_members` has a `lives` column, initialized to `startingLives` when a player joins. `DataManager.deductLife()` runs `UPDATE league_members SET lives = lives - 1 WHERE ...` and returns the new value. When lives reach 0, `is_eliminated` is set to 1 and `elimination_round` is recorded. `getActiveMembers()` filters `WHERE is_eliminated = 0`.

**Q: Why SQLite instead of PostgreSQL or MySQL?**
A: SQLite is file-based — no separate DB server process to manage, no connection string credentials to configure. Railway supports persistent file storage, so the `.db` file persists between deployments. For a single-host app with low concurrency, SQLite is simpler and more than sufficient. The only limitation is write concurrency, which is handled by routing all writes through the single `DataManager` instance.

**Q: How does the overlay UI work? Why not use `Alert` or a new `Stage`?**
A: All dialogs are built as `VBox` "cards" placed inside a `StackPane` overlay layer that sits above the content layer in the scene graph. When `MainApp.showOverlay(node)` is called, the overlay becomes visible with a semi-transparent background. `hideOverlay()` removes the card and makes it mouse-transparent again. This keeps everything in one window and avoids OS-level dialog chrome.

**Q: Is the server stateless?**
A: Mostly yes. All game state (players, picks, lives, round numbers) is in SQLite. The only in-memory server state is the `MarketDataProvider` (current/previous prices) and `LeagueDraftManager.currentDraft`. The current prices are re-fetched from Yahoo Finance on each server startup, and a `stock_prices` table records persisted round prices for evaluation replay.

**Q: What is `Draft.java` for?**
A: `Draft` is a lightweight in-memory object that tracks which players are in the current round and whose turn it is. It's built by `LeagueDraftManager.startDraft()` from the current list of active player IDs. All authoritative state (picks, round number, status) still lives in the DB — `Draft` is just a convenience object for turn-order tracking within one round.

**Q: How does the percent change formula work?**
A: `percentChange = ((currentPrice - previousPrice) / previousPrice) × 100`. A positive result means the stock went up (bad for a short seller). The engine sorts ascending, so rank 1 has the most negative change (stock fell most = best short). The player at the end of the sorted list (most positive change) is the worst performer and loses a life.

**Q: What is `LeaderboardEntry` and where does it come from?**
A: It's an **immutable DTO** (Data Transfer Object) produced by `PortfolioEvaluationEngine` after each round. It holds userId, username, ticker, percentChange, rank, eliminated flag, eliminationRound, and livesRemaining. The `heartsDisplay()` static method returns a hearts string like `❤❤♡` for display in the leaderboard table. It's also used as the payload in Observer callbacks.
