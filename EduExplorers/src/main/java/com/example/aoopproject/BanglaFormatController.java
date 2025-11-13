package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class BanglaFormatController {

    @FXML
    void BackTolearn(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"learn.fxml");
    }

    @FXML
    void NextToBenjonborno(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"bangla-benjonborno.fxml");
    }

    @FXML
    void NextToChinho(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"bangla-chinho.fxml");
    }

    @FXML
    void NextToShoroborno(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"bangla-shoroborno.fxml");
    }
    @FXML
    void NextToShorobornoWord(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"bangla-shoroborno-word.fxml");
    }
    @FXML
    void NextToBenjonbornoWord(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"bangla-benjonborno-word.fxml");
    }
    @FXML
    private void switchToBanglaFormat(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"bangla-format.fxml");
    }

}
