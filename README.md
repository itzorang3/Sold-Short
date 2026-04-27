# Sold Short

A JavaFX fantasy-stock-trading tournament app built for CSC3380 (Object-Oriented Design).
Players draft stocks in a league and compete round-by-round; the worst-performing portfolio each round loses a life, and the last player standing wins.

## Tech

- **Java 17** with **JavaFX 17** (OpenJFX) — desktop client
- **Spring Boot 3** REST server — hosts all game state
- **SQLite** via Xerial JDBC (server-side only; file `soldshort.db` created at runtime)
- **Yahoo Finance API** (YahooFinanceAPI 3.17.0) — server fetches real-time prices on startup
- **Maven** build (`pom.xml` for client, `server/pom.xml` for server)
- **Railway** cloud deployment — players connect from any device via a public HTTPS URL

## Design Patterns

- **Strategy** — `MarketDataProvider` interface → `ManualMarketSimulator` (seed prices) / `LiveMarketProvider` (Yahoo Finance, extends ManualMarketSimulator)
- **Observer** — `PlayerObserver` fired by `PortfolioEvaluationEngine` on round complete and elimination (server-side)
- **Singleton** — `DataManager.getInstance()` (server-side DB access) and `ApiClient.get()` (client-side HTTP singleton)

## Layered Architecture

**Client (JavaFX fat JAR)**
1. Entry — `Launcher` (fat-JAR entry point), `MainApp`
2. API — `ApiClient` — HTTP singleton; all server communication goes through here
3. Models — `User`, `DraftPick`, `League`, `LeaderboardEntry`
4. UI — `LoginScreen`, `MainMenuScreen`, `DraftScreen`, `HostControlScreen`, `LeaderboardScreen`

**Server (Spring Boot fat JAR)**
5. Controllers — `AuthController`, `MarketController`, `LeagueController` (REST endpoints)
6. Business Logic — `LeagueDraftManager`, `PortfolioEvaluationEngine`
7. Market / Strategy — `MarketDataProvider`, `ManualMarketSimulator`, `LiveMarketProvider`
8. Data — `DataManager`, `DatabaseConnection` (SQLite CRUD)

## Run It

The quickest way to play:

```bash
# Everyone: build the client JAR once
mvn clean package -DskipTests

# Set the server URL (use the Railway URL, or localhost:8080 for local dev)
# Windows
set SERVER_URL=https://your-app.up.railway.app
# Mac / Linux
export SERVER_URL=https://your-app.up.railway.app

java -jar target/sold-short-fat.jar
```

See `HOW_TO_RUN.md` for full setup and `SERVER_SETUP.md` for deploying the server.
