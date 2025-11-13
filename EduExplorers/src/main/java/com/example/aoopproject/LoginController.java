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

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button backButton;

    // Point this at your server
    private final AuthService auth = new AuthService(new LineClient("localhost", 6000));

    @FXML
    private void handleLogin(ActionEvent event) {
        String u = usernameField.getText();
        String p = passwordField.getText();

        loginButton.setDisable(true);

        Task<AuthService.Result> task = new Task<>() {
            @Override
            protected AuthService.Result call() {
                return auth.login(u, p);
            }
        };

        task.setOnSucceeded(ev -> {
            var r = task.getValue();
            loginButton.setDisable(false);
            if (r.ok()) {
                // success → go to menu
                goTo("/com/example/aoopproject/menu.fxml", "Menu Page", event);
            } else {
                // stay here and show why
                showAlert("Login failed", r.message());
            }
        });

        task.setOnFailed(ev -> {
            loginButton.setDisable(false);
            String msg = task.getException() == null ? "Unknown error" : task.getException().getMessage();
            showAlert("Login error", msg);
        });

        new Thread(task, "login-task").start();
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
