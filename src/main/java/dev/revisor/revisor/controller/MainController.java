package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.MateriaDao;
import dev.revisor.revisor.model.Materia;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * Controller do layout principal (sidebar + área de conteúdo).
 * Troca dinamicamente o centro do BorderPane ao clicar nos itens da navegação.
 */
public class MainController implements Initializable {

    private static final String VIEWS_BASE = "/dev/revisor/revisor/views/";

    @FXML
    private StackPane contentArea;

    @FXML
    private HBox navDashboard;
    @FXML
    private HBox navMateriais;
    @FXML
    private HBox navQuestoes;
    @FXML
    private HBox navDesempenho;
    @FXML
    private HBox navPlanejamento;
    @FXML
    private HBox navRevisoes;
    @FXML
    private HBox navDebug;

    private final MateriaDao materiaDao = new MateriaDao();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupNavHovers();
        loadView("DashboardView");
    }

    private void setupNavHovers() {
        HBox[] navItems = {navDashboard, navMateriais, navQuestoes,
                navDesempenho, navPlanejamento, navRevisoes, navDebug};
        String[] views = {"DashboardView", "MateriaisView", "QuestoesView",
                "DesempenhoView", "PlanejamentoView", "RevisoesView", "PipelineDebugView"};

        for (int i = 0; i < navItems.length; i++) {
            HBox item = navItems[i];
            if (item == null) continue;          // ← ignora itens não injetados pelo FXML
            String view = views[i];
            item.setOnMouseClicked(e -> {
                for (HBox n : navItems) {
                    if (n != null) n.getStyleClass().remove("nav-active");
                }
                item.getStyleClass().add("nav-active");
                loadView(view);
            });
        }
    }

    /**
     * Carrega um FXML na área central e substitui o conteúdo anterior.
     *
     * @param viewName Nome do arquivo sem extensão (ex: "DashboardView")
     */
    private void loadView(String viewName) {
        try {
            String path = VIEWS_BASE + viewName + ".fxml";
            VBox view = FXMLLoader.load(getClass().getResource(path));
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (IOException ex) {
            throw new RuntimeException("Erro ao carregar view: " + viewName, ex);
        }
    }

    /**
     * Ação do link \"+ Adicionar matéria\" na sidebar.
     * Cria um objeto Materia e salva no banco.
     */
    @FXML
    private void onAddSubject(javafx.scene.input.MouseEvent e) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nova matéria");
        dialog.setHeaderText("Adicionar nova matéria");
        dialog.setContentText("Nome da matéria:");

        dialog.showAndWait().ifPresent(nome -> {
            String trimmed = nome.trim();
            if (trimmed.isEmpty()) {
                mostrarAlerta("Informe um nome válido para a matéria.");
                return;
            }

            // Cor padrão por enquanto; depois você pode pedir em outro diálogo
            String corPadrao = "#4f9cf9";

            try {
                Materia materia = materiaDao.inserir(trimmed, corPadrao);
                System.out.println("Matéria criada: " + materia);
                mostrarAlerta("Matéria \"" + materia.getNome() + "\" adicionada com sucesso!");
                // Aqui, se quiser, você pode atualizar a lista visual de matérias na sidebar.
            } catch (SQLException ex) {
                ex.printStackTrace();
                mostrarAlerta("Erro ao salvar matéria: " + ex.getMessage());
            }
        });
    }

    @FXML
    private void onSettings(javafx.scene.input.MouseEvent e) {
        System.out.println("Abrir configurações ⚙");
    }

    private void mostrarAlerta(String mensagem) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Revisor");
        alert.setHeaderText(null);
        alert.setContentText(mensagem);
        alert.showAndWait();
    }
}

