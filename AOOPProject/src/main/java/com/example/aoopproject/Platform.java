package com.example.aoop_project;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;

public class Platform {

    @FXML private Button learnButton;
    @FXML private Button drawingButton;
    @FXML private Button testFeatureButton;
    @FXML private Button collaborationButton;

    @FXML
    private void handleLearn(ActionEvent event) throws IOException {
        loadScene(event, "menu.fxml");
    }

    @FXML
    private void handleDrawing(ActionEvent event) {
        System.out.println("Drawing button clicked — implement drawing page.");
    }

    @FXML
    private void handleTestFeature(ActionEvent event) {
        System.out.println("Test Feature clicked — implement test page.");
    }

    @FXML
    private void handleCollaboration(ActionEvent event) {
        System.out.println("Collaboration clicked — implement collaboration page.");
    }

    // small helper to switch scenes (loads FXML from same package resources)
    private void loadScene(ActionEvent event, String fxmlFile) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }
}