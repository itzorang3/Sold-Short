package com.soldshort.data;

import com.soldshort.models.*;
import com.soldshort.observer.LeaderboardEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Central data-access facade for the Sold Short application.
 *
 * Provides full CRUD operations for every entity in the system.
 * All SQL interactions route through this class, keeping persistence
 * logic separate from business logic in higher layers.
 *
 * Design Pattern: Singleton
 * Role: Subject — the single shared instance manages the database connection
 *       and ensures consistent state across all consumers.
 */
public class DataManager {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static DataManager instance;

    private DataManager() {
        initializeDatabase();
    }

    /**
     * Returns the single DataManager instance, creating it on first call.
     * Thread-safe via synchronized keyword (sufficient for a single-host app).
     */
    public static synchronized DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    // ── Schema Initialization ─────────────────────────────────────────────────

    /**
     * Creates all tables if they do not already exist, then runs migrations
     * to add any new columns introduced after the initial schema.
     * Safe to call every time the application starts.
     */
    private void initializeDatabase() {
        String[] ddl = {
            // Users
            """
            CREATE TABLE IF NOT EXISTS users (
                id       INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT    UNIQUE NOT NULL,
                password TEXT    NOT NULL,
                email    TEXT
            )
            """,
            // Leagues — starting_lives stores the per-league life count chosen by the host
            """
            CREATE TABLE IF NOT EXISTS leagues (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                name           TEXT    NOT NULL,
                join_code      TEXT    UNIQUE NOT NULL,
                host_id        INTEGER NOT NULL,
                max_players    INTEGER DEFAULT 8,
                status         TEXT    DEFAULT 'FORMING',
                current_round  INTEGER DEFAULT 0,
                starting_lives INTEGER DEFAULT 3,
                FOREIGN KEY (host_id) REFERENCES users(id)
            )
            """,
            // League membership + elimination tracking + lives
            """
            CREATE TABLE IF NOT EXISTS league_members (
                league_id         INTEGER NOT NULL,
                user_id           INTEGER NOT NULL,
                is_eliminated     INTEGER DEFAULT 0,
                elimination_round INTEGER DEFAULT -1,
                lives             INTEGER DEFAULT 3,
                PRIMARY KEY (league_id, user_id),
                FOREIGN KEY (league_id) REFERENCES leagues(id),
                FOREIGN KEY (user_id)   REFERENCES users(id)
            )
            """,
            // Draft picks (one per player per round)
            """
            CREATE TABLE IF NOT EXISTS draft_picks (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                league_id    INTEGER NOT NULL,
                user_id      INTEGER NOT NULL,
                ticker       TEXT    NOT NULL,
                round_number INTEGER NOT NULL,
                FOREIGN KEY (league_id) REFERENCES leagues(id),
                FOREIGN KEY (user_id)   REFERENCES users(id)
            )
            """,
            // Stock prices per round per league
            """
            CREATE TABLE IF NOT EXISTS stock_prices (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                ticker       TEXT    NOT NULL,
                price        REAL    NOT NULL,
                league_id    INTEGER,
                round_number INTEGER,
                recorded_at  TEXT    DEFAULT (datetime('now'))
            )
            """
        };

        try (Statement stmt = DatabaseConnection.getConnection().createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema: " + e.getMessage(), e);
        }

        // Migrate existing databases that were created before the lives columns were added
        runMigrations();
    }

    /**
     * Applies schema migrations for columns added after the initial release.
     * Each ALTER TABLE is attempted independently; duplicate-column errors are ignored
     * so this is safe to re-run on every startup.
     */
    private void runMigrations() {
        String[] migrations = {
            "ALTER TABLE league_members ADD COLUMN lives INTEGER DEFAULT 3",
            "ALTER TABLE leagues ADD COLUMN starting_lives INTEGER DEFAULT 3"
        };
        for (String sql : migrations) {
            try (Statement stmt = DatabaseConnection.getConnection().createStatement()) {
                stmt.execute(sql);
            } catch (SQLException e) {
                // "duplicate column name" is expected on 2nd+ runs — suppress it
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    System.err.println("Migration warning: " + e.getMessage());
                }
            }
        }
    }

    // ── User CRUD ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user. Returns the created User with its generated ID,
     * or null if the username is already taken.
     */
    public User createUser(String username, String password, String email) {
        String sql = "INSERT INTO users (username, password, email) VALUES (?, ?, ?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, email);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new User(keys.getInt(1), username, password, email);
            }
        } catch (SQLException e) {
            System.err.println("createUser failed: " + e.getMessage());
        }
        return null;
    }

    /** Fetches a user by username, or null if not found. */
    public User getUserByUsername(String username) {
        String sql = "SELECT id, username, password, email FROM users WHERE username = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"),
                        rs.getString("password"), rs.getString("email"));
            }
        } catch (SQLException e) {
            System.err.println("getUserByUsername failed: " + e.getMessage());
        }
        return null;
    }

    /** Fetches a user by ID, or null if not found. */
    public User getUserById(int id) {
        String sql = "SELECT id, username, password, email FROM users WHERE id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"),
                        rs.getString("password"), rs.getString("email"));
            }
        } catch (SQLException e) {
            System.err.println("getUserById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates the password for the given username.
     *
     * @return true if the user was found and the password was changed
     */
    public boolean updatePassword(String username, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("updatePassword failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Validates login credentials.
     *
     * @return the matching User, or null if credentials are incorrect
     */
    public User validateLogin(String username, String password) {
        User user = getUserByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    // ── League CRUD ───────────────────────────────────────────────────────────

    /**
     * Creates a new league with the specified starting lives, and returns it
     * with its generated ID.
     */
    public League createLeague(String name, int hostId, int maxPlayers,
                               String joinCode, int startingLives) {
        String sql = "INSERT INTO leagues "
                   + "(name, join_code, host_id, max_players, status, current_round, starting_lives) "
                   + "VALUES (?, ?, ?, ?, 'FORMING', 0, ?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, joinCode);
            ps.setInt(3, hostId);
            ps.setInt(4, maxPlayers);
            ps.setInt(5, startingLives);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                // Auto-add the host as first member with correct starting lives
                joinLeagueWithLives(id, hostId, startingLives);
                return new League(id, name, joinCode, hostId, maxPlayers,
                        League.Status.FORMING, 0, startingLives);
            }
        } catch (SQLException e) {
            System.err.println("createLeague failed: " + e.getMessage());
        }
        return null;
    }

    /** Backward-compatible overload — defaults to 3 starting lives. */
    public League createLeague(String name, int hostId, int maxPlayers, String joinCode) {
        return createLeague(name, hostId, maxPlayers, joinCode, 3);
    }

    /** Fetches a league by its join code, or null if not found. */
    public League getLeagueByCode(String joinCode) {
        String sql = "SELECT * FROM leagues WHERE join_code = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, joinCode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapLeague(rs);
            }
        } catch (SQLException e) {
            System.err.println("getLeagueByCode failed: " + e.getMessage());
        }
        return null;
    }

    /** Fetches a league by ID, or null if not found. */
    public League getLeagueById(int leagueId) {
        String sql = "SELECT * FROM leagues WHERE id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapLeague(rs);
            }
        } catch (SQLException e) {
            System.err.println("getLeagueById failed: " + e.getMessage());
        }
        return null;
    }

    /** Returns every league a user belongs to. */
    public List<League> getLeaguesForUser(int userId) {
        List<League> leagues = new ArrayList<>();
        String sql = "SELECT l.* FROM leagues l "
                   + "JOIN league_members lm ON l.id = lm.league_id "
                   + "WHERE lm.user_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                leagues.add(mapLeague(rs));
            }
        } catch (SQLException e) {
            System.err.println("getLeaguesForUser failed: " + e.getMessage());
        }
        return leagues;
    }

    /** Checks if a join code is already in use. */
    public boolean joinCodeExists(String joinCode) {
        return getLeagueByCode(joinCode) != null;
    }

    /**
     * Adds a user to a league as a member, initialising their lives from the
     * league's starting_lives setting. Returns true on success.
     */
    public boolean joinLeague(int leagueId, int userId) {
        League league = getLeagueById(leagueId);
        int lives = (league != null) ? league.getStartingLives() : 3;
        return joinLeagueWithLives(leagueId, userId, lives);
    }

    /** Internal overload used during league creation (lives already known). */
    private boolean joinLeagueWithLives(int leagueId, int userId, int lives) {
        String sql = "INSERT OR IGNORE INTO league_members (league_id, user_id, lives) VALUES (?, ?, ?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, userId);
            ps.setInt(3, lives);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("joinLeagueWithLives failed: " + e.getMessage());
        }
        return false;
    }

    /** Returns all members (active and eliminated) of a league. */
    public List<User> getLeagueMembers(int leagueId) {
        List<User> members = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.password, u.email "
                   + "FROM users u JOIN league_members lm ON u.id = lm.user_id "
                   + "WHERE lm.league_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(new User(rs.getInt("id"), rs.getString("username"),
                        rs.getString("password"), rs.getString("email")));
            }
        } catch (SQLException e) {
            System.err.println("getLeagueMembers failed: " + e.getMessage());
        }
        return members;
    }

    /** Returns only members who have NOT been eliminated. */
    public List<User> getActiveMembers(int leagueId) {
        List<User> members = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.password, u.email "
                   + "FROM users u JOIN league_members lm ON u.id = lm.user_id "
                   + "WHERE lm.league_id = ? AND lm.is_eliminated = 0";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(new User(rs.getInt("id"), rs.getString("username"),
                        rs.getString("password"), rs.getString("email")));
            }
        } catch (SQLException e) {
            System.err.println("getActiveMembers failed: " + e.getMessage());
        }
        return members;
    }

    /** Updates the league status. */
    public void updateLeagueStatus(int leagueId, League.Status status) {
        String sql = "UPDATE leagues SET status = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, leagueId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("updateLeagueStatus failed: " + e.getMessage());
        }
    }

    /**
     * Removes a league and all associated data (members, picks, prices).
     * Only call this for leagues the current user hosts.
     */
    public void removeLeague(int leagueId) {
        String[] sqls = {
            "DELETE FROM stock_prices    WHERE league_id = ?",
            "DELETE FROM draft_picks     WHERE league_id = ?",
            "DELETE FROM league_members  WHERE league_id = ?",
            "DELETE FROM leagues         WHERE id = ?"
        };
        try {
            for (String sql : sqls) {
                try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
                    ps.setInt(1, leagueId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("removeLeague failed: " + e.getMessage());
        }
    }

    /** Advances the league to the next round number. */
    public void updateLeagueRound(int leagueId, int round) {
        String sql = "UPDATE leagues SET current_round = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, round);
            ps.setInt(2, leagueId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("updateLeagueRound failed: " + e.getMessage());
        }
    }

    // ── Draft Picks ───────────────────────────────────────────────────────────

    /** Records a player's stock pick for a given round. */
    public DraftPick saveDraftPick(int leagueId, int userId, String ticker, int round) {
        String sql = "INSERT INTO draft_picks (league_id, user_id, ticker, round_number) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, userId);
            ps.setString(3, ticker);
            ps.setInt(4, round);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                return new DraftPick(keys.getInt(1), leagueId, userId, ticker, round);
            }
        } catch (SQLException e) {
            System.err.println("saveDraftPick failed: " + e.getMessage());
        }
        return null;
    }

    /** Returns all picks for a given round in a league. */
    public List<DraftPick> getPicksForRound(int leagueId, int round) {
        List<DraftPick> picks = new ArrayList<>();
        String sql = "SELECT * FROM draft_picks WHERE league_id = ? AND round_number = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, round);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                picks.add(new DraftPick(rs.getInt("id"), rs.getInt("league_id"),
                        rs.getInt("user_id"), rs.getString("ticker"), rs.getInt("round_number")));
            }
        } catch (SQLException e) {
            System.err.println("getPicksForRound failed: " + e.getMessage());
        }
        return picks;
    }

    /** Returns the specific pick a user made in a given round, or null. */
    public DraftPick getPickForUser(int leagueId, int userId, int round) {
        String sql = "SELECT * FROM draft_picks WHERE league_id = ? AND user_id = ? AND round_number = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, userId);
            ps.setInt(3, round);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new DraftPick(rs.getInt("id"), rs.getInt("league_id"),
                        rs.getInt("user_id"), rs.getString("ticker"), rs.getInt("round_number"));
            }
        } catch (SQLException e) {
            System.err.println("getPickForUser failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns true when every active (non-eliminated) player has submitted a pick
     * for the given round.
     */
    public boolean allPicksSubmitted(int leagueId, int round) {
        int activePlayers = getActiveMembers(leagueId).size();
        if (activePlayers == 0) return true;
        String sql = "SELECT COUNT(*) FROM draft_picks WHERE league_id = ? AND round_number = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, round);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) >= activePlayers;
            }
        } catch (SQLException e) {
            System.err.println("allPicksSubmitted failed: " + e.getMessage());
        }
        return false;
    }

    /** Returns all tickers already picked in a round (to prevent duplicate picks). */
    public List<String> getDraftedTickers(int leagueId, int round) {
        List<String> tickers = new ArrayList<>();
        String sql = "SELECT ticker FROM draft_picks WHERE league_id = ? AND round_number = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, round);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tickers.add(rs.getString("ticker"));
            }
        } catch (SQLException e) {
            System.err.println("getDraftedTickers failed: " + e.getMessage());
        }
        return tickers;
    }

    /**
     * Returns every distinct ticker that has been picked in ANY round of a league.
     * Used to permanently retire tickers from the pool once they have been drafted.
     */
    public List<String> getDraftedTickersAllRounds(int leagueId) {
        List<String> tickers = new ArrayList<>();
        String sql = "SELECT DISTINCT ticker FROM draft_picks WHERE league_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tickers.add(rs.getString("ticker"));
            }
        } catch (SQLException e) {
            System.err.println("getDraftedTickersAllRounds failed: " + e.getMessage());
        }
        return tickers;
    }

    // ── Stock Prices ──────────────────────────────────────────────────────────

    /** Records a stock price for a specific round. */
    public void saveStockPrice(String ticker, double price, int leagueId, int round) {
        String sql = "INSERT INTO stock_prices (ticker, price, league_id, round_number) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, ticker);
            ps.setDouble(2, price);
            ps.setInt(3, leagueId);
            ps.setInt(4, round);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("saveStockPrice failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves the closing price for a ticker in a specific round.
     * Returns -1.0 if no price is recorded.
     */
    public double getStockPrice(String ticker, int leagueId, int round) {
        String sql = "SELECT price FROM stock_prices WHERE ticker = ? AND league_id = ? AND round_number = ? LIMIT 1";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setString(1, ticker);
            ps.setInt(2, leagueId);
            ps.setInt(3, round);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("price");
            }
        } catch (SQLException e) {
            System.err.println("getStockPrice failed: " + e.getMessage());
        }
        return -1.0;
    }

    // ── Lives System ──────────────────────────────────────────────────────────

    /**
     * Returns the number of lives remaining for a player in a league.
     * Returns -1 if the membership record is not found.
     */
    public int getLives(int leagueId, int userId) {
        String sql = "SELECT lives FROM league_members WHERE league_id = ? AND user_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("lives");
            }
        } catch (SQLException e) {
            System.err.println("getLives failed: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Deducts one life from a player. If lives reach 0 the player is fully
     * eliminated from the tournament via {@link #eliminatePlayer}.
     *
     * @return remaining lives after deduction (0 means fully eliminated)
     */
    public int deductLife(int leagueId, int userId, int round) {
        String sql = "UPDATE league_members SET lives = MAX(0, lives - 1) "
                   + "WHERE league_id = ? AND user_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("deductLife update failed: " + e.getMessage());
        }
        int remaining = getLives(leagueId, userId);
        if (remaining <= 0) {
            eliminatePlayer(leagueId, userId, round);
            return 0;
        }
        return remaining;
    }

    // ── Elimination ───────────────────────────────────────────────────────────

    /**
     * Marks a player as eliminated in the given round.
     * Also checks if only one active player remains and auto-closes the league.
     */
    public void eliminatePlayer(int leagueId, int userId, int round) {
        String sql = "UPDATE league_members SET is_eliminated = 1, elimination_round = ? "
                   + "WHERE league_id = ? AND user_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, round);
            ps.setInt(2, leagueId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("eliminatePlayer failed: " + e.getMessage());
        }
    }

    /** Returns the elimination round for a player, or -1 if still active. */
    public int getEliminationRound(int leagueId, int userId) {
        String sql = "SELECT elimination_round FROM league_members WHERE league_id = ? AND user_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("elimination_round");
            }
        } catch (SQLException e) {
            System.err.println("getEliminationRound failed: " + e.getMessage());
        }
        return -1;
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    /**
     * Builds a leaderboard snapshot for the given league.
     * Calculates real % change for each player from persisted stock_prices rows.
     * Includes each player's remaining lives.
     * If the league is FINISHED and exactly one active player remains, their
     * status is flagged with eliminationRound = -2 to signal "Winner".
     */
    public List<LeaderboardEntry> getLeaderboard(int leagueId, int currentRound) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String sql = "SELECT u.id, u.username, lm.is_eliminated, lm.elimination_round, lm.lives "
                   + "FROM users u JOIN league_members lm ON u.id = lm.user_id "
                   + "WHERE lm.league_id = ? "
                   + "ORDER BY lm.is_eliminated ASC, lm.elimination_round DESC";

        // Detect winner (for winner badge)
        League league = getLeagueById(leagueId);
        boolean isFinished = (league != null && league.getStatus() == League.Status.FINISHED);
        List<User> activeMembers = getActiveMembers(leagueId);
        int winnerId = (isFinished && activeMembers.size() == 1) ? activeMembers.get(0).getId() : -1;

        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ResultSet rs = ps.executeQuery();
            int rank = 1;
            while (rs.next()) {
                int     userId         = rs.getInt("id");
                boolean elim           = rs.getInt("is_eliminated") == 1;
                int     elimR          = rs.getInt("elimination_round");
                int     livesRemaining = rs.getInt("lives");

                DraftPick pick  = getPickForUser(leagueId, userId, currentRound);
                String   ticker = (pick != null) ? pick.getTicker() : "—";

                // Calculate % change from persisted price rows
                double pctChange = 0.0;
                if (pick != null) {
                    double cur  = getStockPrice(ticker, leagueId, currentRound);
                    double prev = getStockPrice(ticker, leagueId, currentRound - 1);
                    if (cur > 0 && prev > 0) {
                        pctChange = ((cur - prev) / prev) * 100.0;
                    }
                }

                // -2 is a sentinel value meaning "Winner"
                int elimFlag = elim ? elimR : (userId == winnerId ? -2 : -1);

                entries.add(new LeaderboardEntry(
                        userId, rs.getString("username"),
                        ticker, pctChange, rank++, elim, elimFlag, livesRemaining));
            }
        } catch (SQLException e) {
            System.err.println("getLeaderboard failed: " + e.getMessage());
        }
        return entries;
    }

    // ── Round Evaluation Status ───────────────────────────────────────────────

    /**
     * Returns true if every pick for the given round already has a corresponding
     * stock_prices row — meaning the host has already evaluated this round.
     *
     * Used by HostControlScreen to prevent double-evaluation, and by DraftScreen
     * to auto-navigate non-host players to the leaderboard once evaluation is done.
     */
    public boolean isRoundEvaluated(int leagueId, int round) {
        List<DraftPick> picks = getPicksForRound(leagueId, round);
        if (picks.isEmpty()) return false;
        return picks.stream().allMatch(p -> getStockPrice(p.getTicker(), leagueId, round) > 0);
    }

    // ── Leave League ──────────────────────────────────────────────────────────

    /**
     * Removes a non-host user from a league.
     * If the league is mid-game (DRAFTING or ACTIVE), the player is first marked
     * as eliminated so existing round results stay coherent.
     *
     * @return true if the membership row was found and removed
     */
    public boolean leaveLeague(int leagueId, int userId) {
        League league = getLeagueById(leagueId);
        if (league == null) return false;

        // Eliminate first so active-game standings reflect the departure
        if (league.getStatus() == League.Status.DRAFTING
                || league.getStatus() == League.Status.ACTIVE) {
            eliminatePlayer(leagueId, userId, league.getCurrentRound());
        }

        String sql = "DELETE FROM league_members WHERE league_id = ? AND user_id = ?";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("leaveLeague failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns all draft picks for a league across every round, ordered by round
     * then user.  Used to build the round-history table on the final leaderboard.
     */
    public List<DraftPick> getAllPicksForLeague(int leagueId) {
        List<DraftPick> picks = new ArrayList<>();
        String sql = "SELECT * FROM draft_picks WHERE league_id = ? ORDER BY round_number ASC, user_id ASC";
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
            ps.setInt(1, leagueId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                picks.add(new DraftPick(rs.getInt("id"), rs.getInt("league_id"),
                        rs.getInt("user_id"), rs.getString("ticker"), rs.getInt("round_number")));
            }
        } catch (SQLException e) {
            System.err.println("getAllPicksForLeague failed: " + e.getMessage());
        }
        return picks;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private League mapLeague(ResultSet rs) throws SQLException {
        int startingLives = 3;
        try {
            startingLives = rs.getInt("starting_lives");
            if (rs.wasNull()) startingLives = 3;
        } catch (SQLException ignored) {
            // Column not yet present in very old DB files
        }
        return new League(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("join_code"),
                rs.getInt("host_id"),
                rs.getInt("max_players"),
                League.Status.valueOf(rs.getString("status")),
                rs.getInt("current_round"),
                startingLives
        );
    }
}
