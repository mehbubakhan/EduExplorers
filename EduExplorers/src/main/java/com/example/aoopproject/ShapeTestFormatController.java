package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Level-selection screen controller.
 * Loads one of the four quiz screens, or navigates back to your test field.
 */
public class ShapeTestFormatController {

    // ====== Level choices ======

    @FXML
    private void chooseShapeOnly(ActionEvent e) throws IOException {
        load(e, "shape-test-shape.fxml");
    }

    @FXML
    private void chooseColorOnly(ActionEvent e) throws IOException {
        load(e, "shape-test-color.fxml");
    }

    @FXML
    private void chooseMedium(ActionEvent e) throws IOException {
        load(e, "shape-test-medium.fxml");
    }

    @FXML
    private void chooseHard(ActionEvent e) throws IOException {
        load(e, "shape-test-hard.fxml");
    }
    @FXML
    private void switchToShapeTestFormat(ActionEvent event){
        ChangeFxmlController.switchScene(event,"shape-test-format.fxml");
    }
   @FXML
   void NextToShapeTestColor(ActionEvent event){
        ChangeFxmlController.switchScene(event,"shape-test-color.fxml");
   }
    @FXML
    private void NextToShapeTestShape(ActionEvent event){
        ChangeFxmlController.switchScene(event,"shape-test-shape.fxml");
    }


    // ====== Back navigation ======

    @FXML
    private void BackToTest(ActionEvent event) {
        // Keep identical behavior to your original controller:
        // go back to the test hub screen via your existing helper.
        try {
            ChangeFxmlController.switchScene(event, "testfield.fxml");
        } catch (Throwable t) {
            // Fallback: if ChangeFxmlController isn't available in this context,
            // just close the window to avoid leaving the UI stuck.
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.close();
        }
    }

    // ====== Helper ======

    private void load(ActionEvent e, String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }
}
