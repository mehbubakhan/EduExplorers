package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class LearnController {

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
    private void handleBtn5(ActionEvent event) {
        System.out.println("Button 5 clicked");
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
    void NextToAlphabetFormat(ActionEvent event){
        ChangeFxmlController.switchScene(event,"english-format.fxml");
    }
    @FXML
    private void switchToNumber(ActionEvent event){
        ChangeFxmlController.switchScene(event,"math-format.fxml");
    }
    @FXML
    private void switchToShape(ActionEvent event){
        ChangeFxmlController.switchScene(event,"shape.fxml");
    }
    @FXML
    private void switchToColor(ActionEvent event){
        ChangeFxmlController.switchScene(event,"bangla-english-format.fxml");
    }
    @FXML
    private void switchToLearn(ActionEvent event){
        ChangeFxmlController.switchScene(event,"learn.fxml");
    }
    @FXML
    void NextToBanglaFormat(ActionEvent event){
        ChangeFxmlController.switchScene(event,"bangla-format.fxml");
    }


}
