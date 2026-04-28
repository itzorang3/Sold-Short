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

**Prerequisites:** [Java 17+](https://adoptium.net) and [Maven 3.8+](https://maven.apache.org)

### Windows — one command after cloning

```cmd
git clone -b main https://github.com/itzorang3/Sold-Short.git
cd Sold-Short
run.bat
```

`run.bat` handles everything: builds the JAR if it doesn't exist, sets the Railway server URL automatically, and launches the app. Just double-click it or run it from any terminal.

### Mac / Linux

```bash
git clone -b main https://github.com/itzorang3/Sold-Short.git
cd Sold-Short
mvn clean package -DskipTests
export SERVER_URL=https://sold-short-production.up.railway.app
java -jar target/sold-short-fat.jar
```

Or use the included script:

```bash
bash run.sh
```

See `HOW_TO_RUN.md` for full setup details and `SERVER_SETUP.md` for deploying the server.
