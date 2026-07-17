module org.example.newpl.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;


    opens org.example.newpl.demo to javafx.fxml;
    exports org.example.newpl.demo;
}