package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.ArquivoDao;
import dev.revisor.revisor.db.DatabaseManager;
import dev.revisor.revisor.db.MateriaDao;
import dev.revisor.revisor.model.Arquivo;
import dev.revisor.revisor.model.Materia;
import dev.revisor.revisor.service.ArquivoParserService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller da aba Materiais.
 * Grade/Lista de PDFs com busca, filtro por matéria, stats e extração de questões.
 */
public class MateriaisViewController implements Initializable {

    // ── CONTROLES ─────────────────────────────────────────────────────────
    @FXML private Label    lblTotalArquivos;
    @FXML private TextField campoBuscaMaterial;
    @FXML private ComboBox<Materia> filtroMateriaMat;
    @FXML private Button   btnGrade, btnLista;

    // ── STATS e dica de monitoramento ──────────────────────────────────────
    @FXML private Label statArquivos, statQuestoes, statTamanho, statMaterias;
    @FXML private HBox boxDicaQuestoes;

    // ── CONTEÚDO ──────────────────────────────────────────────────────────
    @FXML private FlowPane gradeContainer;
    @FXML private VBox     listaContainer;
    @FXML private VBox     emptyMateriais;

    private final ArquivoDao arquivoDao = new ArquivoDao();
    private final MateriaDao materiaDao = new MateriaDao();

    private boolean modoGrade = true;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try {
            filtroMateriaMat.getItems().add(null);
            filtroMateriaMat.getItems().addAll(materiaDao.listar());
            filtroMateriaMat.setConverter(new javafx.util.StringConverter<>() {
                public String toString(Materia m)   { return m == null ? "Todas as matérias" : m.getNome(); }
                public Materia fromString(String s) { return null; }
            });
        } catch (SQLException e) { e.printStackTrace(); }

        campoBuscaMaterial.textProperty().addListener((obs, o, n) -> recarregar());
        filtroMateriaMat.valueProperty().addListener((obs, o, n) -> recarregar());

        recarregar();
    }

    // ══════════════════════════════════════════════════════════════════════
    // CARREGAR
    // ══════════════════════════════════════════════════════════════════════

    private void recarregar() {
        gradeContainer.getChildren().clear();
        listaContainer.getChildren().clear();

        try {
            List<Arquivo> todos = arquivoDao.listarTodos();
            String busca = campoBuscaMaterial.getText() != null
                    ? campoBuscaMaterial.getText().toLowerCase().trim() : "";
            Materia filtroM = filtroMateriaMat.getValue();

            List<Arquivo> filtrados = todos.stream()
                .filter(a -> busca.isEmpty() || a.getNomeOriginal().toLowerCase().contains(busca))
                .filter(a -> filtroM == null || (a.getMateriaId() != null && a.getMateriaId().equals(filtroM.getId())))
                .toList();

            lblTotalArquivos.setText(filtrados.size() + " arquivo" + (filtrados.size() != 1 ? "s" : ""));

            atualizarStats(todos);

            boolean empty = filtrados.isEmpty();
            emptyMateriais.setVisible(empty);
            emptyMateriais.setManaged(empty);
            gradeContainer.setVisible(!empty && modoGrade);
            gradeContainer.setManaged(!empty && modoGrade);
            listaContainer.setVisible(!empty && !modoGrade);
            listaContainer.setManaged(!empty && !modoGrade);

            for (Arquivo arq : filtrados) {
                if (modoGrade) {
                    gradeContainer.getChildren().add(criarCardGrade(arq));
                } else {
                    listaContainer.getChildren().add(criarLinhaLista(arq));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void atualizarStats(List<Arquivo> todos) throws SQLException {
        statArquivos.setText(String.valueOf(todos.size()));

        long totalBytes = todos.stream().mapToLong(Arquivo::getTamanho).sum();
        statTamanho.setText(formatarTamanho(totalBytes));

        // Questões no banco (monitoramento: 0 = banco ainda não alimentado)
        int totalQuestoes = 0;
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM questao")) {
            if (rs.next()) totalQuestoes = rs.getInt(1);
            statQuestoes.setText(String.valueOf(totalQuestoes));
        }
        if (boxDicaQuestoes != null) {
            boxDicaQuestoes.setVisible(totalQuestoes == 0);
            boxDicaQuestoes.setManaged(totalQuestoes == 0);
        }

        // Matérias cobertas
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(DISTINCT materia_id) FROM arquivo WHERE materia_id IS NOT NULL")) {
            statMaterias.setText(rs.next() ? String.valueOf(rs.getInt(1)) : "0");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CARD GRADE
    // ══════════════════════════════════════════════════════════════════════

    private VBox criarCardGrade(Arquivo arq) {
        VBox card = new VBox(10);
        card.getStyleClass().add("material-card");
        card.setPrefWidth(200);
        card.setMaxWidth(200);
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle("-fx-cursor: hand;");

        // Ícone PDF
        VBox iconBox = new VBox();
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setStyle("-fx-background-color: rgba(255,107,107,0.12); " +
                "-fx-background-radius: 12; -fx-padding: 20; -fx-min-height: 80;");
        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size: 32px;");
        iconBox.getChildren().add(icon);

        // Nome
        Label lNome = new Label(arq.getNomeOriginal());
        lNome.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #e8eaf0; " +
                "-fx-wrap-text: true; -fx-alignment: CENTER; -fx-text-alignment: CENTER;");
        lNome.setWrapText(true);
        lNome.setMaxWidth(180);

        // Info
        Label lInfo = new Label(formatarTamanho(arq.getTamanho()));
        lInfo.setStyle("-fx-font-size: 10px; -fx-text-fill: #6b7280; -fx-font-family: 'Courier New';");

        // Data
        String data = arq.getCreatedAt() != null ? arq.getCreatedAt().format(FMT) : "";
        Label lData = new Label(data);
        lData.setStyle("-fx-font-size: 10px; -fx-text-fill: #6b7280;");

        // Badges de questões extraídas
        int numQ = contarQuestoesPorArquivo(arq.getId());
        HBox badgesBox = new HBox(5);
        badgesBox.setAlignment(Pos.CENTER);
        if (numQ > 0) {
            Label badgeQ = new Label(numQ + " questões");
            badgeQ.setStyle("-fx-background-color: rgba(56,217,169,0.12); -fx-text-fill: #38d9a9; " +
                    "-fx-background-radius: 999; -fx-padding: 2 8 2 8; -fx-font-size: 10px;");
            badgesBox.getChildren().add(badgeQ);
        }

        // Botões de ação
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER);
        Button btnAbrir     = new Button("Abrir");
        Button btnExtrair   = new Button(numQ > 0 ? "Re-extrair" : "Extrair questões");
        btnAbrir.getStyleClass().add("btn-ghost-sm");
        btnExtrair.getStyleClass().add(numQ > 0 ? "btn-ghost-sm" : "btn-primary-sm");
        btnAbrir.setOnAction(e -> abrirArquivo(arq));
        btnExtrair.setOnAction(e -> extrairQuestoes(arq));
        actions.getChildren().addAll(btnAbrir, btnExtrair);

        card.getChildren().addAll(iconBox, lNome, lInfo, lData);
        if (numQ > 0) card.getChildren().add(badgesBox);
        card.getChildren().add(actions);

        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) abrirArquivo(arq); });
        return card;
    }

    // ══════════════════════════════════════════════════════════════════════
    // LINHA LISTA
    // ══════════════════════════════════════════════════════════════════════

    private HBox criarLinhaLista(Arquivo arq) {
        HBox row = new HBox(14);
        row.getStyleClass().add("pdf-item");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-cursor: hand;");

        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size: 22px; -fx-min-width: 36; -fx-alignment: CENTER;");

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label lNome = new Label(arq.getNomeOriginal());
        lNome.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e8eaf0;");
        String data = arq.getCreatedAt() != null ? "Importado em " + arq.getCreatedAt().format(FMT) : "";
        Label lMeta = new Label(formatarTamanho(arq.getTamanho()) + "  ·  " + data);
        lMeta.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280;");
        info.getChildren().addAll(lNome, lMeta);

        int numQ = contarQuestoesPorArquivo(arq.getId());
        if (numQ > 0) {
            Label badgeQ = new Label(numQ + " questões extraídas");
            badgeQ.setStyle("-fx-background-color: rgba(56,217,169,0.12); -fx-text-fill: #38d9a9; " +
                    "-fx-background-radius: 999; -fx-padding: 2 8 2 8; -fx-font-size: 10px;");
            row.getChildren().addAll(icon, info, badgeQ);
        } else {
            row.getChildren().addAll(icon, info);
        }

        Button btnAbrir   = new Button("Abrir");
        Button btnExtrair = new Button("Extrair");
        btnAbrir.getStyleClass().add("btn-ghost-sm");
        btnExtrair.getStyleClass().add("btn-primary-sm");
        btnAbrir.setOnAction(e -> abrirArquivo(arq));
        btnExtrair.setOnAction(e -> extrairQuestoes(arq));
        row.getChildren().addAll(btnAbrir, btnExtrair);

        return row;
    }

    // ══════════════════════════════════════════════════════════════════════
    // AÇÕES
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void onImportPdf() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Importar PDF");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showOpenDialog(null);
        if (file == null) return;

        try {
            Path destino = copiarParaApp(file);
            arquivoDao.inserir(null, destino.toString(), file.getName(), file.length(), null);
            recarregar();
        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Erro ao importar: " + e.getMessage());
        }
    }

    @FXML private void onModoGrade() {
        modoGrade = true;
        btnGrade.getStyleClass().add("view-toggle-active");
        btnLista.getStyleClass().remove("view-toggle-active");
        recarregar();
    }

    @FXML private void onModoLista() {
        modoGrade = false;
        btnLista.getStyleClass().add("view-toggle-active");
        btnGrade.getStyleClass().remove("view-toggle-active");
        recarregar();
    }

    private void abrirArquivo(Arquivo arq) {
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().open(new File(arq.getCaminho()));
        } catch (IOException ex) {
            mostrarAlerta("Não foi possível abrir o arquivo: " + ex.getMessage());
        }
    }

    private void extrairQuestoes(Arquivo arq) {
        try {
            int n = ArquivoParserService.processarArquivo(arq.getId());
            mostrarAlerta("✅ " + n + " questões extraídas de \"" + arq.getNomeOriginal() + "\"");
            recarregar();
        } catch (Exception e) {
            mostrarAlerta("Erro ao extrair questões: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITÁRIOS
    // ══════════════════════════════════════════════════════════════════════

    private int contarQuestoesPorArquivo(int arquivoId) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT COALESCE(questoes_extraidas, 0) FROM arquivo WHERE id = ?")) {
            ps.setInt(1, arquivoId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private String formatarTamanho(long bytes) {
        if (bytes < 1024)          return bytes + " B";
        if (bytes < 1024 * 1024)   return String.format("%.1f KB", bytes / 1024.0);
        return                             String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private Path copiarParaApp(File origem) throws IOException {
        Path dir = Path.of(System.getProperty("user.home") + "/.revisor/pdfs");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Path dest = dir.resolve(origem.getName());
        int i = 1;
        while (Files.exists(dest)) {
            String n = origem.getName(), base = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
            String ext  = n.contains(".") ? n.substring(n.lastIndexOf('.')) : "";
            dest = dir.resolve(base + "_" + i++ + ext);
        }
        Files.copy(origem.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private void mostrarAlerta(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Revisor"); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
