# Sold Short — How to Run

---

## Quick Start (Online Multiplayer — Recommended)

Sold Short uses a client-server architecture. One person runs (or deploys) the server,
and everyone else just needs the `sold-short-fat.jar` client pointed at that server.

The server is already deployed on Railway — ask the host for the URL, then skip to
**Running the Client** below.

**Full deployment instructions → see `SERVER_SETUP.md`**

---

## Running the Client

### Prerequisites
- **Java 17 or newer** — download from https://adoptium.net if not installed
  - Verify: open a terminal and run `java -version`

### Steps

1. **Build the client JAR** (only needed if starting from source):
   ```
   mvn clean package -DskipTests
   ```
   This creates `target/sold-short-fat.jar`.

2. **Set the server URL:**

   Windows (Command Prompt):
   ```cmd
   set SERVER_URL=https://your-app.up.railway.app
   ```
   Windows (PowerShell):
   ```powershell
   $env:SERVER_URL="https://your-app.up.railway.app"
   ```
   Mac / Linux:
   ```bash
   export SERVER_URL=https://your-app.up.railway.app
   ```
   If the server is running locally, you can skip this step — it defaults to `http://localhost:8080`.

   > **Important:** The `set` / `export` command only persists for the current terminal session.
   > Run the JAR from the same window immediately after setting the variable.

3. **Launch the client** (run this in the same terminal window):
   ```
   java -jar target/sold-short-fat.jar
   ```
   Or use the provided scripts:
   - Windows — `run.bat`
   - Mac / Linux — `bash run.sh`

---

## Running the Server (Local / LAN)

```bash
cd server
mvn clean package -DskipTests
java -jar target/sold-short-server-1.0-SNAPSHOT.jar
```

The server starts on port 8080. The SQLite database (`soldshort.db`) is created automatically
on first run. See **`SERVER_SETUP.md`** for Railway cloud deployment.

To wipe the database and start fresh, delete `soldshort.db` and restart the server:
```bash
del soldshort.db          # Windows
rm soldshort.db           # Mac / Linux
java -jar target/sold-short-server-1.0-SNAPSHOT.jar
```

---

## Running via Maven (Development Only)

```bash
# Start the server first (in one terminal):
cd server && mvn spring-boot:run

# Then start the client (in a second terminal):
mvn javafx:run
```

---

## Sharing a Clean Copy

Before zipping the project to share it:
1. **Delete `soldshort.db`** from the server directory (if present — this is local test data).
2. Zip everything **except** the `target/` and `server/target/` folders (Maven rebuilds them).
3. Recipients: build client with `mvn clean package -DskipTests`, then run the JAR.

The server's database lives on the server — players don't carry any data in the client JAR.

---

## Project Structure

```
CSC3380 Project - Sold Short/
├── src/main/java/com/soldshort/        ← CLIENT (JavaFX fat JAR)
│   ├── Launcher.java                   Fat-JAR entry point (delegates to MainApp)
│   ├── api/ApiClient.java              HTTP singleton — all server calls go here
│   ├── models/                         User, DraftPick, League, LeaderboardEntry
│   └── ui/                             MainApp, LoginScreen, MainMenuScreen,
│                                       DraftScreen, HostControlScreen, LeaderboardScreen
│
├── server/src/main/java/com/soldshort/ ← SERVER (Spring Boot fat JAR)
│   ├── ServerApp.java                  Spring Boot entry point
│   ├── config/AppConfig.java           Spring @Bean definitions
│   ├── controllers/                    AuthController, MarketController, LeagueController
│   ├── data/                           DatabaseConnection, DataManager
│   ├── dto/                            Request body classes (LoginRequest, etc.)
│   ├── draft/                          LeagueDraftManager
│   ├── engine/                         PortfolioEvaluationEngine
│   ├── market/                         MarketDataProvider, ManualMarketSimulator,
│   │                                   LiveMarketProvider
│   ├── models/                         User, League, DraftPick, etc.
│   └── observer/                       PlayerObserver, LeaderboardEntry
│
├── SERVER_SETUP.md                     Full server deployment guide
├── HOW_TO_RUN.md                       This file
├── CHANGES.md                          Changelog for the online multiplayer update
├── test_connection.sh                  End-to-end API test (Mac / Linux)
├── test_connection.bat                 End-to-end API test (Windows — run with bash)
├── run.bat                             Windows client launch script
└── run.sh                             Mac/Linux client launch script
```

## Design Patterns Used
- **Strategy** — `MarketDataProvider` interface; `ManualMarketSimulator` uses seed prices,
  `LiveMarketProvider` overrides them with Yahoo Finance real-time quotes on startup
- **Observer** — `PlayerObserver` fired by `PortfolioEvaluationEngine` on elimination / round end
- **Singleton** — `DataManager` (server-side DB) and `ApiClient` (client-side HTTP)
