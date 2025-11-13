//module com.example.aoopproject {
//    requires javafx.controls;
//    requires javafx.fxml;
//    requires javafx.base;
//    requires com.example.aoopproject;
//
//    // For Application subclass (HelloApplication)
//    exports com.example.aoopproject;
//
//    // For controllers inside this package
//    opens com.example.aoopproject to javafx.fxml;
//
//    // If you also have controllers in com.example.aoop_project
//    opens com.example.aoop_project to javafx.fxml;
//}


//module com.example.aoopproject {
//    requires javafx.controls;
//    requires javafx.fxml;
//    requires javafx.base;
//    requires javafx.media;
//
//    exports com.example.aoopproject;
//    opens com.example.aoopproject to javafx.fxml;
//}

module com.example.aoopproject {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;
    requires java.desktop;
    requires javafx.media;
//    requires javafx.media

    exports com.example.aoopproject;
    opens com.example.aoopproject to javafx.fxml;
}
