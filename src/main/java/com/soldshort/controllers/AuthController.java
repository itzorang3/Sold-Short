package com.soldshort.controllers;

import com.soldshort.data.DataManager;
import com.soldshort.dto.LoginRequest;
import com.soldshort.dto.UpdatePasswordRequest;
import com.soldshort.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user authentication.
 *
 * POST /api/auth/login     — validate credentials, return User
 * POST /api/auth/register  — create account, return User
 * GET  /api/users          — look up user by username (?username=X)
 * GET  /api/users/{id}     — look up user by ID
 * POST /api/users/password — reset password
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final DataManager dm = DataManager.getInstance();

    /** Login: validate username + password, return the User or 401. */
    @PostMapping("/auth/login")
    public ResponseEntity<User> login(@RequestBody LoginRequest req) {
        User user = dm.validateLogin(req.getUsername(), req.getPassword());
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(user);
    }

    /** Register: create a new account, return the User or 409 if taken. */
    @PostMapping("/auth/register")
    public ResponseEntity<User> register(@RequestBody LoginRequest req) {
        if (dm.getUserByUsername(req.getUsername()) != null) {
            return ResponseEntity.status(409).build(); // conflict — username taken
        }
        User user = dm.createUser(req.getUsername(), req.getPassword(),
                req.getEmail() == null || req.getEmail().isBlank() ? null : req.getEmail());
        if (user == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(user);
    }

    /** Look up a user by username query param. */
    @GetMapping("/users")
    public ResponseEntity<User> getUserByUsername(@RequestParam String username) {
        User user = dm.getUserByUsername(username);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    /** Look up a user by ID. */
    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable int id) {
        User user = dm.getUserById(id);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    /** Reset a user's password. */
    @PostMapping("/users/password")
    public ResponseEntity<Void> updatePassword(@RequestBody UpdatePasswordRequest req) {
        boolean ok = dm.updatePassword(req.getUsername(), req.getNewPassword());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
