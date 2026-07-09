package com.metorrent.app;

import com.metorrent.gui.controllers.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * meTorrent - Peer-to-Peer File Distribution Engine.
 * JavaFX application entry point: bootstraps the backend (server + client +
 * transfer engine) via {@link AppContext}, then loads the GUI shell on top
 * of it.
 */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private AppContext context;

    @Override
    public void start(Stage primaryStage) throws IOException {
        context = AppContext.bootstrap();

        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/main.fxml"),
                "main.fxml not found on classpath"));
        Parent root = loader.load();

        MainController controller = loader.getController();
        controller.init(context);

        primaryStage.setTitle("meTorrent - Peer-to-Peer File Distribution Engine");
        primaryStage.setScene(new Scene(root, 1100, 720));
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setOnCloseRequest(e -> context.shutdown());
        primaryStage.show();

        log.info("meTorrent GUI started");
    }

    @Override
    public void stop() {
        if (context != null) {
            context.shutdown();
        }
        log.info("meTorrent shutting down");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
