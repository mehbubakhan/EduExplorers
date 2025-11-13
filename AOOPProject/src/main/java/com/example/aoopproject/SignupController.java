package com.example.aoopproject;

import com.example.aoopproject.AuthService;
import com.example.aoopproject.LineClient;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class SignupController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button signupButton;
    @FXML private Button cancelButton;
    @FXML private Button backButton;

    // if you ever add an email field, wire it here
    // for now we’ll reuse the username as dummy email (server expects 3 values)
    private final AuthService auth = new AuthService(new LineClient("localhost", 6000));

    @FXML
    private void handleSignup(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();

        if (username.isBlank() || password.isBlank() || confirm.isBlank()) {
            showAlert("Missing fields", "Please fill all fields.");
            return;
        }
        if (!password.equals(confirm)) {
            showAlert("Password mismatch", "Passwords do not match.");
            return;
        }

        signupButton.setDisable(true);

        Task<AuthService.Result> task = new Task<>() {
            @Override
            protected AuthService.Result call() {
                // using username as dummy email until you add an email input
                return auth.signup(username, username + "@example.com", password);
            }
        };

        task.setOnSucceeded(ev -> {
            signupButton.setDisable(false);
            var r = task.getValue();
            if (r.ok()) {
                showAlert("Signup successful", "Account created successfully!");
                goTo("/com/example/aoopproject/Login.fxml", "Login Page", event);
            } else {
                showAlert("Signup failed", r.message());
            }
        });

        task.setOnFailed(ev -> {
            signupButton.setDisable(false);
            String msg = task.getException() == null ? "Unknown error" : task.getException().getMessage();
            showAlert("Network error", msg);
        });

        new Thread(task, "signup-task").start();
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        showAlert("Google Sign-Up", "This button is not yet connected.");
    }

    @FXML
    private void handleBack(ActionEvent event) {
        goTo("/com/example/aoopproject/startup.fxml", "Startup Page", event);
    }

    private void goTo(String fxml, String title, ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle(title);
            stage.show();
        } catch (Exception e) {
            showAlert("Navigation error", e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(String header, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Info");
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }
}
