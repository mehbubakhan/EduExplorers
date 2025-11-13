package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class TestFieldController {

    @FXML
    private void handleBtn1(ActionEvent event) {
        System.out.println("Button 1 clicked");
    }

    @FXML
    private void handleBtn2(ActionEvent event) {
        System.out.println("Button 2 clicked");
    }

    @FXML
    private void handleBtn3(ActionEvent event) {
        System.out.println("Button 3 clicked");
    }

    @FXML
    private void handleBtn4(ActionEvent event) {
        System.out.println("Button 4 clicked");
    }

    @FXML
    private void handleBackToMenu(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("menu.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }
    @FXML
    void NextToAlphabetTest(ActionEvent event){
        ChangeFxmlController.switchScene(event,"english-test-format.fxml");
    }
    @FXML
    void NextToNumberTest(ActionEvent event){
        ChangeFxmlController.switchScene(event,"math-test-format.fxml");
    }
    @FXML
    void NextToBanglaTest(ActionEvent event){
        ChangeFxmlController.switchScene(event,"bangla-alphabet-test-format.fxml");
    }
    @FXML
    void NextToShapeTestFormat(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"shape-test-format.fxml");
    }
    @FXML
    private void switchToTest(ActionEvent event){
        ChangeFxmlController.switchScene(event,"testfield.fxml");
    }

}
