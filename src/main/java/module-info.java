module com.example.musicapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.sql;
    requires mysql.connector.j;

    opens com.musicapp.ui to javafx.fxml;
    exports com.musicapp.ui;
    exports com.musicapp;
}