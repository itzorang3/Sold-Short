# Sold Short — Server Setup & Deployment Guide

This guide covers running the Spring Boot server both **locally** (for development/testing)
and **on Railway** (for real online multiplayer from any device).

---

## Architecture Overview

```
┌─────────────────────────┐        HTTP/JSON        ┌──────────────────────────┐
│  JavaFX Desktop Client  │ ──────────────────────► │  Spring Boot REST Server │
│  (each player's PC)     │ ◄────────────────────── │  + SQLite database       │
└─────────────────────────┘                         └──────────────────────────┘
```

- The **server** owns all game state (users, leagues, picks, prices).
- The **client** is a thin UI — all logic lives on the server.
- Multiple players connect to the same server URL simultaneously.
- On startup the server fetches real-time Yahoo Finance prices for all 64 tickers in batches
  (10 tickers per request, 1.2 s between batches) to avoid rate-limiting. If a fetch fails,
  the hard-coded seed prices are used instead — the server always starts successfully.

---

## Part 1 — Run Locally (Development / LAN Play)

### Prerequisites
- Java 17+ (`java -version`)
- Apache Maven 3.8+ (`mvn -version`)

### 1. Build the server fat JAR

```bash
cd server
mvn clean package -DskipTests
```

This produces `server/target/sold-short-server-1.0-SNAPSHOT.jar`.

### 2. Start the server

```bash
java -jar server/target/sold-short-server-1.0-SNAPSHOT.jar
```

The server starts on **port 8080** by default. You'll see Spring Boot's startup banner followed
by live-price fetch progress in the logs.

To use a different port:
```bash
java -DPORT=9090 -jar server/target/sold-short-server-1.0-SNAPSHOT.jar
```

To store the database in a specific location:
```bash
java -DDB_PATH=/path/to/soldshort.db -jar server/target/sold-short-server-1.0-SNAPSHOT.jar
```

### 3. Wipe the database (fresh start)

```bash
del soldshort.db          # Windows
rm soldshort.db           # Mac / Linux
```

Then restart the server — it re-creates the schema automatically.

### 4. Point the client at the local server

By default, the JavaFX client connects to `http://localhost:8080`.
For LAN play, set `SERVER_URL` to your machine's local IP before launching the client:

**Windows (Command Prompt):**
```cmd
set SERVER_URL=http://192.168.1.X:8080
java -jar sold-short-fat.jar
```

**Mac / Linux:**
```bash
SERVER_URL=http://192.168.1.X:8080 java -jar sold-short-fat.jar
```

Find your local IP with `ipconfig` (Windows) or `ifconfig` / `ip addr` (Mac/Linux).

> **Note:** LAN play only works when all players are on the same local network subnet.
> Campus or corporate networks frequently block cross-device traffic — use Railway instead.

---

## Part 2 — Deploy to Railway (Full Online Multiplayer)

Railway is a cloud platform that hosts your server so players can connect from anywhere.

### Step 1 — Push the whole project to GitHub

Railway deploys from a Git repository. The project root (containing both the client source
and the `server/` folder) should be committed to your GitHub repo.

```bash
git add .
git commit -m "Deploy to Railway"
git push origin master
```

### Step 2 — Create a Railway account

Sign up at [https://railway.app](https://railway.app) (free, no credit card required for hobby use).

### Step 3 — Create a new Railway project

1. Go to [https://railway.app/new](https://railway.app/new)
2. Click **"Deploy from GitHub repo"**
3. Select your repository
4. Railway will detect the Maven project and start a build

### Step 4 — Point Railway at the right branch and root

In the service **Settings** tab:

- **Branch** — set to `master` (or whatever branch has the server code)
- **Root Directory** — set to `server` (so Railway builds from `server/pom.xml`)

Railway auto-detects the Spring Boot Maven layout and runs the resulting JAR.

### Step 5 — Set environment variables

In the Railway service dashboard → **Variables** tab (inside the service, not the project root), add:

| Variable | Value | Notes |
|----------|-------|-------|
| `PORT` | *(leave blank)* | Railway injects this automatically |
| `DB_PATH` | `soldshort.db` | SQLite file path on the server |

The server reads `PORT` via `server.port=${PORT:8080}` in `application.properties`.

> **Persistence note:** By default, Railway's filesystem resets on each redeploy, which wipes
> `soldshort.db`. To keep data across deploys, add a **Railway Volume**, mount it at `/data`,
> and set `DB_PATH=/data/soldshort.db`.

### Step 6 — Get your server URL

After the deploy succeeds, Railway shows a generated URL in the service overview, e.g.:
```
https://sold-short-server-production.up.railway.app
```

Verify it works by opening `<URL>/api/market/stocks` in a browser — you should see a JSON
array of 64 tickers.

### Step 7 — Configure the client

**Option A — Environment variable (set each session, no rebuild needed):**

Windows (Command Prompt — must run JAR from the same window):
```cmd
set SERVER_URL=https://sold-short-server-production.up.railway.app
java -jar sold-short-fat.jar
```

Windows (PowerShell):
```powershell
$env:SERVER_URL="https://sold-short-server-production.up.railway.app"
java -jar sold-short-fat.jar
```

Mac / Linux:
```bash
export SERVER_URL=https://sold-short-server-production.up.railway.app
java -jar sold-short-fat.jar
```

**Option B — Hardcode the URL for easy distribution (recommended):**

Edit `src/main/java/com/soldshort/ui/MainApp.java`:
```java
// Change this line:
System.getenv().getOrDefault("SERVER_URL", "http://localhost:8080");

// To:
System.getenv().getOrDefault("SERVER_URL",
    "https://sold-short-server-production.up.railway.app");
```

Then rebuild and distribute — no env var needed for anyone:
```bash
mvn clean package -DskipTests
# Distribute target/sold-short-fat.jar
```

---

## Part 3 — Connection Test

A shell script is included to verify the full end-to-end flow (register, login, create league,
join, draft, evaluate):

```bash
# Local server:
bash test_connection.sh

# Railway server:
bash test_connection.sh https://your-app.up.railway.app
```

The script runs 9 tests and prints a pass/fail summary with a leaderboard at the end.

---

## Part 4 — Server API Reference

All endpoints are prefixed with `/api`.

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Log in with username + password |
| POST | `/api/auth/register` | Create a new account |
| GET | `/api/users?username=X` | Check if username exists |
| GET | `/api/users/{id}` | Fetch user by ID |
| POST | `/api/users/password` | Reset/update password |

### Market
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/market/stocks` | List all 64 stocks with current prices |
| GET | `/api/market/price?ticker=X&leagueId=Y&round=N` | Get a specific stock price for a round |
| GET | `/api/market/live-price?ticker=X` | Fetch real-time price from Yahoo Finance |

### Leagues & Draft
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/leagues` | Create a new league |
| GET | `/api/leagues/{id}` | Get league by ID |
| GET | `/api/leagues/code/{code}` | Get league by join code |
| GET | `/api/leagues/user/{userId}` | Get all leagues for a user |
| POST | `/api/leagues/{id}/join` | Join a league (use id=0 + joinCode body to join by code) |
| POST | `/api/leagues/{id}/start` | Start the draft (host only) |
| POST | `/api/leagues/{id}/next-round` | Start next round (host only) |
| GET | `/api/leagues/{id}/available-tickers?round=N` | Available tickers for this round |
| GET | `/api/leagues/{id}/picks?round=N` | All picks for a round |
| GET | `/api/leagues/{id}/pick?userId=X&round=N` | A specific user's pick |
| GET | `/api/leagues/{id}/all-picks-submitted?round=N` | Check if all picks are in |
| GET | `/api/leagues/{id}/round-evaluated?round=N` | Check if round has been evaluated |
| POST | `/api/leagues/{id}/pick` | Submit a draft pick |
| POST | `/api/leagues/{id}/evaluate` | Host enters prices and evaluates the round |
| GET | `/api/leagues/{id}/leaderboard` | Current standings |
| GET | `/api/leagues/{id}/active-members` | Players still in the tournament |
| GET | `/api/leagues/{id}/all-picks` | All picks ever (for history panel) |
| DELETE | `/api/leagues/{id}/leave?userId=X` | Leave a league |

---

## Troubleshooting

**Server won't start — port already in use:**
```bash
java -DPORT=9090 -jar server/target/sold-short-server-1.0-SNAPSHOT.jar
```

**Client can't connect — `http connect timed out`:**
- Confirm the server is running and the URL has no trailing slash
- If using Railway, verify the deploy succeeded and the URL is correct
- If using local/LAN, both devices must be on the same network subnet — campus Wi-Fi often
  blocks cross-device traffic; use Railway instead

**"Username already taken" on a fresh server:**
- Delete `soldshort.db` on the server and restart to wipe all data.

**Yahoo Finance prices not loading (server logs show 429):**
- The server batches the startup price fetch (10 tickers / 1.2 s). If it still rate-limits,
  seed prices remain active and the app continues normally.

**Railway deploy fails at build:**
- Confirm the service Root Directory is set to `server`
- Confirm the branch matches where your code lives (commonly `master`, not `main`)
- Check the Railway build logs for the specific Maven error
