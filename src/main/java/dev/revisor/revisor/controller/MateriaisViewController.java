package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.ArquivoDao;
import dev.revisor.revisor.db.DatabaseManager;
import dev.revisor.revisor.db.MateriaDao;
import dev.revisor.revisor.model.Arquivo;
import dev.revisor.revisor.model.Materia;
import dev.revisor.revisor.service.ArquivoParserService;
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
import java.util.*;

public class MateriaisViewController implements Initializable {

    @FXML private Label     lblTotalArquivos;
    @FXML private TextField campoBuscaMaterial;
    @FXML private ComboBox<Materia> filtroMateriaMat;
    @FXML private Button    btnGrade, btnLista;
    @FXML private Label     statArquivos, statQuestoes, statTamanho, statMaterias;
    @FXML private HBox      boxDicaQuestoes;
    @FXML private FlowPane  gradeContainer;
    @FXML private VBox      listaContainer;
    @FXML private VBox      emptyMateriais;

    private final ArquivoDao arquivoDao = new ArquivoDao();
    private final MateriaDao materiaDao = new MateriaDao();
    private boolean modoGrade = true;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final List<String[]> KEYWORD_MAP = List.of(
        new String[]{"história",   "História"},
        new String[]{"historia",   "História"},
        new String[]{"geografia",  "Geografia"},
        new String[]{"matemática", "Matemática"},
        new String[]{"matematica", "Matemática"},
        new String[]{"português",  "Português"},
        new String[]{"portugues",  "Português"},
        new String[]{"física",     "Física"},
        new String[]{"fisica",     "Física"},
        new String[]{"química",    "Química"},
        new String[]{"quimica",    "Química"},
        new String[]{"biologia",   "Biologia"},
        new String[]{"inglês",     "Inglês"},
        new String[]{"ingles",     "Inglês"},
        new String[]{"english",    "Inglês"},
        new String[]{"atualidades","Atualidades"},
        new String[]{"informática","Informática"},
        new String[]{"informatica","Informática"},
        new String[]{"direito",    "Direito"},
        new String[]{"raciocínio", "Raciocínio Lógico"},
        new String[]{"raciocinio", "Raciocínio Lógico"},
        new String[]{"redação",    "Redação"},
        new String[]{"redacao",    "Redação"}
    );

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
    // DETECÇÃO DE MATÉRIA PELO NOME DO ARQUIVO
    // ══════════════════════════════════════════════════════════════════════

    private Materia detectarMateriaPeloNome(String nome) {
        if (nome == null) return null;
        String lower = nome.toLowerCase();
        for (String[] kv : KEYWORD_MAP) {
            if (lower.contains(kv[0])) {
                try {
                    List<Materia> todas = materiaDao.listar();
                    Optional<Materia> ex = todas.stream()
                        .filter(m -> m.getNome().equalsIgnoreCase(kv[1])).findFirst();
                    if (ex.isPresent()) return ex.get();
                    String[] cores = {"#4f9cf9","#38d9a9","#f093fb","#ffd93d","#ff6b6b","#ff8c42","#a78bfa"};
                    return materiaDao.inserir(kv[1], cores[Math.abs(kv[1].hashCode()) % cores.length]);
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // RECARREGAR
    // ══════════════════════════════════════════════════════════════════════

    private void recarregar() {
        gradeContainer.getChildren().clear();
        listaContainer.getChildren().clear();
        try {
            List<Arquivo> todos = arquivoDao.listarTodos();
            String  busca   = campoBuscaMaterial.getText() != null ? campoBuscaMaterial.getText().toLowerCase().trim() : "";
            Materia filtroM = filtroMateriaMat.getValue();

            List<Arquivo> filtrados = todos.stream()
                .filter(a -> busca.isEmpty() || a.getNomeOriginal().toLowerCase().contains(busca))
                .filter(a -> filtroM == null || (a.getMateriaId() != null && a.getMateriaId().equals(filtroM.getId())))
                .toList();

            lblTotalArquivos.setText(filtrados.size() + " arquivo" + (filtrados.size() != 1 ? "s" : ""));
            atualizarStats(todos);

            boolean empty = filtrados.isEmpty();
            emptyMateriais.setVisible(empty);  emptyMateriais.setManaged(empty);
            gradeContainer.setVisible(!empty && modoGrade);  gradeContainer.setManaged(!empty && modoGrade);
            listaContainer.setVisible(!empty && !modoGrade); listaContainer.setManaged(!empty && !modoGrade);

            for (Arquivo arq : filtrados) {
                if (modoGrade) gradeContainer.getChildren().add(criarCardGrade(arq));
                else           listaContainer.getChildren().add(criarLinhaLista(arq));
            }
            // Card "+ Adicionar PDF" sempre visível na grade quando há arquivos
            if (!empty && modoGrade) gradeContainer.getChildren().add(criarCardAdicionarPdf());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /** Card "＋ Adicionar PDF" que aparece na grade ao lado dos arquivos existentes. */
    private VBox criarCardAdicionarPdf() {
        VBox card = new VBox(8);
        card.getStyleClass().add("material-card");
        card.setPrefWidth(210); card.setMaxWidth(210); card.setMinHeight(160);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-border-color:rgba(79,156,249,0.3);-fx-border-radius:10;-fx-border-width:2;-fx-background-radius:10;-fx-cursor:hand;");
        Label icon = new Label("＋");
        icon.setStyle("-fx-font-size:36px;-fx-text-fill:#4f9cf9;");
        Label lbl = new Label("Adicionar PDF");
        lbl.setStyle("-fx-font-size:12px;-fx-text-fill:#4f9cf9;-fx-font-weight:bold;");
        Label sub = new Label("Clique para importar");
        sub.setStyle("-fx-font-size:10px;-fx-text-fill:#6b7280;");
        card.getChildren().addAll(icon, lbl, sub);
        card.setOnMouseClicked(e -> onImportPdf());
        card.setOnMouseEntered(e -> card.setStyle("-fx-border-color:#4f9cf9;-fx-border-radius:10;-fx-border-width:2;-fx-background-color:rgba(79,156,249,0.08);-fx-background-radius:10;-fx-cursor:hand;"));
        card.setOnMouseExited (e -> card.setStyle("-fx-border-color:rgba(79,156,249,0.3);-fx-border-radius:10;-fx-border-width:2;-fx-background-radius:10;-fx-cursor:hand;"));
        return card;
    }

    private void atualizarStats(List<Arquivo> todos) throws SQLException {
        statArquivos.setText(String.valueOf(todos.size()));
        statTamanho.setText(fmt(todos.stream().mapToLong(Arquivo::getTamanho).sum()));
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM questao")) {
            int q = rs.next() ? rs.getInt(1) : 0;
            statQuestoes.setText(String.valueOf(q));
            if (boxDicaQuestoes != null) { boxDicaQuestoes.setVisible(q == 0); boxDicaQuestoes.setManaged(q == 0); }
        }
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(DISTINCT materia_id) FROM arquivo WHERE materia_id IS NOT NULL")) {
            statMaterias.setText(rs.next() ? String.valueOf(rs.getInt(1)) : "0");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CARD GRADE
    // ══════════════════════════════════════════════════════════════════════

    private VBox criarCardGrade(Arquivo arq) {
        VBox card = new VBox(8);
        card.getStyleClass().add("material-card");
        card.setPrefWidth(210); card.setMaxWidth(210);
        card.setAlignment(Pos.TOP_CENTER);

        VBox iconBox = new VBox();
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setStyle("-fx-background-color:" + corFundo(arq) + ";-fx-background-radius:10;-fx-padding:20;-fx-min-height:80;");
        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size:32px;");
        iconBox.getChildren().add(icon);

        Label lNome = new Label(arq.getNomeOriginal());
        lNome.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#e8eaf0;");
        lNome.setWrapText(true); lNome.setMaxWidth(190); lNome.setAlignment(Pos.CENTER);

        Label lInfo = new Label(fmt(arq.getTamanho()));
        lInfo.setStyle("-fx-font-size:10px;-fx-text-fill:#6b7280;");
        Label lData = new Label(arq.getCreatedAt() != null ? arq.getCreatedAt().format(FMT) : "");
        lData.setStyle("-fx-font-size:10px;-fx-text-fill:#6b7280;");

        int numQ = contarQuestoes(arq.getId());
        card.getChildren().addAll(iconBox, materiaLabel(arq), lNome, lInfo, lData);
        if (numQ > 0) {
            Label bq = new Label(numQ + " questões");
            bq.setStyle("-fx-background-color:rgba(56,217,169,0.12);-fx-text-fill:#38d9a9;-fx-background-radius:999;-fx-padding:2 8 2 8;-fx-font-size:10px;");
            card.getChildren().add(bq);
        }

        // ── Botões: Abrir | Extrair | ✏ Editar | 🗑 Apagar ────────────
        HBox actions = new HBox(4);
        actions.setAlignment(Pos.CENTER);
        actions.setStyle("-fx-padding:4 0 0 0;");

        Button btnAbrir   = btn("Abrir",   "btn-ghost-sm",   e -> abrirArquivo(arq));
        Button btnExtrair = btn(numQ > 0 ? "Re-extrair" : "Extrair",
                                numQ > 0 ? "btn-ghost-sm"  : "btn-primary-sm",
                                e -> extrairQuestoes(arq));
        Button btnEditar  = btn("✏",       "btn-ghost-sm",   e -> editarArquivo(arq));
        Button btnApagar  = btn("🗑",       "btn-ghost-sm",   e -> confirmarApagar(arq));
        btnApagar.setStyle("-fx-text-fill:#ef4444;");
        btnApagar.setTooltip(new Tooltip("Apagar apostila e suas questões"));

        actions.getChildren().addAll(btnAbrir, btnExtrair, btnEditar, btnApagar);
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

        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size:22px;-fx-min-width:36;-fx-alignment:CENTER;");

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label lNome = new Label(arq.getNomeOriginal());
        lNome.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#e8eaf0;");
        String data = arq.getCreatedAt() != null ? "Importado em " + arq.getCreatedAt().format(FMT) : "";
        Label lMeta = new Label(fmt(arq.getTamanho()) + "  ·  " + data);
        lMeta.setStyle("-fx-font-size:11px;-fx-text-fill:#6b7280;");
        info.getChildren().addAll(lNome, lMeta, materiaLabel(arq));

        int numQ = contarQuestoes(arq.getId());
        row.getChildren().addAll(icon, info);
        if (numQ > 0) {
            Label bq = new Label(numQ + " questões");
            bq.setStyle("-fx-background-color:rgba(56,217,169,0.12);-fx-text-fill:#38d9a9;-fx-background-radius:999;-fx-padding:2 8 2 8;-fx-font-size:10px;");
            row.getChildren().add(bq);
        }
        row.getChildren().addAll(
            btn("Abrir",     "btn-ghost-sm",  e -> abrirArquivo(arq)),
            btn("Extrair",   "btn-primary-sm", e -> extrairQuestoes(arq)),
            btn("✏ Editar",  "btn-ghost-sm",  e -> editarArquivo(arq)),
            btn("🗑 Apagar", "btn-ghost-sm",  e -> confirmarApagar(arq))
        );
        return row;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ✏ EDITAR ARQUIVO — nome + matéria (propaga materia_id às questões)
    // ══════════════════════════════════════════════════════════════════════

    private void editarArquivo(Arquivo arq) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar apostila");
        dialog.setHeaderText(null);

        ButtonType salvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(salvar, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding:16;");

        Label lblN = lbl("Nome do arquivo:");
        TextField campoNome = new TextField(arq.getNomeOriginal());
        campoNome.setMaxWidth(Double.MAX_VALUE);

        Label lblM = lbl("Matéria vinculada:");
        ComboBox<Materia> comboMat = new ComboBox<>();
        comboMat.setMaxWidth(Double.MAX_VALUE);
        try {
            comboMat.getItems().add(null);
            comboMat.getItems().addAll(materiaDao.listar());
        } catch (SQLException e) { e.printStackTrace(); }
        comboMat.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Materia m)   { return m == null ? "— Sem matéria —" : m.getNome(); }
            public Materia fromString(String s) { return null; }
        });
        comboMat.getItems().stream()
            .filter(m -> m != null && m.getId().equals(arq.getMateriaId()))
            .findFirst().ifPresent(comboMat::setValue);

        Label aviso = new Label("⚠ Alterar a matéria vai atualizar todas as questões deste arquivo.");
        aviso.setStyle("-fx-font-size:11px;-fx-text-fill:#ffd93d;");
        aviso.setWrapText(true);

        content.getChildren().addAll(lblN, campoNome, lblM, comboMat, aviso);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(400);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != salvar) return;
            String novoNome = campoNome.getText().trim();
            if (novoNome.isEmpty()) return;
            Materia novaMateria  = comboMat.getValue();
            Integer novoMatId    = novaMateria != null ? novaMateria.getId() : null;
            try {
                try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                        "UPDATE arquivo SET nome_original=?, materia_id=? WHERE id=?")) {
                    ps.setString(1, novoNome);
                    if (novoMatId != null) ps.setInt(2, novoMatId); else ps.setNull(2, Types.INTEGER);
                    ps.setInt(3, arq.getId());
                    ps.executeUpdate();
                }
                // Propaga materia_id para todas as questões deste arquivo
                try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                        "UPDATE questao SET materia_id=? WHERE arquivo_id=?")) {
                    if (novoMatId != null) ps.setInt(1, novoMatId); else ps.setNull(1, Types.INTEGER);
                    ps.setInt(2, arq.getId());
                    ps.executeUpdate();
                }
                recarregar();
            } catch (SQLException ex) { ex.printStackTrace(); mostrarAlerta("Erro: " + ex.getMessage()); }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // 🗑 APAGAR ARQUIVO
    // ══════════════════════════════════════════════════════════════════════

    private void confirmarApagar(Arquivo arq) {
        int numQ = contarQuestoes(arq.getId());
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Apagar apostila");
        a.setHeaderText("Apagar \"" + arq.getNomeOriginal() + "\"?");
        a.setContentText("Será removido:\n• Este registro de apostila\n"
            + (numQ > 0 ? "• " + numQ + " questão(ões) extraída(s)\n" : "")
            + "\nO arquivo PDF no disco não será apagado.");
        ButtonType sim = new ButtonType("Sim, apagar", ButtonBar.ButtonData.OK_DONE);
        a.getButtonTypes().setAll(sim, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        a.showAndWait().filter(b -> b == sim).ifPresent(b -> {
            try { apagarComTransacao(arq.getId()); recarregar(); }
            catch (SQLException ex) { mostrarAlerta("Erro: " + ex.getMessage()); }
        });
    }

    private void apagarComTransacao(int id) throws SQLException {
        Connection c = DatabaseManager.getConnection();
        c.setAutoCommit(false);
        try {
            exec(c, "DELETE FROM alternativa WHERE questao_id IN (SELECT id FROM questao WHERE arquivo_id=?)", id);
            exec(c, "DELETE FROM resposta    WHERE questao_id IN (SELECT id FROM questao WHERE arquivo_id=?)", id);
            exec(c, "DELETE FROM questao WHERE arquivo_id=?", id);
            exec(c, "DELETE FROM arquivo  WHERE id=?",        id);
            c.commit();
        } catch (SQLException e) { c.rollback(); throw e; }
        finally { c.setAutoCommit(true); }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 📂 IMPORTAR PDF — múltiplos, detecta matéria pelo nome
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void onImportPdf() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Importar PDF(s)");
        chooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        javafx.stage.Window owner = gradeContainer.getScene() != null
                ? gradeContainer.getScene().getWindow() : null;
        List<File> files = chooser.showOpenMultipleDialog(owner);
        if (files == null || files.isEmpty()) return;

        try {
            List<Materia> todasMaterias = materiaDao.listar();
            for (File file : files) {
                Materia detectada = detectarMateriaPeloNome(file.getName());
                Materia escolhida = escolherMateria(file.getName(), detectada, todasMaterias);
                if (escolhida == null && detectada == null && !confirmarSemMateria(file.getName())) continue;
                Path dest = copiarParaApp(file);
                arquivoDao.inserir(escolhida != null ? escolhida.getId() : null,
                                   dest.toString(), file.getName(), file.length(), null);
            }
            recarregar();
        } catch (Exception e) { e.printStackTrace(); mostrarAlerta("Erro ao importar: " + e.getMessage()); }
    }

    private Materia escolherMateria(String nomeArq, Materia detectada, List<Materia> todas) {
        Dialog<Materia> dialog = new Dialog<>();
        dialog.setTitle("Vincular matéria");
        dialog.setHeaderText("Arquivo: " + nomeArq);

        ButtonType okBtn  = new ButtonType("Importar",    ButtonBar.ButtonData.OK_DONE);
        ButtonType semBtn = new ButtonType("Sem matéria", ButtonBar.ButtonData.LEFT);
        ButtonType canBtn = new ButtonType("Cancelar",    ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, semBtn, canBtn);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding:12;");
        Label info = new Label(detectada != null
            ? "✅ Detectada automaticamente: " + detectada.getNome()
            : "Nenhuma matéria detectada no nome.");
        info.setWrapText(true);
        info.setStyle("-fx-font-size:12px;-fx-text-fill:" + (detectada != null ? "#38d9a9" : "#ffd93d") + ";");

        ComboBox<Materia> combo = new ComboBox<>();
        combo.getItems().add(null);
        combo.getItems().addAll(todas);
        combo.setValue(detectada);
        combo.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Materia m)   { return m == null ? "— Nenhuma —" : m.getNome(); }
            public Materia fromString(String s) { return null; }
        });
        combo.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().addAll(info, lbl("Matéria:"), combo);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(380);

        boolean[] cancelado = {false};
        dialog.setResultConverter(b -> {
            if (b == okBtn)  return combo.getValue();
            if (b == semBtn) return null;
            cancelado[0] = true; return null;
        });
        Optional<Materia> result = dialog.showAndWait();
        return cancelado[0] ? null : result.orElse(null);
    }

    private boolean confirmarSemMateria(String nome) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Importar sem matéria");
        a.setHeaderText("Importar sem matéria vinculada?");
        a.setContentText("\"" + nome + "\" será importado sem matéria.\nVocê pode editar depois com o botão ✏.");
        return a.showAndWait().map(b -> b == ButtonType.OK).orElse(false);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AÇÕES FXML
    // ══════════════════════════════════════════════════════════════════════

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
        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(arq.getCaminho())); }
        catch (IOException ex) { mostrarAlerta("Não foi possível abrir: " + ex.getMessage()); }
    }

    private void extrairQuestoes(Arquivo arq) {
        try {
            int n = ArquivoParserService.processarArquivo(arq.getId());
            mostrarAlerta("✅ " + n + " questões extraídas de \"" + arq.getNomeOriginal() + "\"");
            recarregar();
        } catch (Exception e) { mostrarAlerta("Erro ao extrair: " + e.getMessage()); }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILS
    // ══════════════════════════════════════════════════════════════════════

    private Label materiaLabel(Arquivo arq) {
        Label l = new Label();
        if (arq.getMateriaId() != null) {
            try {
                materiaDao.listar().stream()
                    .filter(m -> m.getId().equals(arq.getMateriaId())).findFirst()
                    .ifPresent(m -> {
                        String cor = (m.getCor() != null && !m.getCor().isBlank()) ? m.getCor() : "#4f9cf9";
                        l.setText(m.getNome());
                        l.setStyle("-fx-font-size:10px;-fx-text-fill:" + cor +
                            ";-fx-border-color:" + cor + ";-fx-border-radius:999;-fx-padding:1 6 1 6;");
                    });
            } catch (SQLException ignored) {}
        }
        if (l.getText() == null || l.getText().isBlank()) {
            l.setText("Sem matéria");
            l.setStyle("-fx-font-size:10px;-fx-text-fill:#6b7280;-fx-border-color:#6b7280;-fx-border-radius:999;-fx-padding:1 6 1 6;");
        }
        return l;
    }

    private String corFundo(Arquivo arq) {
        if (arq.getMateriaId() != null) {
            try {
                return materiaDao.listar().stream()
                    .filter(m -> m.getId().equals(arq.getMateriaId()))
                    .map(m -> hexRgba(m.getCor() != null ? m.getCor() : "#4f9cf9", 0.15))
                    .findFirst().orElse("rgba(79,156,249,0.12)");
            } catch (SQLException ignored) {}
        }
        return "rgba(255,107,107,0.12)";
    }

    private String hexRgba(String hex, double a) {
        try {
            hex = hex.replace("#","");
            return String.format("rgba(%d,%d,%d,%.2f)",
                Integer.parseInt(hex.substring(0,2),16),
                Integer.parseInt(hex.substring(2,4),16),
                Integer.parseInt(hex.substring(4,6),16), a);
        } catch (Exception e) { return "rgba(79,156,249,0.12)"; }
    }

    private int contarQuestoes(int arquivoId) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT COALESCE(questoes_extraidas,0) FROM arquivo WHERE id=?")) {
            ps.setInt(1, arquivoId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private String fmt(long bytes) {
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return                          String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private Path copiarParaApp(File origem) throws IOException {
        Path dir = Path.of(System.getProperty("user.home") + "/.revisor/pdfs");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Path dest = dir.resolve(origem.getName());
        int i = 1;
        while (Files.exists(dest)) {
            String n = origem.getName(), base = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
            String ext = n.contains(".") ? n.substring(n.lastIndexOf('.')) : "";
            dest = dir.resolve(base + "_" + i++ + ext);
        }
        Files.copy(origem.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private void exec(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { ps.setInt(1, id); ps.executeUpdate(); }
    }

    private Button btn(String text, String styleClass, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button(text); b.getStyleClass().add(styleClass); b.setOnAction(action); return b;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size:12px;-fx-text-fill:#9ca3af;"); return l;
    }

    private void mostrarAlerta(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Revisor"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
