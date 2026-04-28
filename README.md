# Sold Short — REST API Server

Spring Boot backend for **Sold Short**. This server owns all game state and is deployed to Railway for online multiplayer.

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

## Running Locally

**Prerequisites:** Java 17+, Maven 3.8+

```bash
# Build
cd server
mvn clean package -DskipTests

# Run
java -jar target/sold-short-server-1.0-SNAPSHOT.jar
```

Server starts on **port 8080** by default. 


**Fresh start (wipe all data):**
```bash
rm soldshort.db   # or `del soldshort.db` on Windows
# Then restart — schema is recreated automatically
```

---

## Deploying to Railway

### 1. Connect your repo

1. Go to [railway.app/new](https://railway.app/new) → **Deploy from GitHub repo**
2. Select your repository

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
| `PORT` | 
| `DB_PATH` | `soldshort.db` |


### 4. Point the client at the server

Set the `SERVER_URL` environment variable before launching the desktop client:

**Windows (Command Prompt):**
```cmd
set SERVER_URL=https://your-app.up.railway.app
java -jar sold-short-fat.jar
```

**Mac / Linux:**
```bash
export SERVER_URL=https://your-app.up.railway.app
java -jar sold-short-fat.jar
```


---

## Database Schema

Five tables, auto-created on first startup:

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

