package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.DatabaseManager;
import dev.revisor.revisor.db.MateriaDao;
import dev.revisor.revisor.model.Materia;
import dev.revisor.revisor.model.Questao;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.sql.*;
import java.util.*;

/**
 * Controller da aba Questões com filtros robustos:
 * busca textual, matéria, assunto, banca, ano, tipo e questões salvas.
 */
public class QuestoesViewController implements Initializable {

    // ── TOPBAR (monitoramento do banco) ────────────────────────────────────
    @FXML private Label lblTotalBanco;
    @FXML private Label lblContador;

    // ── Estado vazio (mensagens dinâmicas) ───────────────────────────────────
    @FXML private Label lblEmptyTitulo;
    @FXML private Label lblEmptyTexto;

    // ── FILTROS ───────────────────────────────────────────────────────────
    @FXML private TextField campoBusca;
    @FXML private ComboBox<Materia> filtroMateria;
    @FXML private ComboBox<String>  filtroAssunto;
    @FXML private ComboBox<String>  filtroBanca;
    @FXML private ComboBox<String>  filtroAno;
    @FXML private ComboBox<String>  filtroTipo;
    @FXML private Button            btnSoSalvas;
    @FXML private Label             lblFiltrosAtivos;

    // ── CHIPS ─────────────────────────────────────────────────────────────
    @FXML private HBox chipsAtivos;

    // ── LISTA ─────────────────────────────────────────────────────────────
    @FXML private VBox listaQuestoes;
    @FXML private VBox emptyState;

    private final MateriaDao materiaDao = new MateriaDao();
    private boolean soSalvas = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try {
            popularCombos();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Busca em tempo real ao digitar
        campoBusca.textProperty().addListener((obs, o, n) -> aplicarFiltros());
        filtroMateria.valueProperty().addListener((obs, o, n) -> {
            atualizarAssuntos();
            aplicarFiltros();
        });
        filtroAssunto.valueProperty().addListener((obs, o, n) -> aplicarFiltros());
        filtroBanca.valueProperty().addListener((obs, o, n)   -> aplicarFiltros());
        filtroAno.valueProperty().addListener((obs, o, n)     -> aplicarFiltros());
        filtroTipo.valueProperty().addListener((obs, o, n)    -> aplicarFiltros());

        aplicarFiltros();
    }

    // ══════════════════════════════════════════════════════════════════════
    // POPULAR COMBOS
    // ══════════════════════════════════════════════════════════════════════

    private void popularCombos() throws SQLException {
        // Matérias
        List<Materia> materias = materiaDao.listar();
        filtroMateria.setItems(FXCollections.observableArrayList(materias));

        // Bancas
        List<String> bancas = queryDistinct("SELECT DISTINCT banca FROM questao WHERE banca IS NOT NULL ORDER BY banca");
        filtroBanca.getItems().add("Todas as bancas");
        filtroBanca.getItems().addAll(bancas);

        // Anos
        List<String> anos = queryDistinct("SELECT DISTINCT CAST(ano AS TEXT) FROM questao WHERE ano IS NOT NULL ORDER BY ano DESC");
        filtroAno.getItems().add("Todos os anos");
        filtroAno.getItems().addAll(anos);

        // Tipos
        filtroTipo.getItems().addAll(
            "Todos os tipos",
            "Múltipla escolha",
            "Certo/Errado",
            "Dissertativa"
        );
    }

    private void atualizarAssuntos() {
        filtroAssunto.getItems().clear();
        filtroAssunto.getSelectionModel().clearSelection();

        Materia m = filtroMateria.getValue();
        if (m == null) return;

        try {
            List<String> assuntos = queryDistinct(
                "SELECT DISTINCT assunto FROM questao WHERE materia_id = " + m.getId()
                + " AND assunto IS NOT NULL ORDER BY assunto"
            );
            filtroAssunto.getItems().add("Todos os assuntos");
            filtroAssunto.getItems().addAll(assuntos);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private List<String> queryDistinct(String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null && !v.isBlank()) result.add(v);
            }
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // APLICAR FILTROS
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void onAplicarFiltros() { aplicarFiltros(); }

    private void aplicarFiltros() {
        listaQuestoes.getChildren().clear();
        chipsAtivos.getChildren().clear();

        String busca      = campoBusca.getText() != null ? campoBusca.getText().trim() : "";
        Materia materia   = filtroMateria.getValue();
        String assunto    = filtroAssunto.getValue();
        String banca      = filtroBanca.getValue();
        String ano        = filtroAno.getValue();
        String tipo       = filtroTipo.getValue();

        // Montar SQL dinâmico
        StringBuilder sql = new StringBuilder("""
            SELECT q.id, q.materia_id, q.assunto, q.enunciado, q.banca, q.ano, q.tipo,
                   q.alternativa_correta, m.nome AS materia_nome, m.cor AS materia_cor
            FROM questao q
            LEFT JOIN materia m ON m.id = q.materia_id
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();
        int filtrosAtivos = 0;

        if (!busca.isEmpty()) {
            sql.append(" AND q.enunciado LIKE ?");
            params.add("%" + busca + "%");
            filtrosAtivos++;
            addChip(busca, "#4f9cf9", () -> { campoBusca.clear(); aplicarFiltros(); });
        }
        if (materia != null) {
            sql.append(" AND q.materia_id = ?");
            params.add(materia.getId());
            filtrosAtivos++;
            addChip(materia.getNome(), materia.getCor(), () -> { filtroMateria.setValue(null); });
        }
        if (assunto != null && !assunto.isBlank() && !assunto.equals("Todos os assuntos")) {
            sql.append(" AND q.assunto = ?");
            params.add(assunto);
            filtrosAtivos++;
            addChip(assunto, "#f093fb", () -> { filtroAssunto.setValue(null); });
        }
        if (banca != null && !banca.equals("Todas as bancas")) {
            sql.append(" AND q.banca = ?");
            params.add(banca);
            filtrosAtivos++;
            addChip(banca, "#ffd93d", () -> { filtroBanca.setValue(null); });
        }
        if (ano != null && !ano.equals("Todos os anos")) {
            sql.append(" AND q.ano = ?");
            params.add(Integer.parseInt(ano));
            filtrosAtivos++;
            addChip(ano, "#38d9a9", () -> { filtroAno.setValue(null); });
        }
        if (tipo != null && !tipo.equals("Todos os tipos")) {
            String tipoDb = mapTipo(tipo);
            sql.append(" AND q.tipo = ?");
            params.add(tipoDb);
            filtrosAtivos++;
            addChip(tipo, "#ff8c42", () -> { filtroTipo.setValue(null); });
        }

        sql.append(" ORDER BY m.nome, q.assunto, q.ano DESC, q.id DESC");

        try {
            // Total no banco (para monitorar se está sendo alimentado)
            int totalNoBanco = 0;
            try (Statement st = DatabaseManager.getConnection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM questao")) {
                if (rs.next()) totalNoBanco = rs.getInt(1);
            }

            lblTotalBanco.setText("Banco: " + totalNoBanco + " questão" + (totalNoBanco != 1 ? "ões" : ""));

            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));

                List<Map<String, Object>> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id",        rs.getInt("id"));
                        row.put("enunciado", rs.getString("enunciado"));
                        row.put("banca",     rs.getString("banca"));
                        row.put("ano",       rs.getObject("ano"));
                        row.put("tipo",      rs.getString("tipo"));
                        row.put("assunto",   rs.getString("assunto"));
                        row.put("materia",   rs.getString("materia_nome"));
                        row.put("cor",       rs.getString("materia_cor"));
                        rows.add(row);
                    }
                }

                lblContador.setText("Exibidas: " + rows.size() + " questão" + (rows.size() != 1 ? "ões" : ""));
                lblFiltrosAtivos.setText(filtrosAtivos > 0 ? filtrosAtivos + " filtro(s)" : "");
                lblFiltrosAtivos.setVisible(filtrosAtivos > 0);
                lblFiltrosAtivos.setManaged(filtrosAtivos > 0);

                boolean empty = rows.isEmpty();
                emptyState.setVisible(empty);
                emptyState.setManaged(empty);

                if (empty && lblEmptyTitulo != null && lblEmptyTexto != null) {
                    if (totalNoBanco == 0) {
                        lblEmptyTitulo.setText("O banco de questões está vazio");
                        lblEmptyTexto.setText("Para alimentar o banco: vá em Materiais, importe um PDF e clique em \"Extrair questões\" em cada arquivo. As questões aparecerão aqui.");
                    } else {
                        lblEmptyTitulo.setText("Nenhuma questão com os filtros atuais");
                        lblEmptyTexto.setText("Tente alterar ou limpar os filtros para ver as " + totalNoBanco + " questões do banco.");
                    }
                }

                for (Map<String, Object> row : rows) {
                    listaQuestoes.getChildren().add(criarCardQuestao(row));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String mapTipo(String label) {
        return switch (label) {
            case "Múltipla escolha" -> "multipla_escolha";
            case "Certo/Errado"     -> "certo_errado";
            case "Dissertativa"     -> "dissertativa";
            default -> label;
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // CARD DE QUESTÃO
    // ══════════════════════════════════════════════════════════════════════

    private VBox criarCardQuestao(Map<String, Object> row) {
        VBox card = new VBox();
        card.getStyleClass().add("card");
        card.setStyle("-fx-cursor: hand;");

        // Header
        HBox header = new HBox(10);
        header.getStyleClass().add("card-header");
        header.setAlignment(Pos.CENTER_LEFT);

        String cor    = row.get("cor") != null ? (String) row.get("cor") : "#4f9cf9";
        String nomeM  = row.get("materia") != null ? (String) row.get("materia") : "Sem matéria";
        String assun  = row.get("assunto") != null ? " · " + row.get("assunto") : "";

        Rectangle dot = new Rectangle(8, 8);
        dot.setArcWidth(2);
        dot.setArcHeight(2);
        dot.setStyle("-fx-fill: " + cor + ";");

        Label lTitulo = new Label(nomeM + assun);
        lTitulo.getStyleClass().add("card-title");
        HBox.setHgrow(lTitulo, Priority.ALWAYS);

        // Badges
        HBox badges = new HBox(5);
        badges.setAlignment(Pos.CENTER_RIGHT);

        if (row.get("banca") != null) {
            badges.getChildren().add(criarBadge((String) row.get("banca"), "badge-banca"));
        }
        if (row.get("ano") != null) {
            badges.getChildren().add(criarBadge(String.valueOf(row.get("ano")), "badge-ano"));
        }
        if (row.get("tipo") != null) {
            badges.getChildren().add(criarBadge(formatarTipo((String) row.get("tipo")), "badge-tipo"));
        }

        header.getChildren().addAll(dot, lTitulo, badges);

        // Corpo
        VBox body = new VBox(8);
        body.setStyle("-fx-padding: 0 18 14 18;");

        String enunc = (String) row.get("enunciado");
        if (enunc != null && enunc.length() > 220) enunc = enunc.substring(0, 220) + "…";
        Label lEnunc = new Label(enunc);
        lEnunc.getStyleClass().add("q-text");
        lEnunc.setWrapText(true);

        // Alternativas (se existirem no banco)
        VBox altsBox = carregarAlternativas((int) row.get("id"));

        body.getChildren().add(lEnunc);
        if (altsBox != null) body.getChildren().add(altsBox);

        // Rodapé com ações
        HBox footer = new HBox(8);
        footer.setStyle("-fx-padding: 4 18 12 18;");
        footer.setAlignment(Pos.CENTER_LEFT);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnSalvar    = new Button("🔖 Salvar");
        Button btnResponder = new Button("▶  Responder");
        btnSalvar.getStyleClass().add("btn-ghost-sm");
        btnResponder.getStyleClass().add("btn-primary-sm");
        footer.getChildren().addAll(sp, btnSalvar, btnResponder);

        card.getChildren().addAll(header, body, footer);
        return card;
    }

    private VBox carregarAlternativas(int questaoId) {
        try {
            String sql = "SELECT letra, texto, correta FROM alternativa WHERE questao_id = ? ORDER BY letra";
            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
                ps.setInt(1, questaoId);
                ResultSet rs = ps.executeQuery();
                VBox box = new VBox(4);

                boolean temAlts = false;
                while (rs.next()) {
                    temAlts = true;
                    boolean correta = rs.getInt("correta") == 1;
                    String letra = rs.getString("letra");
                    String texto = rs.getString("texto");
                    if (texto != null && texto.length() > 100) texto = texto.substring(0, 100) + "…";

                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setStyle("-fx-padding: 3 0 3 0;");

                    Label lLetra = new Label(letra);
                    lLetra.getStyleClass().add("opt-letter");
                    if (correta) lLetra.setStyle(lLetra.getStyle()
                            + "-fx-background-color: rgba(56,217,169,0.2); -fx-text-fill: #38d9a9;");

                    Label lTexto = new Label(texto);
                    lTexto.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (correta ? "#38d9a9" : "#9ca3af") + ";");
                    lTexto.setWrapText(false);
                    HBox.setHgrow(lTexto, Priority.ALWAYS);

                    row.getChildren().addAll(lLetra, lTexto);
                    box.getChildren().add(row);
                }
                return temAlts ? box : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private Label criarBadge(String texto, String styleClass) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("task-tag", styleClass);
        return l;
    }

    private String formatarTipo(String tipo) {
        return switch (tipo != null ? tipo : "") {
            case "multipla_escolha" -> "Múltipla";
            case "certo_errado"     -> "C/E";
            case "dissertativa"     -> "Dissertativa";
            default -> tipo;
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // CHIPS DE FILTRO
    // ══════════════════════════════════════════════════════════════════════

    private void addChip(String texto, String cor, Runnable onRemove) {
        HBox chip = new HBox(5);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setStyle("-fx-background-color: rgba(79,156,249,0.12); -fx-background-radius: 999; " +
                "-fx-padding: 3 10 3 10; -fx-cursor: hand; " +
                "-fx-border-color: " + cor + "; -fx-border-radius: 999; -fx-border-width: 1;");

        Label lTxt = new Label(texto);
        lTxt.setStyle("-fx-font-size: 11px; -fx-text-fill: " + cor + ";");

        Label lX = new Label("✕");
        lX.setStyle("-fx-font-size: 10px; -fx-text-fill: " + cor + ";");

        chip.getChildren().addAll(lTxt, lX);
        chip.setOnMouseClicked(e -> { onRemove.run(); aplicarFiltros(); });
        chipsAtivos.getChildren().add(chip);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AÇÕES
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void onLimparFiltros() {
        campoBusca.clear();
        filtroMateria.setValue(null);
        filtroAssunto.setValue(null);
        filtroBanca.setValue(null);
        filtroAno.setValue(null);
        filtroTipo.setValue(null);
        soSalvas = false;
        btnSoSalvas.getStyleClass().remove("btn-primary-sm");
        btnSoSalvas.getStyleClass().add("btn-ghost-sm");
        aplicarFiltros();
    }

    @FXML private void onToggleSalvas() {
        soSalvas = !soSalvas;
        if (soSalvas) {
            btnSoSalvas.getStyleClass().remove("btn-ghost-sm");
            btnSoSalvas.getStyleClass().add("btn-primary-sm");
        } else {
            btnSoSalvas.getStyleClass().remove("btn-primary-sm");
            btnSoSalvas.getStyleClass().add("btn-ghost-sm");
        }
        aplicarFiltros();
    }

    @FXML private void onNovaQuestao() {
        System.out.println("Abrir formulário de nova questão");
    }

    @FXML private void onImportarPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = chooser.showOpenDialog(null);
        if (f != null) System.out.println("Importar: " + f.getAbsolutePath());
    }
}
