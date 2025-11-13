package com.example.aoopproject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class BanglaAlphabetTestFormatController {

    @FXML private void gotoShoroborno(ActionEvent e){
        ChangeFxmlController.switchScene(e, "bangla-shoroborno-test.fxml");
    }
    @FXML private void gotoBenjonborno(ActionEvent e){
        ChangeFxmlController.switchScene(e, "bangla-benjonborno-test.fxml");
    }
    @FXML private void gotoMedium(ActionEvent e){
        ChangeFxmlController.switchScene(e, "bangla-medium-test.fxml");
    }
    @FXML private void gotoHard(ActionEvent e){
        ChangeFxmlController.switchScene(e, "bangla-hard-test.fxml");
    }
    @FXML private void BackToTest(ActionEvent e){
        ChangeFxmlController.switchScene(e, "testfield.fxml");
    }
}
