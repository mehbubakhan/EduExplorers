package com.example.aoopproject;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.event.ActionEvent;
import javafx.stage.Stage;
import java.io.IOException;

public class MenuController {

    @FXML
    private void handleBack(ActionEvent event) throws IOException {
        // Load login.fxml instead of welcome.fxml
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("Login Page");
        stage.show();
    }

    @FXML
    private void handleLearn(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("learn.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("Learn Page");
        stage.show();
    }

    @FXML
    private void handleFeatureA(ActionEvent event) {
        System.out.println("Drawing feature clicked — implement.");
    }

    @FXML
    private void handleFeatureB(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"collab-drawing.fxml");
            }

    @FXML
    private void handleTest(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("testfield.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("Test Page");
        stage.show();
    }
    @FXML
    void NextToDrawing(ActionEvent event){
        ChangeFxmlController.switchScene(event,"drawing.fxml");
    }
    @FXML
    private void switchToMenu(ActionEvent event){
        ChangeFxmlController.switchScene(event,"menu.fxml");
    }
}
