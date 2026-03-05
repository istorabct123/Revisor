package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.MateriaDao;
import dev.revisor.revisor.model.Materia;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private static final String VIEWS_BASE = "/dev/revisor/revisor/views/";

    @FXML private StackPane contentArea;
    @FXML private HBox navDashboard, navMateriais, navQuestoes;
    @FXML private HBox navDesempenho, navPlanejamento, navRevisoes, navDebug;
    @FXML private VBox materiasContainer;

    private final MateriaDao materiaDao = new MateriaDao();
    private HBox navAtivo = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupNav();
        carregarMateriasSidebar();
        navegarPara("DashboardView", navDashboard);
    }

    // ══════════════════════════════════════════════════════════════════════
    // NAVEGAÇÃO
    // ══════════════════════════════════════════════════════════════════════

    private void setupNav() {
        HBox[]    items = {navDashboard, navMateriais, navQuestoes, navDesempenho, navPlanejamento, navRevisoes, navDebug};
        String[] views  = {"DashboardView","MateriaisView","QuestoesView","DesempenhoView","PlanejamentoView","RevisoesView","PipelineDebugView"};
        for (int i = 0; i < items.length; i++) {
            HBox item = items[i]; if (item == null) continue;
            String view = views[i];
            item.setOnMouseClicked(e -> navegarPara(view, item));
        }
    }

    private void navegarPara(String viewName, HBox navItem) {
        desativarTodos();
        if (navItem != null) navItem.getStyleClass().add("nav-active");
        navAtivo = navItem;
        loadView(viewName);
    }

    private void loadView(String viewName) {
        try {
            VBox view = FXMLLoader.load(getClass().getResource(VIEWS_BASE + viewName + ".fxml"));
            contentArea.getChildren().setAll(view);
        } catch (IOException ex) { throw new RuntimeException("Erro ao carregar: " + viewName, ex); }
    }

    private void desativarTodos() {
        for (HBox n : new HBox[]{navDashboard, navMateriais, navQuestoes, navDesempenho, navPlanejamento, navRevisoes, navDebug})
            if (n != null) n.getStyleClass().remove("nav-active");
        if (materiasContainer == null) return;
        materiasContainer.getChildren().forEach(node -> {
            if (node instanceof HBox h && h.getStyle().contains("rgba(79,156,249,0.12)"))
                h.setStyle("-fx-cursor:hand;");
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // SIDEBAR DE MATÉRIAS
    // ══════════════════════════════════════════════════════════════════════

    public void carregarMateriasSidebar() {
        if (materiasContainer == null) return;
        materiasContainer.getChildren().clear();
        try {
            for (Materia m : materiaDao.listar())
                materiasContainer.getChildren().add(criarItemMateria(m));
        } catch (SQLException e) { e.printStackTrace(); }

        Label addLink = new Label("+ Adicionar matéria");
        addLink.getStyleClass().add("add-subject-link");
        addLink.setOnMouseClicked(this::onAddSubject);
        materiasContainer.getChildren().add(addLink);
    }

    private HBox criarItemMateria(Materia materia) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("subject-item");
        item.setStyle("-fx-cursor:hand;");

        String cor = materia.getCor() != null && !materia.getCor().isBlank() ? materia.getCor() : "#4f9cf9";

        Rectangle dot = new Rectangle(8, 8);
        dot.setArcWidth(3); dot.setArcHeight(3);
        dot.setFill(javafx.scene.paint.Color.web(cor));

        Label lblNome = new Label(materia.getNome());
        lblNome.getStyleClass().add("subject-name");
        HBox.setHgrow(lblNome, Priority.ALWAYS);

        int count = contarQuestoes(materia.getId());
        Label lblCount = new Label(String.valueOf(count));
        lblCount.setStyle("-fx-text-fill:#6b7280;-fx-font-size:11px;");

        // ✏ Editar matéria (nome + cor)
        Label btnEdit = new Label("✏");
        btnEdit.setStyle("-fx-font-size:10px;-fx-text-fill:#6b7280;-fx-cursor:hand;-fx-padding:0 4 0 0;");
        btnEdit.setVisible(false); // aparece no hover
        btnEdit.setOnMouseClicked(e -> { e.consume(); editarMateria(materia); });

        item.getChildren().addAll(dot, lblNome, lblCount, btnEdit);

        // Hover: mostra botão editar
        item.setOnMouseEntered(e -> {
            btnEdit.setVisible(true);
            if (!item.getStyle().contains("rgba(79,156,249,0.12)"))
                item.setStyle("-fx-cursor:hand;-fx-background-color:rgba(255,255,255,0.05);-fx-background-radius:8;");
        });
        item.setOnMouseExited(e -> {
            btnEdit.setVisible(false);
            if (!item.getStyle().contains("rgba(79,156,249,0.12)"))
                item.setStyle("-fx-cursor:hand;");
        });

        // Clique → abre Questões filtradas
        item.setOnMouseClicked(e -> {
            desativarTodos();
            item.setStyle("-fx-cursor:hand;-fx-background-color:rgba(79,156,249,0.12);-fx-background-radius:8;");
            abrirQuestoesComMateria(materia);
        });

        return item;
    }

    private void abrirQuestoesComMateria(Materia materia) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(VIEWS_BASE + "QuestoesView.fxml"));
            VBox view = loader.load();
            QuestoesViewController ctrl = loader.getController();
            ctrl.setFiltroMateriaId(materia.getId());
            ctrl.aplicarFiltroPorMateria(materia);
            contentArea.getChildren().setAll(view);
        } catch (IOException ex) { throw new RuntimeException("Erro ao abrir Questões", ex); }
    }

    private int contarQuestoes(int materiaId) {
        try (var st = dev.revisor.revisor.db.DatabaseManager.getConnection().createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM questao WHERE materia_id=" + materiaId)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ✏ EDITAR MATÉRIA
    // ══════════════════════════════════════════════════════════════════════

    private void editarMateria(Materia materia) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar matéria");
        dialog.setHeaderText(null);

        ButtonType salvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(salvar, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding:16;");

        Label lblN = new Label("Nome da matéria:");
        lblN.setStyle("-fx-font-size:12px;-fx-text-fill:#9ca3af;");
        TextField campoNome = new TextField(materia.getNome());
        campoNome.setMaxWidth(Double.MAX_VALUE);

        Label lblC = new Label("Cor (ex: #4f9cf9):");
        lblC.setStyle("-fx-font-size:12px;-fx-text-fill:#9ca3af;");
        TextField campoCor = new TextField(materia.getCor() != null ? materia.getCor() : "#4f9cf9");
        campoCor.setMaxWidth(Double.MAX_VALUE);

        // Preview da cor
        Label preview = new Label("  ■■■  " + materia.getNome());
        preview.setStyle("-fx-font-size:13px;-fx-text-fill:" + campoCor.getText() + ";");
        campoCor.textProperty().addListener((obs, o, n) -> {
            preview.setText("  ■■■  " + campoNome.getText());
            preview.setStyle("-fx-font-size:13px;-fx-text-fill:" + n + ";");
        });
        campoNome.textProperty().addListener((obs, o, n) ->
            preview.setText("  ■■■  " + n));

        // Paleta de cores rápidas
        HBox paleta = new HBox(8);
        paleta.setAlignment(Pos.CENTER_LEFT);
        String[] cores = {"#4f9cf9","#38d9a9","#f093fb","#ffd93d","#ff6b6b","#ff8c42","#a78bfa","#06b6d4"};
        for (String c : cores) {
            Label dot = new Label("●");
            dot.setStyle("-fx-font-size:18px;-fx-text-fill:" + c + ";-fx-cursor:hand;");
            dot.setOnMouseClicked(e -> campoCor.setText(c));
            paleta.getChildren().add(dot);
        }

        content.getChildren().addAll(lblN, campoNome, lblC, campoCor, paleta, preview);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(380);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != salvar) return;
            String novoNome = campoNome.getText().trim();
            String novaCor  = campoCor.getText().trim();
            if (novoNome.isEmpty()) return;
            try {
                materiaDao.atualizar(materia.getId(), novoNome, novaCor);
                carregarMateriasSidebar();
            } catch (SQLException ex) { ex.printStackTrace(); mostrarAlerta("Erro: " + ex.getMessage()); }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // + ADICIONAR MATÉRIA
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void onAddSubject(javafx.scene.input.MouseEvent e) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Nova matéria");
        dialog.setHeaderText(null);

        ButtonType salvar = new ButtonType("Criar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(salvar, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding:16;");

        Label lblN = new Label("Nome da matéria:");
        lblN.setStyle("-fx-font-size:12px;-fx-text-fill:#9ca3af;");
        TextField campoNome = new TextField();
        campoNome.setPromptText("Ex: História, Matemática...");
        campoNome.setMaxWidth(Double.MAX_VALUE);

        Label lblC = new Label("Escolha uma cor:");
        lblC.setStyle("-fx-font-size:12px;-fx-text-fill:#9ca3af;");

        String[] cores = {"#4f9cf9","#38d9a9","#f093fb","#ffd93d","#ff6b6b","#ff8c42","#a78bfa","#06b6d4"};
        String[] corEscolhida = {cores[0]};

        HBox paleta = new HBox(8);
        paleta.setAlignment(Pos.CENTER_LEFT);
        Label[] dots = new Label[cores.length];
        for (int i = 0; i < cores.length; i++) {
            final String c = cores[i]; final int idx = i;
            dots[i] = new Label("●");
            dots[i].setStyle("-fx-font-size:18px;-fx-text-fill:" + c + ";-fx-cursor:hand;" +
                             (i == 0 ? "-fx-effect:dropshadow(gaussian," + c + ",6,0.8,0,0);" : ""));
            dots[i].setOnMouseClicked(ev -> {
                corEscolhida[0] = c;
                for (int j = 0; j < cores.length; j++)
                    dots[j].setStyle("-fx-font-size:18px;-fx-text-fill:" + cores[j] + ";-fx-cursor:hand;" +
                        (j == idx ? "-fx-effect:dropshadow(gaussian," + c + ",6,0.8,0,0);" : ""));
            });
            paleta.getChildren().add(dots[i]);
        }

        content.getChildren().addAll(lblN, campoNome, lblC, paleta);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(360);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != salvar) return;
            String nome = campoNome.getText().trim();
            if (nome.isEmpty()) { mostrarAlerta("Informe um nome."); return; }
            try {
                materiaDao.inserir(nome, corEscolhida[0]);
                carregarMateriasSidebar();
            } catch (SQLException ex) { ex.printStackTrace(); mostrarAlerta("Erro: " + ex.getMessage()); }
        });
    }

    @FXML private void onSettings(javafx.scene.input.MouseEvent e) {
        System.out.println("Configurações ⚙");
    }

    private void mostrarAlerta(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Revisor"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
