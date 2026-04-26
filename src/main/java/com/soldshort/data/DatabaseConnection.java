package com.soldshort.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provides a single shared SQLite JDBC connection for the server.
 *
 * The database file path is read from the DB_PATH environment variable,
 * falling back to "soldshort.db" in the current working directory.
 * On Railway, set DB_PATH to a persistent volume path (e.g. /data/soldshort.db).
 */
public class DatabaseConnection {

    private static final String DB_PATH =
            System.getenv().getOrDefault("DB_PATH", "soldshort.db");
    private static final String DB_URL  = "jdbc:sqlite:" + DB_PATH;

    private static Connection connection;

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            connection.createStatement().execute("PRAGMA foreign_keys = ON;");
        }
        return connection;
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Warning: could not close DB connection — " + e.getMessage());
            }
        }
    }
}
