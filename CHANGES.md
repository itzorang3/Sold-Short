# Sold Short — Changelog: Online Multiplayer Update

This document describes every file added or modified to convert Sold Short from a
single-machine local app into a client-server multiplayer game.

---

## Summary

The app was split into two independently runnable components:

- **Server** (`server/`) — A Spring Boot REST API that owns all game state. Deployed
  locally or on Railway so any number of players can connect simultaneously.
- **Client** (`src/`) — The existing JavaFX desktop app, slimmed down to a pure UI.
  All business logic was moved to the server. The client communicates via HTTP/JSON.

---

## New Files

### Server — `server/`

| File | Description |
|------|-------------|
| `server/pom.xml` | Maven build for the Spring Boot server. Dependencies: `spring-boot-starter-web`, `sqlite-jdbc:3.45.1.0`. Plugin: `spring-boot-maven-plugin` (produces `sold-short-server.jar`). |
| `server/src/main/resources/application.properties` | Reads `PORT` and `DB_PATH` from environment variables with sensible defaults (`8080` / `soldshort.db`). |
| `server/src/main/java/com/soldshort/ServerApp.java` | `@SpringBootApplication` entry point. `@PreDestroy` closes the SQLite connection on shutdown. |
| `server/src/main/java/com/soldshort/config/AppConfig.java` | `@Configuration` class. Declares Spring `@Bean`s for `ManualMarketSimulator`, `LeagueDraftManager`, and `PortfolioEvaluationEngine` so they are shared singletons across all HTTP requests. |
| `server/src/main/java/com/soldshort/data/DatabaseConnection.java` | Modified copy of the original. Reads the `DB_PATH` environment variable so the database location is configurable at deploy time. |
| `server/src/main/java/com/soldshort/dto/LoginRequest.java` | Request body: `username`, `password`, `email`. Used by login and register endpoints. |
| `server/src/main/java/com/soldshort/dto/CreateLeagueRequest.java` | Request body: `hostId`, `name`, `maxPlayers`, `startingLives`. |
| `server/src/main/java/com/soldshort/dto/JoinRequest.java` | Request body: `userId`, `joinCode`. |
| `server/src/main/java/com/soldshort/dto/MakePickRequest.java` | Request body: `userId`, `ticker`. |
| `server/src/main/java/com/soldshort/dto/EvaluateRequest.java` | Request body: `hostId`, `Map<String, Double> prices`. Carries the host-entered end-of-round prices. |
| `server/src/main/java/com/soldshort/dto/UpdatePasswordRequest.java` | Request body: `username`, `newPassword`. |
| `server/src/main/java/com/soldshort/controllers/AuthController.java` | `@RestController` at `/api`. Endpoints: `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/users?username=X`, `GET /api/users/{id}`, `POST /api/users/password`. |
| `server/src/main/java/com/soldshort/controllers/MarketController.java` | `@RestController` at `/api/market`. Endpoints: `GET /api/market/stocks` (all 64 tickers with company names and prices), `GET /api/market/price` (price for a specific ticker/league/round). |
| `server/src/main/java/com/soldshort/controllers/LeagueController.java` | `@RestController` at `/api/leagues`. All league, draft, and evaluation logic. Key endpoints: create, join (by ID or join code), start draft, submit pick, evaluate round, get leaderboard, get active members, start next round, leave league. The `/evaluate` endpoint applies host-entered prices to the `ManualMarketSimulator` singleton then triggers `PortfolioEvaluationEngine.evaluateRound()`. |

**Copied without modification from `src/`:**
- `server/.../data/DataManager.java`
- `server/.../draft/LeagueDraftManager.java`
- `server/.../engine/PortfolioEvaluationEngine.java`
- `server/.../market/ManualMarketSimulator.java`, `MarketDataProvider.java`, `LiveMarketProvider.java`
- `server/.../models/` — all model classes
- `server/.../observer/` — `PlayerObserver.java`, `LeaderboardEntry.java`

### Client — New Files

| File | Description |
|------|-------------|
| `src/main/java/com/soldshort/api/ApiClient.java` | Singleton HTTP client for the JavaFX app. Configured once via `ApiClient.configure(serverUrl)`. Uses `java.net.http.HttpClient` (Java 11+) and Jackson `JsonNode` for manual JSON parsing. Mirrors every server endpoint. Inner class `StockInfo` holds ticker, company name, and price data. |

### Documentation

| File | Description |
|------|-------------|
| `SERVER_SETUP.md` | Step-by-step guide for running the server locally, on a LAN, and deploying to Railway. Includes API reference and troubleshooting. |
| `CHANGES.md` | This file. |

---

## Modified Files

### Client

| File | What Changed | Why |
|------|-------------|-----|
| `pom.xml` | Added `jackson-databind:2.17.1` dependency | Client needs Jackson's `ObjectMapper` and `JsonNode` to parse JSON responses from the server |
| `src/.../ui/MainApp.java` | Removed local `DataManager`, `market`, `draftManager`, `evaluationEngine` fields. Added `SERVER_URL` constant + `ApiClient.configure(SERVER_URL)` in `start()`. Simplified all `show*()` method signatures (no longer pass engine/manager objects). | All business logic moved to server; client is now stateless except for the current user |
| `src/.../ui/LoginScreen.java` | Replaced direct `DataManager` calls with `ApiClient.get()` methods. All auth calls moved to background threads with `Platform.runLater()` for UI updates. | HTTP calls must not block the JavaFX Application Thread |
| `src/.../ui/MainMenuScreen.java` | Constructor simplified to `MainMenuScreen(User currentUser)` only (no `draftManager`). All league operations (`getLeaguesForUser`, `startDraft`, `leaveLeague`) now call `ApiClient.get()` on background threads. | Same as above; local managers no longer exist in the client |
| `src/.../ui/DraftScreen.java` | Constructor simplified to `DraftScreen(User, League)`. Status panel fetches `getActiveMembers()` and `getPicksForRound()` from server. Picking panel fetches `getAvailableTickers()` from server. Pick submission calls `ApiClient.get().submitPick()`. Ticker table uses `StockInfo` objects instead of raw strings. Auto-refresh `Timeline` polls the server every 4 seconds. | Draft state is now authoritative on the server; all clients poll to stay in sync |
| `src/.../ui/HostControlScreen.java` | Constructor simplified to `HostControlScreen(User, League)`. Picks and stock data fetched from server. `handleEvaluate()` sends a price map to `ApiClient.get().evaluateRound()` on a background thread. Results overlay displays before/after prices returned by the server. | Host evaluation now triggers server-side `PortfolioEvaluationEngine` and updates DB there |
| `src/.../ui/LeaderboardScreen.java` | Constructor simplified to `LeaderboardScreen(League)`. Standings from `ApiClient.get().getLeaderboard()`. Winner determined from server-returned entries. "Start Next Round" calls `ApiClient.get().startNextRound()`. Round history panel uses `getAllPicksForLeague()` + `getUserById()` + `getAllStocks()`. | All leaderboard and history data now lives on the server |
| `HOW_TO_RUN.md` | Updated to cover client/server setup, `SERVER_URL` environment variable, and running the server locally | Reflects the new two-component architecture |

---

## Files Unchanged

The following files were **not modified** as part of this update:

- `src/main/java/com/soldshort/Launcher.java`
- `src/main/java/com/soldshort/models/` — all model classes
- `src/main/java/com/soldshort/observer/PlayerObserver.java`, `LeaderboardEntry.java`
- `src/main/resources/com/soldshort/ui/styles.css`
- `run.bat`, `run.sh`

---

## Architecture Before vs. After

### Before (single-machine)
```
JavaFX UI
  └── DataManager (SQLite, local file)
  └── LeagueDraftManager
  └── PortfolioEvaluationEngine
  └── ManualMarketSimulator
```

### After (client-server)
```
JavaFX UI
  └── ApiClient (HTTP singleton)
        │
        │  JSON over HTTP
        ▼
  Spring Boot Server
    └── LeagueController / AuthController / MarketController
          └── DataManager (SQLite, server-side)
          └── LeagueDraftManager (@Bean singleton)
          └── PortfolioEvaluationEngine (@Bean singleton)
          └── ManualMarketSimulator (@Bean singleton)
```
