package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.stage.Stage;

public class EnglishTestFormatController {

    /* ===== Level routes ===== */

    @FXML
    private void chooseUppercase(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-test-uppercase.fxml");
    }

    @FXML
    private void chooseLowercase(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-test-lowercase.fxml");
    }

    @FXML
    private void chooseWordmaking(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-test-wordmaking.fxml");
    }

    @FXML
    private void chooseMedium(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-test-medium.fxml");
    }

    /** ✅ Hard route (this is the connection you asked for) */
    @FXML
    private void chooseHard(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-test-hard.fxml");
    }

    /* ===== Back ===== */

    @FXML
    private void BackToTest(ActionEvent event) {
        try {
            ChangeFxmlController.switchScene(event, "testfield.fxml");
        } catch (Throwable t) {
            // Optional fallback if testfield.fxml isn't present
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.close();
        }
    }
}
