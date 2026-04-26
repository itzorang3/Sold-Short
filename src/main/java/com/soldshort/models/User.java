package com.soldshort.models;

/**
 * Represents a registered user of the Sold Short application.
 * Stores authentication credentials and a unique identifier.
 */
public class User {

    private int    id;
    private String username;
    private String password;
    private String email;

    public User() {}

    public User(int id, String username, String password, String email) {
        this.id       = id;
        this.username = username;
        this.password = password;
        this.email    = email;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int    getId()       { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail()    { return email; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setId(int id)             { this.id       = id; }
    public void setUsername(String u)     { this.username = u; }
    public void setPassword(String p)     { this.password = p; }
    public void setEmail(String e)        { this.email    = e; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "'}";
    }
}
