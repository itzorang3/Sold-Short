package com.soldshort.dto;

public class UpdatePasswordRequest {
    private String username;
    private String newPassword;

    public String getUsername()    { return username; }
    public String getNewPassword() { return newPassword; }
    public void setUsername(String v)    { this.username    = v; }
    public void setNewPassword(String v) { this.newPassword = v; }
}
