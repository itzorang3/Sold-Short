package com.soldshort.ui;

import com.soldshort.api.ApiClient;
import com.soldshort.models.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

/**
 * Login and Registration screen.
 *
 * All auth calls go to the REST server via {@link ApiClient}.
 * HTTP calls run on a background thread so the JavaFX UI stays responsive.
 */
public class LoginScreen {

    // ── Build ─────────────────────────────────────────────────────────────────

    public Pane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40, 50, 40, 50));
        card.setMaxWidth(420);
        card.getStyleClass().add("card");

        Label title = new Label("SOLD SHORT");
        title.getStyleClass().add("title-label");

        Label subtitle = new Label("Fantasy Stock Trading — Last Man Standing");
        subtitle.getStyleClass().add("subtitle-label");
        subtitle.setWrapText(true);
        subtitle.setTextAlignment(TextAlignment.CENTER);

        Separator sep = new Separator();
        sep.setPadding(new Insets(4, 0, 4, 0));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.getStyleClass().add("text-field");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("text-field");

        Label pwHint = new Label("Min. 6 characters required.");
        pwHint.getStyleClass().add("hint-label");
        pwHint.setVisible(false);
        pwHint.setManaged(false);

        passwordField.textProperty().addListener((obs, old, val) -> {
            if (!pwHint.isVisible()) return;
            if (val.length() >= 6) {
                pwHint.setStyle("-fx-text-fill: #4caf50;");
                pwHint.setText("✓ Looks good!");
            } else {
                pwHint.setStyle("-fx-text-fill: #e94560;");
                pwHint.setText("Min. 6 characters required.");
            }
        });

        TextField emailField = new TextField();
        emailField.setPromptText("Email (optional)");
        emailField.getStyleClass().add("text-field");
        emailField.setVisible(false);
        emailField.setManaged(false);

        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        Button actionBtn = new Button("LOG IN");
        actionBtn.getStyleClass().add("primary-btn");
        actionBtn.setMaxWidth(Double.MAX_VALUE);

        Label toggleLabel = new Label("Don't have an account?");
        toggleLabel.getStyleClass().add("hint-label");

        Hyperlink toggleLink = new Hyperlink("Register here");
        toggleLink.getStyleClass().add("link");

        HBox toggleRow = new HBox(6, toggleLabel, toggleLink);
        toggleRow.setAlignment(Pos.CENTER);

        Hyperlink forgotLink = new Hyperlink("Forgot password?");
        forgotLink.getStyleClass().add("link");
        forgotLink.setOnAction(e -> showForgotPasswordOverlay());

        HBox forgotRow = new HBox(forgotLink);
        forgotRow.setAlignment(Pos.CENTER);

        final boolean[] isRegister = { false };

        toggleLink.setOnAction(e -> {
            isRegister[0] = !isRegister[0];
            if (isRegister[0]) {
                actionBtn.setText("REGISTER");
                toggleLabel.setText("Already have an account?");
                toggleLink.setText("Log in here");
                emailField.setVisible(true);   emailField.setManaged(true);
                pwHint.setVisible(true);       pwHint.setManaged(true);
                forgotRow.setVisible(false);   forgotRow.setManaged(false);
            } else {
                actionBtn.setText("LOG IN");
                toggleLabel.setText("Don't have an account?");
                toggleLink.setText("Register here");
                emailField.setVisible(false);  emailField.setManaged(false);
                pwHint.setVisible(false);      pwHint.setManaged(false);
                forgotRow.setVisible(true);    forgotRow.setManaged(true);
            }
            statusLabel.setText("");
        });

        actionBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String email    = emailField.getText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                statusLabel.setText("Username and password are required.");
                statusLabel.getStyleClass().setAll("status-label", "error");
                return;
            }

            actionBtn.setDisable(true);
            statusLabel.setText("Connecting…");
            statusLabel.getStyleClass().setAll("status-label");

            if (isRegister[0]) {
                handleRegister(username, password, email, statusLabel, actionBtn);
            } else {
                handleLogin(username, password, statusLabel, actionBtn);
            }
        });

        passwordField.setOnAction(e -> actionBtn.fire());
        usernameField.setOnAction(e -> passwordField.requestFocus());

        card.getChildren().addAll(title, subtitle, sep,
                usernameField, passwordField, pwHint, emailField,
                statusLabel, actionBtn, toggleRow, forgotRow);

        StackPane centrePane = new StackPane(card);
        centrePane.getStyleClass().add("root-pane");
        root.setCenter(centrePane);
        return root;
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleLogin(String username, String password,
                             Label statusLabel, Button actionBtn) {
        new Thread(() -> {
            User user = ApiClient.get().login(username, password);
            Platform.runLater(() -> {
                actionBtn.setDisable(false);
                if (user == null) {
                    statusLabel.setText("Invalid username or password.");
                    statusLabel.getStyleClass().setAll("status-label", "error");
                } else {
                    MainApp.setCurrentUser(user);
                    MainApp.showMainMenu();
                }
            });
        }).start();
    }

    private void handleRegister(String username, String password,
                                String email, Label statusLabel, Button actionBtn) {
        if (password.length() < 6) {
            statusLabel.setText("Password must be at least 6 characters.");
            statusLabel.getStyleClass().setAll("status-label", "error");
            actionBtn.setDisable(false);
            return;
        }
        new Thread(() -> {
            // Check if taken first, then register
            boolean taken = ApiClient.get().usernameExists(username);
            if (taken) {
                Platform.runLater(() -> {
                    statusLabel.setText("Username '" + username + "' is already taken.");
                    statusLabel.getStyleClass().setAll("status-label", "error");
                    actionBtn.setDisable(false);
                });
                return;
            }
            User user = ApiClient.get().register(username, password,
                    email.isEmpty() ? null : email);
            Platform.runLater(() -> {
                actionBtn.setDisable(false);
                if (user == null) {
                    statusLabel.setText("Registration failed. Please try again.");
                    statusLabel.getStyleClass().setAll("status-label", "error");
                } else {
                    MainApp.setCurrentUser(user);
                    MainApp.showMainMenu();
                }
            });
        }).start();
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    private void showForgotPasswordOverlay() {
        TextField userField = new TextField();
        userField.setPromptText("Your username");
        userField.getStyleClass().add("text-field");

        PasswordField newPassField = new PasswordField();
        newPassField.setPromptText("New password");
        newPassField.getStyleClass().add("text-field");

        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Confirm new password");
        confirmPassField.getStyleClass().add("text-field");

        Label errorLabel = new Label("");
        errorLabel.getStyleClass().addAll("status-label", "error");
        errorLabel.setWrapText(true);

        Button resetBtn  = new Button("Reset Password");
        resetBtn.getStyleClass().add("primary-btn");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-btn");
        cancelBtn.setOnAction(e -> MainApp.hideOverlay());

        HBox btnRow = new HBox(12, resetBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        resetBtn.setOnAction(e -> {
            String username = userField.getText().trim();
            String newPass  = newPassField.getText();
            String confirm  = confirmPassField.getText();

            if (username.isEmpty() || newPass.isEmpty()) {
                errorLabel.setText("Username and new password are required.");
                return;
            }
            if (!newPass.equals(confirm)) {
                errorLabel.setText("Passwords do not match.");
                return;
            }

            resetBtn.setDisable(true);
            new Thread(() -> {
                boolean userExists = ApiClient.get().usernameExists(username);
                if (!userExists) {
                    Platform.runLater(() -> {
                        errorLabel.setText("No account found with that username.");
                        resetBtn.setDisable(false);
                    });
                    return;
                }
                boolean ok = ApiClient.get().updatePassword(username, newPass);
                Platform.runLater(() -> {
                    resetBtn.setDisable(false);
                    if (ok) {
                        MainApp.hideOverlay();
                        MainApp.showAlertOverlay("Password Reset",
                                "Password updated for '" + username + "'. You can now log in.");
                    } else {
                        errorLabel.setText("Reset failed. Please try again.");
                    }
                });
            }).start();
        });

        VBox form = new VBox(10,
                new Label("Enter your username and choose a new password:") {{
                    getStyleClass().add("hint-label"); setWrapText(true);
                }},
                userField, newPassField, confirmPassField, errorLabel, btnRow);

        MainApp.showFormOverlay("Reset Password", form);
    }
}
