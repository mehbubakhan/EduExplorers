package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class EnglishFormatController {

    @FXML
    void BackToLearn(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "learn.fxml");
    }

    @FXML
    void NextToLowercaseAlphabet(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-lowercase-alphabet.fxml");
    }

    @FXML
    void NextToUppercaseAlphabet(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-uppercase-alphabet.fxml");
    }

    @FXML
    void NextToWordMaking(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-word-making.fxml");
    }

    @FXML
    void NextToRhyme(ActionEvent event) {
        ChangeFxmlController.switchScene(event, "english-rhyme.fxml");
    }
}
