package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class MathFormatController {

    @FXML
    void BackToLearn(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"learn.fxml");
    }

    @FXML
        void NextToNumberBangla(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"math-number-bangla.fxml");
    }

    @FXML
    void NextToNumberBanglaSpelling(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"math-number-bangla-spelling.fxml");
    }

    @FXML
    void NextToNumberEnglish(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"math-number-english.fxml");
    }

    @FXML
    void NextToNumberEnglishSpelling(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"math-number-english-spelling.fxml");
    }
    @FXML
    private void switchToMathFormat(ActionEvent event) {
        ChangeFxmlController.switchScene(event,"mathformat.fxml");
    }

}
