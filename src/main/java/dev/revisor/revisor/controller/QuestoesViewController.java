package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.DatabaseManager;
import dev.revisor.revisor.db.MateriaDao;
import dev.revisor.revisor.model.Materia;
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

public class QuestoesViewController implements Initializable {

    @FXML private Label lblTotalBanco;
    @FXML private Label lblContador;
    @FXML private Label lblEmptyTitulo;
    @FXML private Label lblEmptyTexto;

    @FXML private TextField         campoBusca;
    @FXML private ComboBox<Materia> filtroMateria;
    @FXML private ComboBox<String>  filtroAssunto;
    @FXML private ComboBox<String>  filtroArquivo;
    @FXML private ComboBox<String>  filtroBanca;
    @FXML private ComboBox<String>  filtroAno;
    @FXML private ComboBox<String>  filtroTipo;
    @FXML private Button            btnSoSalvas;
    @FXML private Label             lblFiltrosAtivos;
    @FXML private HBox              chipsAtivos;

    @FXML private VBox listaQuestoes;
    @FXML private VBox emptyState;
    @FXML private HBox paginacaoBox;

    private final MateriaDao materiaDao = new MateriaDao();
    private boolean soSalvas = false;

    private static final int POR_PAGINA = 10;
    private int paginaAtual   = 0;
    private int totalFiltrado = 0;

    private final Map<Integer, List<Map<String, Object>>> cacheAlts          = new HashMap<>();
    private final Map<Integer, Integer>                   respostaSelecionada = new HashMap<>();
    private final Set<Integer>                            gabaritoAtivo       = new HashSet<>();

    private Integer filtroMateriaPreId = null;
    public void setFiltroMateriaId(Integer id) { this.filtroMateriaPreId = id; }

    // ══════════════════════════════════════════════════════════════════════
    // INIT
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try { popularCombos(); } catch (SQLException e) { e.printStackTrace(); }

        campoBusca.textProperty().addListener((o, a, b)     -> resetarPaginaEFiltrar());
        filtroMateria.valueProperty().addListener((o, a, b) -> { atualizarAssuntos(); resetarPaginaEFiltrar(); });
        atualizarFiltroArquivo();
        if (filtroArquivo != null)
            filtroArquivo.valueProperty().addListener((o, a, b) -> resetarPaginaEFiltrar());
        filtroAssunto.valueProperty().addListener((o, a, b) -> resetarPaginaEFiltrar());
        filtroBanca.valueProperty().addListener((o, a, b)   -> resetarPaginaEFiltrar());
        filtroAno.valueProperty().addListener((o, a, b)     -> resetarPaginaEFiltrar());
        filtroTipo.valueProperty().addListener((o, a, b)    -> resetarPaginaEFiltrar());

        listaQuestoes.setFillWidth(true);
        aplicarFiltros();
    }

    public void aplicarFiltroPorMateria(Materia materia) {
        if (materia == null) return;
        filtroMateria.setValue(materia);
        atualizarAssuntos();
        paginaAtual = 0;
        aplicarFiltros();
    }

    private void resetarPaginaEFiltrar() { paginaAtual = 0; aplicarFiltros(); }

    // ══════════════════════════════════════════════════════════════════════
    // COMBOS
    // ══════════════════════════════════════════════════════════════════════

    private void popularCombos() throws SQLException {
        filtroMateria.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Materia m)   { return m == null ? "Todas as matérias" : m.getNome(); }
            public Materia fromString(String s) { return null; }
        });
        filtroMateria.setItems(FXCollections.observableArrayList(materiaDao.listar()));

        filtroBanca.getItems().add("Todas as bancas");
        filtroBanca.getItems().addAll(queryDistinct(
            "SELECT DISTINCT banca FROM questao WHERE banca IS NOT NULL ORDER BY banca"));

        filtroAno.getItems().add("Todos os anos");
        filtroAno.getItems().addAll(queryDistinct(
            "SELECT DISTINCT CAST(ano AS TEXT) FROM questao WHERE ano IS NOT NULL ORDER BY ano DESC"));

        filtroTipo.getItems().addAll("Todos os tipos", "Múltipla escolha", "Certo/Errado", "Dissertativa");

        filtroAssunto.getItems().add("Todos os assuntos");
        filtroAssunto.getItems().addAll(queryDistinct(
            "SELECT DISTINCT assunto FROM questao WHERE assunto IS NOT NULL ORDER BY assunto"));
    }

    private void atualizarFiltroArquivo() {
        if (filtroArquivo == null) return;
        filtroArquivo.getItems().clear();
        filtroArquivo.getItems().add("Todos os PDFs");
        try {
            filtroArquivo.getItems().addAll(queryDistinct(
                "SELECT DISTINCT a.nome_original FROM arquivo a" +
                " INNER JOIN questao q ON q.arquivo_id = a.id" +
                " WHERE a.nome_original IS NOT NULL ORDER BY a.nome_original"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void atualizarAssuntos() {
        filtroAssunto.getItems().clear();
        filtroAssunto.getSelectionModel().clearSelection();
        Materia m = filtroMateria.getValue();
        try {
            filtroAssunto.getItems().add("Todos os assuntos");
            String sql = m != null
                ? "SELECT DISTINCT assunto FROM questao WHERE materia_id=" + m.getId() + " AND assunto IS NOT NULL ORDER BY assunto"
                : "SELECT DISTINCT assunto FROM questao WHERE assunto IS NOT NULL ORDER BY assunto";
            filtroAssunto.getItems().addAll(queryDistinct(sql));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private List<String> queryDistinct(String sql) throws SQLException {
        List<String> r = new ArrayList<>();
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) { String v = rs.getString(1); if (v != null && !v.isBlank()) r.add(v); }
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════
    // FILTROS
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void onAplicarFiltros() { resetarPaginaEFiltrar(); }

    private void aplicarFiltros() {
        listaQuestoes.getChildren().clear();
        chipsAtivos.getChildren().clear();
        cacheAlts.clear();

        String  busca   = campoBusca.getText() != null ? campoBusca.getText().trim() : "";
        Materia materia = filtroMateria.getValue();
        String  assunto = filtroAssunto.getValue();
        String  banca   = filtroBanca.getValue();
        String  ano     = filtroAno.getValue();
        String  tipo    = filtroTipo.getValue();

        StringBuilder where  = new StringBuilder(" WHERE 1=1");
        List<Object>  params = new ArrayList<>();
        int filtrosAtivos    = 0;

        if (!busca.isEmpty()) {
            where.append(" AND q.enunciado LIKE ?"); params.add("%" + busca + "%"); filtrosAtivos++;
            addChip(busca, "#4f9cf9", () -> { campoBusca.clear(); aplicarFiltros(); });
        }
        if (materia != null) {
            where.append(" AND (q.materia_id = ? OR (q.materia_id IS NULL AND LOWER(q.assunto) LIKE LOWER(?)))");
            params.add(materia.getId());
            params.add("%" + materia.getNome() + "%");
            filtrosAtivos++;
            addChip(materia.getNome(), safeColor(materia.getCor()), () -> filtroMateria.setValue(null));
        }
        if (assunto != null && !assunto.isBlank() && !assunto.equals("Todos os assuntos")) {
            where.append(" AND q.assunto = ?"); params.add(assunto); filtrosAtivos++;
            addChip(assunto, "#f093fb", () -> filtroAssunto.setValue(null));
        }
        String arquivo = filtroArquivo != null ? filtroArquivo.getValue() : null;
        if (arquivo != null && !arquivo.isBlank() && !arquivo.equals("Todos os PDFs")) {
            where.append(" AND q.arquivo_id = (SELECT id FROM arquivo WHERE nome_original = ? LIMIT 1)");
            params.add(arquivo); filtrosAtivos++;
            addChip("📄 " + arquivo, "#a78bfa", () -> { if (filtroArquivo != null) filtroArquivo.setValue(null); });
        }
        if (banca != null && !banca.equals("Todas as bancas")) {
            where.append(" AND q.banca = ?"); params.add(banca); filtrosAtivos++;
            addChip(banca, "#ffd93d", () -> filtroBanca.setValue(null));
        }
        if (ano != null && !ano.equals("Todos os anos")) {
            where.append(" AND q.ano = ?"); params.add(Integer.parseInt(ano)); filtrosAtivos++;
            addChip(ano, "#38d9a9", () -> filtroAno.setValue(null));
        }
        if (tipo != null && !tipo.equals("Todos os tipos")) {
            where.append(" AND q.tipo = ?"); params.add(mapTipo(tipo)); filtrosAtivos++;
            addChip(tipo, "#ff8c42", () -> filtroTipo.setValue(null));
        }

        try {
            int totalNoBanco = 0;
            try (Statement st = DatabaseManager.getConnection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM questao")) {
                if (rs.next()) totalNoBanco = rs.getInt(1);
            }
            lblTotalBanco.setText("Banco: " + totalNoBanco + " questão" + (totalNoBanco != 1 ? "ões" : ""));

            String sqlCount = "SELECT COUNT(*) FROM questao q LEFT JOIN materia m ON m.id = q.materia_id" + where;
            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sqlCount)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) { totalFiltrado = rs.next() ? rs.getInt(1) : 0; }
            }

            lblContador.setText("Exibidas: " + totalFiltrado + " questão" + (totalFiltrado != 1 ? "ões" : ""));
            lblFiltrosAtivos.setText(filtrosAtivos > 0 ? filtrosAtivos + " filtro(s)" : "");
            lblFiltrosAtivos.setVisible(filtrosAtivos > 0);
            lblFiltrosAtivos.setManaged(filtrosAtivos > 0);

            boolean empty = totalFiltrado == 0;
            emptyState.setVisible(empty); emptyState.setManaged(empty);
            if (paginacaoBox != null) { paginacaoBox.setVisible(!empty); paginacaoBox.setManaged(!empty); }

            if (empty) {
                lblEmptyTitulo.setText(totalNoBanco == 0 ? "O banco de questões está vazio" : "Nenhuma questão com os filtros atuais");
                lblEmptyTexto.setText(totalNoBanco == 0
                    ? "Vá em Materiais, importe um PDF e clique em \"Extrair questões\"."
                    : "Tente limpar os filtros para ver as " + totalNoBanco + " questões do banco.");
                return;
            }

            String sqlPage =
                "SELECT q.id, q.materia_id, q.assunto, q.enunciado, q.banca, q.ano, q.tipo," +
                "       q.alternativa_correta, m.nome AS materia_nome, m.cor AS materia_cor" +
                " FROM questao q LEFT JOIN materia m ON m.id = q.materia_id" +
                where + " ORDER BY m.nome, q.assunto, q.ano DESC, q.id DESC" +
                " LIMIT " + POR_PAGINA + " OFFSET " + (paginaAtual * POR_PAGINA);

            List<Map<String, Object>> rows = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sqlPage)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id",          rs.getInt("id"));
                        row.put("enunciado",   rs.getString("enunciado"));
                        row.put("banca",       rs.getString("banca"));
                        row.put("ano",         rs.getObject("ano"));
                        row.put("tipo",        rs.getString("tipo"));
                        row.put("assunto",     rs.getString("assunto"));
                        row.put("materia",     rs.getString("materia_nome"));
                        row.put("cor",         rs.getString("materia_cor"));
                        row.put("alt_correta", rs.getObject("alternativa_correta"));
                        rows.add(row);
                        ids.add(rs.getInt("id"));
                    }
                }
            }

            if (!ids.isEmpty()) carregarAlternativasBatch(ids);
            for (Map<String, Object> row : rows) listaQuestoes.getChildren().add(criarCard(row));
            renderizarPaginacao();

        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void carregarAlternativasBatch(List<Integer> ids) throws SQLException {
        String ph  = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT questao_id, letra, texto, correta FROM alternativa" +
                     " WHERE questao_id IN (" + ph + ") ORDER BY questao_id, letra";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int qId = rs.getInt("questao_id");
                    Map<String, Object> alt = new HashMap<>();
                    alt.put("letra",   rs.getString("letra"));
                    alt.put("texto",   rs.getString("texto"));
                    alt.put("correta", rs.getInt("correta"));
                    cacheAlts.computeIfAbsent(qId, k -> new ArrayList<>()).add(alt);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CARD
    // ══════════════════════════════════════════════════════════════════════

    private VBox criarCard(Map<String, Object> row) {
        int     qId        = (int) row.get("id");
        boolean gabarito   = gabaritoAtivo.contains(qId);
        Integer selecionado = respostaSelecionada.get(qId);

        VBox card = new VBox();
        card.getStyleClass().add("card");
        card.setMaxWidth(1100);

        // ── Header ───────────────────────────────────────────────────────
        HBox header = new HBox(10);
        header.getStyleClass().add("card-header");
        header.setAlignment(Pos.CENTER_LEFT);

        String cor        = safeColor((String) row.get("cor"));
        String materiaRaw = (String) row.get("materia");
        String assuntoRaw = (String) row.get("assunto");
        String titulo     = materiaRaw != null ? materiaRaw + (assuntoRaw != null ? " · " + assuntoRaw : "")
                                               : (assuntoRaw != null ? assuntoRaw : "Sem matéria");

        Rectangle dot = new Rectangle(8, 8);
        dot.setArcWidth(2); dot.setArcHeight(2);
        dot.setStyle("-fx-fill: " + cor + ";");

        Label lTitulo = new Label(titulo);
        lTitulo.getStyleClass().add("card-title");
        HBox.setHgrow(lTitulo, Priority.ALWAYS);

        HBox badges = new HBox(5);
        badges.setAlignment(Pos.CENTER_RIGHT);
        if (row.get("banca") != null) badges.getChildren().add(badge((String) row.get("banca"), "badge-banca"));
        if (row.get("ano")   != null) badges.getChildren().add(badge(String.valueOf(row.get("ano")), "badge-ano"));
        if (row.get("tipo")  != null) badges.getChildren().add(badge(fmtTipo((String) row.get("tipo")), "badge-tipo"));

        if (gabarito && selecionado != null) {
            List<Map<String, Object>> alts = cacheAlts.getOrDefault(qId, List.of());
            boolean acertou = selecionado < alts.size()
                && ((Number) alts.get(selecionado).get("correta")).intValue() == 1;
            Label lRes = new Label(acertou ? "✅" : "❌");
            lRes.setStyle("-fx-font-size: 16px;");
            badges.getChildren().add(0, lRes);
        }
        header.getChildren().addAll(dot, lTitulo, badges);

        // ── Body ─────────────────────────────────────────────────────────
        VBox body = new VBox(8);
        body.setStyle("-fx-padding: 0 18 10 18;");
        body.setFillWidth(true);

        Label lEnunc = new Label((String) row.get("enunciado"));
        lEnunc.getStyleClass().add("q-text");
        lEnunc.setWrapText(true);
        lEnunc.setMaxWidth(Double.MAX_VALUE);
        body.getChildren().add(lEnunc);

        // ── Alternativas ─────────────────────────────────────────────────
        List<Map<String, Object>> alts = cacheAlts.getOrDefault(qId, List.of());
        if (!alts.isEmpty()) {
            VBox altsBox = new VBox(4);
            altsBox.setFillWidth(true);
            altsBox.setStyle("-fx-padding: 6 0 0 0;");

            for (int i = 0; i < alts.size(); i++) {
                final int idx = i;
                Map<String, Object> alt = alts.get(i);
                String  letra   = (String)  alt.get("letra");
                String  texto   = (String)  alt.get("texto");
                boolean correta = ((Number) alt.get("correta")).intValue() == 1;

                HBox altRow = new HBox(10);
                altRow.setAlignment(Pos.CENTER_LEFT);
                altRow.setStyle("-fx-padding: 5 8 5 8; -fx-background-radius: 6;");

                Label lLetra = new Label(letra);
                lLetra.setMinWidth(28);
                lLetra.setMaxWidth(28);
                lLetra.setAlignment(Pos.CENTER);
                lLetra.getStyleClass().add("opt-letter");

                Label lTexto = new Label(texto);
                lTexto.setWrapText(true);
                lTexto.setMinWidth(0);
                HBox.setHgrow(lTexto, Priority.ALWAYS);
                // ─── FIX DEFINITIVO: amarra a largura máxima do texto ao container ───
                // altsBox.widthProperty() reflete a largura real depois do layout.
                // Subtraímos 56px = 28 (letra) + 10 (spacing) + 18 (padding cada lado).
                lTexto.maxWidthProperty().bind(altsBox.widthProperty().subtract(56));
                lTexto.setStyle("-fx-font-size: 12px; -fx-text-fill: #9ca3af;");

                estiloAlt(altRow, lLetra, lTexto, gabarito, correta, selecionado, i);

                if (!gabarito) {
                    altRow.setOnMouseClicked(e -> {
                        respostaSelecionada.put(qId, idx);
                        int ci = listaQuestoes.getChildren().indexOf(card);
                        if (ci >= 0) listaQuestoes.getChildren().set(ci, criarCard(row));
                    });
                }

                altRow.getChildren().addAll(lLetra, lTexto);
                altsBox.getChildren().add(altRow);
            }
            body.getChildren().add(altsBox);
        }

        if (gabarito && selecionado != null && !alts.isEmpty()) {
            boolean acertou = selecionado < alts.size()
                && ((Number) alts.get(selecionado).get("correta")).intValue() == 1;
            Label lFb = new Label(acertou ? "✅  Resposta correta!" : "❌  Resposta incorreta.");
            lFb.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8 0 0 0; -fx-text-fill: "
                    + (acertou ? "#38d9a9" : "#ef4444") + ";");
            body.getChildren().add(lFb);
        }

        // ── Rodapé ───────────────────────────────────────────────────────
        HBox footer = new HBox(8);
        footer.setStyle("-fx-padding: 4 18 14 18;");
        footer.setAlignment(Pos.CENTER_LEFT);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnSalvar = new Button("🔖  Salvar");
        btnSalvar.getStyleClass().add("btn-ghost-sm");
        footer.getChildren().addAll(sp, btnSalvar);

        if (!alts.isEmpty()) {
            if (!gabarito) {
                Button btnResponder = new Button("▶  Responder");
                btnResponder.getStyleClass().add("btn-primary-sm");
                btnResponder.setDisable(selecionado == null);
                btnResponder.setOnAction(e -> confirmarResposta(qId, selecionado, alts, row, card));
                footer.getChildren().add(btnResponder);
            } else {
                Button btnReset = new Button("↺  Tentar novamente");
                btnReset.getStyleClass().add("btn-ghost-sm");
                btnReset.setOnAction(e -> {
                    respostaSelecionada.remove(qId);
                    gabaritoAtivo.remove(qId);
                    int ci = listaQuestoes.getChildren().indexOf(card);
                    if (ci >= 0) listaQuestoes.getChildren().set(ci, criarCard(row));
                });
                footer.getChildren().add(btnReset);
            }
        }

        card.getChildren().addAll(header, body, footer);
        return card;
    }

    private void estiloAlt(HBox row, Label lLetra, Label lTexto,
                            boolean gabarito, boolean correta, Integer sel, int idx) {
        if (gabarito) {
            if (correta) {
                row.setStyle("-fx-background-color:rgba(56,217,169,0.15);-fx-background-radius:6;-fx-padding:5 8 5 8;");
                lLetra.setStyle("-fx-background-color:#38d9a9;-fx-text-fill:#0d1117;-fx-font-weight:bold;-fx-background-radius:4;");
                lTexto.setStyle("-fx-font-size:12px;-fx-text-fill:#38d9a9;-fx-font-weight:bold;");
            } else if (sel != null && sel == idx) {
                row.setStyle("-fx-background-color:rgba(239,68,68,0.15);-fx-background-radius:6;-fx-padding:5 8 5 8;");
                lLetra.setStyle("-fx-background-color:#ef4444;-fx-text-fill:#ffffff;-fx-font-weight:bold;-fx-background-radius:4;");
                lTexto.setStyle("-fx-font-size:12px;-fx-text-fill:#ef4444;");
            }
        } else {
            if (sel != null && sel == idx) {
                row.setStyle("-fx-background-color:rgba(79,156,249,0.15);-fx-background-radius:6;-fx-padding:5 8 5 8;-fx-cursor:hand;");
                lLetra.setStyle("-fx-background-color:#4f9cf9;-fx-text-fill:#ffffff;-fx-font-weight:bold;-fx-background-radius:4;");
                lTexto.setStyle("-fx-font-size:12px;-fx-text-fill:#e8eaf0;-fx-font-weight:bold;");
            } else {
                row.setStyle("-fx-cursor:hand;-fx-padding:5 8 5 8;-fx-background-radius:6;");
                row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:rgba(79,156,249,0.07);-fx-background-radius:6;-fx-cursor:hand;-fx-padding:5 8 5 8;"));
                row.setOnMouseExited (e -> row.setStyle("-fx-cursor:hand;-fx-padding:5 8 5 8;-fx-background-radius:6;"));
            }
        }
    }

    private void confirmarResposta(int qId, Integer idxSel, List<Map<String, Object>> alts,
                                   Map<String, Object> row, VBox card) {
        if (idxSel == null) return;
        gabaritoAtivo.add(qId);
        boolean acertou = idxSel < alts.size() && ((Number) alts.get(idxSel).get("correta")).intValue() == 1;
        try { salvarResposta(qId, idxSel, acertou ? 1 : 0); } catch (SQLException ex) { ex.printStackTrace(); }
        int ci = listaQuestoes.getChildren().indexOf(card);
        if (ci >= 0) listaQuestoes.getChildren().set(ci, criarCard(row));
    }

    private void salvarResposta(int questaoId, int altEscolhida, int acertou) throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS resposta (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, questao_id INTEGER NOT NULL," +
                "alternativa_escolhida INTEGER NOT NULL, acertou INTEGER NOT NULL," +
                "created_at TEXT DEFAULT (datetime('now','localtime'))," +
                "FOREIGN KEY (questao_id) REFERENCES questao(id))");
        } catch (SQLException ignored) {}
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO resposta (questao_id, alternativa_escolhida, acertou) VALUES (?, ?, ?)")) {
            ps.setInt(1, questaoId); ps.setInt(2, altEscolhida); ps.setInt(3, acertou);
            ps.executeUpdate();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAGINAÇÃO
    // ══════════════════════════════════════════════════════════════════════

    private void renderizarPaginacao() {
        if (paginacaoBox == null) return;
        paginacaoBox.getChildren().clear();
        int total = (int) Math.ceil((double) totalFiltrado / POR_PAGINA);
        if (total <= 1) return;

        Button btnAnt = new Button("← Anterior");
        btnAnt.getStyleClass().add("btn-ghost-sm");
        btnAnt.setDisable(paginaAtual == 0);
        btnAnt.setOnAction(e -> { paginaAtual--; aplicarFiltros(); });

        HBox nums = new HBox(4);
        nums.setAlignment(Pos.CENTER);
        int ini = Math.max(0, paginaAtual - 2), fim = Math.min(total, ini + 5);
        for (int p = ini; p < fim; p++) {
            final int pg = p;
            Button b = new Button(String.valueOf(p + 1));
            b.getStyleClass().add(p == paginaAtual ? "btn-primary-sm" : "btn-ghost-sm");
            b.setOnAction(e -> { paginaAtual = pg; aplicarFiltros(); });
            nums.getChildren().add(b);
        }

        Button btnPrx = new Button("Próxima →");
        btnPrx.getStyleClass().add("btn-ghost-sm");
        btnPrx.setDisable(paginaAtual >= total - 1);
        btnPrx.setOnAction(e -> { paginaAtual++; aplicarFiltros(); });

        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.ALWAYS);
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        paginacaoBox.getChildren().addAll(btnAnt, sp1, nums, sp2, btnPrx);
    }

    // ══════════════════════════════════════════════════════════════════════
    // CHIPS / UTILS
    // ══════════════════════════════════════════════════════════════════════

    private void addChip(String texto, String cor, Runnable onRemove) {
        HBox chip = new HBox(5);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setStyle("-fx-background-color:rgba(79,156,249,0.10);-fx-background-radius:999;" +
                "-fx-padding:3 10 3 10;-fx-cursor:hand;" +
                "-fx-border-color:" + cor + ";-fx-border-radius:999;-fx-border-width:1;");
        Label lTxt = new Label(texto); lTxt.setStyle("-fx-font-size:11px;-fx-text-fill:" + cor + ";");
        Label lX   = new Label("✕");   lX.setStyle("-fx-font-size:10px;-fx-text-fill:" + cor + ";");
        chip.getChildren().addAll(lTxt, lX);
        chip.setOnMouseClicked(e -> { onRemove.run(); aplicarFiltros(); });
        chipsAtivos.getChildren().add(chip);
    }

    @FXML private void onLimparFiltros() {
        campoBusca.clear(); filtroMateria.setValue(null); filtroAssunto.setValue(null);
        if (filtroArquivo != null) filtroArquivo.setValue(null);
        filtroBanca.setValue(null); filtroAno.setValue(null); filtroTipo.setValue(null);
        soSalvas = false;
        btnSoSalvas.getStyleClass().remove("btn-primary-sm");
        btnSoSalvas.getStyleClass().add("btn-ghost-sm");
        paginaAtual = 0; aplicarFiltros();
    }

    @FXML private void onToggleSalvas() {
        soSalvas = !soSalvas;
        if (soSalvas) { btnSoSalvas.getStyleClass().remove("btn-ghost-sm"); btnSoSalvas.getStyleClass().add("btn-primary-sm"); }
        else          { btnSoSalvas.getStyleClass().remove("btn-primary-sm"); btnSoSalvas.getStyleClass().add("btn-ghost-sm"); }
        paginaAtual = 0; aplicarFiltros();
    }

    @FXML private void onNovaQuestao() { System.out.println("Nova questão"); }
    @FXML private void onImportarPdf() {
        FileChooser ch = new FileChooser();
        ch.setTitle("Importar PDF");
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = ch.showOpenDialog(null);
        if (f != null) System.out.println("PDF: " + f.getAbsolutePath());
    }

    private void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
    }

    private String mapTipo(String label) {
        return switch (label) {
            case "Múltipla escolha" -> "multipla_escolha";
            case "Certo/Errado"     -> "certo_errado";
            case "Dissertativa"     -> "dissertativa";
            default -> label;
        };
    }

    private String fmtTipo(String tipo) {
        return switch (tipo != null ? tipo : "") {
            case "multipla_escolha" -> "Múltipla";
            case "certo_errado"     -> "C/E";
            case "dissertativa"     -> "Dissertativa";
            default -> tipo != null ? tipo : "";
        };
    }

    private Label badge(String texto, String cls) {
        Label l = new Label(texto); l.getStyleClass().addAll("task-tag", cls); return l;
    }

    private String safeColor(String cor) {
        return (cor != null && !cor.isBlank()) ? cor : "#4f9cf9";
    }
}
