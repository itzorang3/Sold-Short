package com.soldshort;

import com.soldshort.data.DatabaseConnection;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.PreDestroy;

/**
 * Spring Boot entry point for the Sold Short REST API server.
 *
 * Start locally:
 *   cd server
 *   mvn spring-boot:run
 *
 * Or build a fat JAR and run it:
 *   mvn package
 *   java -jar target/sold-short-server-1.0-SNAPSHOT.jar
 *
 * Deploy to Railway:
 *   Push the server/ folder to a GitHub repo and connect it to a Railway project.
 *   Set the start command to:  java -jar target/sold-short-server-1.0-SNAPSHOT.jar
 */
@SpringBootApplication
public class ServerApp {

    public static void main(String[] args) {
        SpringApplication.run(ServerApp.class, args);
    }

    /** Close the SQLite connection cleanly on shutdown. */
    @PreDestroy
    public void onShutdown() {
        DatabaseConnection.closeConnection();
    }
}
