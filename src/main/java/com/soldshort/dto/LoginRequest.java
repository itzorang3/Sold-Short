package com.soldshort.dto;

public class LoginRequest {
    private String username;
    private String password;
    private String email; // only used during registration

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail()    { return email; }
    public void setUsername(String u) { this.username = u; }
    public void setPassword(String p) { this.password = p; }
    public void setEmail(String e)    { this.email    = e; }
}
