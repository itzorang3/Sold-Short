# Sold Short — REST API Server

Spring Boot backend for **Sold Short**, a fantasy stock trading game where players draft stocks they believe will fall the most. This server owns all game state and is deployed to Railway for online multiplayer.

The desktop client lives on the `main` branch of this repo. This `master` branch contains the server only.

---

## Tech Stack

| Technology | Version | Role |
|---|---|---|
| Java | 17 | Core language |
| Spring Boot | 3.3.5 | REST API framework + embedded Tomcat |
| SQLite + Xerial JDBC | 3.45.1.0 | Server-side database |
| Jackson | (via Spring Boot) | JSON serialization |
| Yahoo Finance API | 3.17.0 | Live stock price fetching |
| Maven | 3.8+ | Build system |
| Railway | — | Cloud deployment |

---

## How It Works

```
┌─────────────────────────┐       HTTP / JSON       ┌──────────────────────────┐
│  JavaFX Desktop Client  │ ──────────────────────► │  Spring Boot REST Server │
│   (each player's PC)    │ ◄────────────────────── │   + SQLite database      │
└─────────────────────────┘                         └──────────────────────────┘
```

- The server is the **single source of truth** — users, leagues, picks, lives, and prices all live here.
- The client is a thin UI that makes HTTP calls for everything.
- Multiple players connect to the same server URL simultaneously from any device.
- On startup, the server fetches live prices for all 64 tickers from Yahoo Finance in batches of 10 (with a 1.2s delay between batches to avoid rate limits). If the fetch fails for any ticker, a hard-coded seed price is used — the server always starts successfully.

---

## Project Structure

```
server/
├── src/main/java/com/soldshort/
│   ├── ServerApp.java                      ← Spring Boot entry point
│   ├── config/
│   │   └── AppConfig.java                  ← Bean wiring (market, draft manager, engine)
│   ├── controllers/
│   │   ├── AuthController.java             ← /api/auth, /api/users
│   │   ├── LeagueController.java           ← /api/leagues (CRUD, draft, evaluation)
│   │   └── MarketController.java           ← /api/market
│   ├── data/
│   │   ├── DataManager.java                ← Singleton DB facade (all SQL)
│   │   └── DatabaseConnection.java         ← Single shared SQLite connection
│   ├── draft/
│   │   ├── Draft.java                      ← In-memory round state
│   │   └── LeagueDraftManager.java         ← League lifecycle + pick validation
│   ├── dto/                                ← Request body POJOs
│   ├── engine/
│   │   └── PortfolioEvaluationEngine.java  ← Round scoring + Observer notifications
│   ├── market/
│   │   ├── MarketDataProvider.java         ← Strategy interface
│   │   ├── ManualMarketSimulator.java      ← Seed prices (64 tickers)
│   │   └── LiveMarketProvider.java         ← Yahoo Finance integration
│   ├── models/                             ← Domain models (User, League, DraftPick, etc.)
│   └── observer/
│       ├── PlayerObserver.java             ← Observer interface
│       └── LeaderboardEntry.java           ← Immutable round result DTO
└── src/main/resources/
    └── application.properties              ← Port + DB path config
```

---

## Running Locally

**Prerequisites:** Java 17+, Maven 3.8+

```bash
# Build
cd server
mvn clean package -DskipTests

# Run
java -jar target/sold-short-server-1.0-SNAPSHOT.jar
```

Server starts on **port 8080** by default. You'll see the Yahoo Finance startup fetch in the logs, followed by `Started ServerApp`.

Verify it's working:
```
http://localhost:8080/api/market/stocks
```
You should get a JSON array of 64 stock tickers with prices.

**Custom port:**
```bash
java -DPORT=9090 -jar target/sold-short-server-1.0-SNAPSHOT.jar
```

**Custom DB path:**
```bash
java -DDB_PATH=/path/to/soldshort.db -jar target/sold-short-server-1.0-SNAPSHOT.jar
```

**Fresh start (wipe all data):**
```bash
rm soldshort.db   # or `del soldshort.db` on Windows
# Then restart — schema is recreated automatically
```

---

## Deploying to Railway

### 1. Connect your repo

1. Go to [railway.app/new](https://railway.app/new) → **Deploy from GitHub repo**
2. Select this repository

### 2. Configure the service

In the service **Settings** tab:

| Setting | Value |
|---|---|
| **Branch** | `master` |
| **Root Directory** | `server` |
| **Build Command** | *(auto-detected — Maven)* |
| **Start Command** | `java -jar target/sold-short-server-1.0-SNAPSHOT.jar` |

### 3. Set environment variables

In the service **Variables** tab:

| Variable | Value |
|---|---|
| `PORT` | *(leave blank — Railway injects this automatically)* |
| `DB_PATH` | `soldshort.db` |

> **Persistence:** By default Railway's filesystem resets on redeploy, wiping `soldshort.db`. To keep data across deploys, add a Railway **Volume**, mount it at `/data`, and set `DB_PATH=/data/soldshort.db`.

### 4. Get your URL

After a successful deploy, Railway shows a URL like:
```
https://sold-short-server-production.up.railway.app
```

Open `<URL>/api/market/stocks` in a browser to confirm the server is live.

### 5. Point the client at the server

Set the `SERVER_URL` environment variable before launching the desktop client:

**Windows (Command Prompt):**
```cmd
set SERVER_URL=https://your-app.up.railway.app
java -jar sold-short-fat.jar
```

**Windows (PowerShell):**
```powershell
$env:SERVER_URL="https://your-app.up.railway.app"
java -jar sold-short-fat.jar
```

**Mac / Linux:**
```bash
export SERVER_URL=https://your-app.up.railway.app
java -jar sold-short-fat.jar
```

---

## API Reference

All endpoints return JSON. All `POST` bodies are `Content-Type: application/json`.

### Auth — `/api/auth`, `/api/users`

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/auth/login` | `{ username, password }` | `User` or 401 |
| `POST` | `/api/auth/register` | `{ username, password, email? }` | `User` or 409 |
| `GET` | `/api/users?username=X` | — | `User` or 404 |
| `GET` | `/api/users/{id}` | — | `User` or 404 |
| `POST` | `/api/users/password` | `{ username, newPassword }` | 200 or 400 |

### Market — `/api/market`

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/market/stocks` | Array of `{ ticker, companyName, currentPrice, previousPrice }` |
| `GET` | `/api/market/price?ticker=X&leagueId=Y&round=N` | `Double` price, or `-1.0` if not found |
| `GET` | `/api/market/live-price?ticker=X` | Live `Double` price from Yahoo Finance, or 404 |

### Leagues — `/api/leagues`

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/leagues` | `{ hostId, name, maxPlayers, startingLives }` | `League` |
| `GET` | `/api/leagues/{id}` | — | `League` or 404 |
| `GET` | `/api/leagues?userId=X` | — | `League[]` |
| `DELETE` | `/api/leagues/{id}?hostId=X` | — | 200 or 403 |
| `POST` | `/api/leagues/0/join` | `{ userId, joinCode }` | `League` or 400 |
| `POST` | `/api/leagues/{id}/leave` | `{ userId }` | 200 |
| `GET` | `/api/leagues/{id}/members` | — | `User[]` |
| `GET` | `/api/leagues/{id}/members/active` | — | `User[]` (non-eliminated) |

### Draft — `/api/leagues/{id}/draft`

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/leagues/{id}/draft/start` | `{ userId }` | `League` or 400 |
| `POST` | `/api/leagues/{id}/round/next` | `{ userId }` | `League` or 400 |
| `POST` | `/api/leagues/{id}/draft/pick` | `{ userId, ticker }` | `DraftPick` or 400 |
| `GET` | `/api/leagues/{id}/draft/picks?round=N` | — | `DraftPick[]` |
| `GET` | `/api/leagues/{id}/draft/picks/user?userId=X&round=N` | — | `DraftPick` or 404 |
| `GET` | `/api/leagues/{id}/draft/picks/all` | — | `DraftPick[]` |
| `GET` | `/api/leagues/{id}/draft/tickers?round=N` | — | Stock info array |
| `GET` | `/api/leagues/{id}/draft/all-submitted?round=N` | — | `true` / `false` |
| `GET` | `/api/leagues/{id}/draft/round-evaluated?round=N` | — | `true` / `false` |

### Evaluation — `/api/leagues/{id}`

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/leagues/{id}/evaluate` | `{ hostId, prices: { TICKER: price, ... } }` | `LeaderboardEntry[]` |
| `GET` | `/api/leagues/{id}/leaderboard` | — | `LeaderboardEntry[]` |

---

## Database Schema

Five tables, auto-created on first startup via `CREATE TABLE IF NOT EXISTS`:

```sql
users           (id, username UNIQUE, password, email)
leagues         (id, name, join_code UNIQUE, host_id, max_players,
                 status, current_round, starting_lives)
league_members  (league_id, user_id, is_eliminated,
                 elimination_round, lives)       ← composite PK
draft_picks     (id, league_id, user_id, ticker, round_number)
stock_prices    (id, ticker, price, league_id, round_number, recorded_at)
```

`league_members.lives` tracks each player's remaining lives per league. `is_eliminated = 1` when lives reach 0. `getActiveMembers()` always filters `WHERE is_eliminated = 0`.

---

## Design Patterns

**Strategy** — `MarketDataProvider` interface with two implementations: `ManualMarketSimulator` (seed prices) and `LiveMarketProvider` (Yahoo Finance). Swap them in `AppConfig.java` with no other changes.

**Observer** — `PortfolioEvaluationEngine` fires `onPlayerEliminated()` and `onRoundComplete()` to all registered `PlayerObserver` subscribers after each round evaluation.

**Singleton** — `DataManager.getInstance()` (synchronized) ensures one shared SQLite connection across all controllers and all request threads.

---

## Troubleshooting

**Port already in use:**
```bash
java -DPORT=9090 -jar target/sold-short-server-1.0-SNAPSHOT.jar
```

**Yahoo Finance 429 rate limit on startup:**
The server batches fetches (10 tickers / 1.2s delay). If rate-limiting still occurs, seed prices stay active and the server starts normally. No action needed.

**Railway deploy fails:**
- Confirm service Root Directory is set to `server`
- Confirm the branch is `master`
- Check Railway build logs for the specific Maven error

**Data not persisting across Railway redeploys:**
Add a Railway Volume, mount at `/data`, and set `DB_PATH=/data/soldshort.db` in the Variables tab.

---

*CSC 3380 · Spring 2026 · Team The Big Shorts*
