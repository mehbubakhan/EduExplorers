package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.stage.Stage;

/**
 * Level-selection screen controller for Math tests.
 * Routes to the specific Math test screens, or back to the main test hub.
 */
public class MathTestFormatController {

    // ---- Navigation to specific Math test screens ----

    @FXML
    private void goBanglaNumber(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-bangla-number.fxml");
    }

    @FXML
    private void goBanglaNumberSpelling(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-bangla-number-spelling.fxml");
    }

    @FXML
    private void goBanglaLevel1(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-bangla-level1.fxml");
    }

    @FXML
    private void goNumber(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-number.fxml");
    }

    @FXML
    private void goSpelling(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-number-spelling.fxml");
    }

    @FXML
    private void goLevel1(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-level1.fxml");
    }

    @FXML
    private void goMedium(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-medium.fxml");
    }

    @FXML
    private void goHard(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "math-test-hard.fxml");
    }

    // ---- Back to test hub ----

    @FXML
    private void BackToTest(ActionEvent event) {
        try {
            ChangeFxmlController.switchScene(event, "testfield.fxml");
        } catch (Throwable t) {
            // Fallback: if helper isn't available, just close the window
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.close();
        }
    }
}
