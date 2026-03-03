package dev.revisor.revisor.programa;

import dev.revisor.revisor.db.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Classe principal JavaFX da aplicação Revisor.
 */
public class Aplicacao extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // inicializa o banco de dados (cria tabelas se não existirem)
        DatabaseManager.init();

        FXMLLoader fxmlLoader =
                new FXMLLoader(Aplicacao.class.getResource("/dev/revisor/revisor/Dashboard.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Revisor");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }
}

