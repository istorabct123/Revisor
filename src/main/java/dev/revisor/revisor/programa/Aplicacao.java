    package dev.revisor.revisor.programa;

    import dev.revisor.revisor.db.DatabaseManager;
    import javafx.application.Application;
    import javafx.fxml.FXMLLoader;
    import javafx.scene.Scene;
    import javafx.scene.image.Image;
    import javafx.stage.Stage;

    import java.io.IOException;
    import java.io.InputStream;
    import java.sql.SQLException;

    /**
     * Classe principal JavaFX da aplicação Revisor.
     */
    public class Aplicacao extends Application {

        @Override
        public void start(Stage stage) throws IOException, SQLException {
            // Inicializa o banco de dados
            DatabaseManager.init();
            var rs2 = DatabaseManager.getConnection()
                    .createStatement()
                    .executeQuery("SELECT name FROM sqlite_master WHERE type='table'");
            System.out.println("Tabelas encontradas:");
            while (rs2.next()) {
                System.out.println("- " + rs2.getString(1));
            }

            // ── Ícone da janela e da taskbar ──────────────────────────────
            InputStream iconStream = Aplicacao.class
                    .getResourceAsStream("/dev/revisor/revisor/revisor.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }

            // ── Carregar UI ───────────────────────────────────────────────
            FXMLLoader fxmlLoader =
                new FXMLLoader(Aplicacao.class.getResource("/dev/revisor/revisor/Dashboard.fxml"));

            Scene scene = new Scene(fxmlLoader.load(), 320, 240);
            stage.setTitle("Revisor");
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();
        }
    }
