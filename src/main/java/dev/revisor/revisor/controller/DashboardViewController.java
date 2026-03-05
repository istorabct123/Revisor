package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.DatabaseManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardViewController implements Initializable {

    // ── Stat cards ──────────────────────────────────────────────────────
    @FXML private Label statQuestoes, statHoje;
    @FXML private Label statAcertos,  statAcertosDetalhe;
    @FXML private Label statSequencia, statStreakSub;
    @FXML private Label statHoras,     statHorasSub;

    // ── Gráficos / tabela (injetados via fx:id) ──────────────────────
    @FXML private VBox  barChartBox;
    @FXML private VBox  donutBox;
    @FXML private VBox  tabelaMaterias;
    @FXML private Label lblQtdMaterias;
    @FXML private Label lblMediaDiaria;
    @FXML private VBox  progressoBox;

    // ── Tabs ────────────────────────────────────────────────────────────
    @FXML private Label tabGeral, tabSemana, tabMateria;

    // ── Estado ──────────────────────────────────────────────────────────
    private long segundosSessao = 0;
    private Timeline timerSessao, autoRefresh;

    // ════════════════════════════════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        garantirTabelas();
        iniciarTimer();
        refresh();

        autoRefresh = new Timeline(new KeyFrame(Duration.seconds(15), e -> refresh()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    // ── Timer de sessão ─────────────────────────────────────────────────
    // segundosSessao = apenas os segundos DESTA sessão (para gravar no banco)
    // segundosHojeAntes = total já gravado hoje antes de abrir o app (para exibir)
    private long segundosHojeAntes = 0;

    private void iniciarTimer() {
        segundosSessao    = 0;                  // começa do zero nesta sessão
        segundosHojeAntes = buscarTempoHoje();  // histórico já gravado hoje

        timerSessao = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            segundosSessao++;
            atualizarLabelTempo();
        }));
        timerSessao.setCycleCount(Timeline.INDEFINITE);
        timerSessao.play();
        atualizarLabelTempo();

        Runtime.getRuntime().addShutdownHook(new Thread(this::persistirTempo));
    }

    private void atualizarLabelTempo() {
        if (statHoras == null) return;
        long total = segundosHojeAntes + segundosSessao;  // exibe o acumulado do dia
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0)
            statHoras.setText(String.format("%dh %02dm %02ds", h, m, s));
        else
            statHoras.setText(String.format("%02dm %02ds", m, s));
    }

    private long buscarTempoHoje() {
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COALESCE(SUM(duracao_segundos),0) FROM sessao_estudo" +
                " WHERE date(inicio)='" + LocalDate.now() + "'")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) { return 0; }
    }

    private void persistirTempo() {
        if (segundosSessao <= 5) return;  // grava só o delta desta sessão
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO sessao_estudo (inicio, fim, duracao_segundos) VALUES (?,?,?)")) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            ps.setString(1, now); ps.setString(2, now); ps.setLong(3, segundosSessao);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    // ════════════════════════════════════════════════════════════════════
    // REFRESH
    // ════════════════════════════════════════════════════════════════════

    public void refresh() {
        try {
            Stats s = carregarStats();
            atualizarStatCards(s);
            atualizarGrafico7Dias(s);
            atualizarDonut(s);
            atualizarTabela(s);
            atualizarProgresso(s);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ════════════════════════════════════════════════════════════════════
    // DADOS
    // ════════════════════════════════════════════════════════════════════

    private static class Stats {
        int total, acertos, hoje, sequencia;
        double taxa;
        List<MateriaStats> materias = new ArrayList<>();
        int[] questoesPorDia = new int[7];   // D-6 .. hoje
        int[] acertosPorDia  = new int[7];
    }

    private static class MateriaStats {
        String nome, cor;
        int questoes, acertos;
        double taxa() { return questoes > 0 ? acertos * 100.0 / questoes : 0; }
    }

    private Stats carregarStats() throws SQLException {
        Stats s = new Stats();
        String hoje = LocalDate.now().toString();

        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            // Totais
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*), COALESCE(SUM(acertou),0) FROM resposta")) {
                if (rs.next()) { s.total = rs.getInt(1); s.acertos = rs.getInt(2); }
            }
            // Hoje
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM resposta WHERE date(created_at)='" + hoje + "'")) {
                if (rs.next()) s.hoje = rs.getInt(1);
            }
            s.taxa = s.total > 0 ? s.acertos * 100.0 / s.total : 0;
            s.sequencia = calcularSequencia(st);

            // 7 dias
            for (int d = 6; d >= 0; d--) {
                String dia = LocalDate.now().minusDays(d).toString();
                try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                        "SELECT COUNT(*), COALESCE(SUM(acertou),0) FROM resposta WHERE date(created_at)=?")) {
                    ps.setString(1, dia);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            s.questoesPorDia[6-d] = rs.getInt(1);
                            s.acertosPorDia[6-d]  = rs.getInt(2);
                        }
                    }
                }
            }

            // Por matéria/assunto
            try (ResultSet rs = st.executeQuery("""
                SELECT COALESCE(m.nome, q.assunto, 'Sem matéria') nome,
                       COALESCE(m.cor, '#4f9cf9') cor,
                       COUNT(*) total, COALESCE(SUM(r.acertou),0) acertos
                FROM resposta r
                JOIN questao q ON q.id=r.questao_id
                LEFT JOIN materia m ON m.id=q.materia_id
                GROUP BY nome ORDER BY total DESC LIMIT 10
            """)) {
                while (rs.next()) {
                    MateriaStats ms = new MateriaStats();
                    ms.nome = rs.getString("nome");
                    ms.cor  = rs.getString("cor");
                    ms.questoes = rs.getInt("total");
                    ms.acertos  = rs.getInt("acertos");
                    s.materias.add(ms);
                }
            }
        }
        return s;
    }

    private int calcularSequencia(Statement st) throws SQLException {
        List<String> dias = new ArrayList<>();

        try (ResultSet rs = st.executeQuery(
                "SELECT DISTINCT date(created_at) FROM resposta ORDER BY date(created_at) DESC LIMIT 365")) {
            while (rs.next()) {
                dias.add(rs.getString(1));
            }
        }

        int seq = 0;
        LocalDate d = LocalDate.now();

        for (String ds : dias) {
            if (LocalDate.parse(ds).equals(d)) {
                seq++;
                d = d.minusDays(1);
            } else {
                break;
            }
        }

        return seq;
    }

    // ════════════════════════════════════════════════════════════════════
    // STAT CARDS
    // ════════════════════════════════════════════════════════════════════

    private void atualizarStatCards(Stats s) {
        setText(statQuestoes,       String.valueOf(s.total));
        setText(statHoje,           "+" + s.hoje + " hoje");
        setText(statAcertos,        String.format("%.0f%%", s.taxa));
        setText(statAcertosDetalhe, s.acertos + " acertos de " + s.total);
        setText(statSequencia,      s.sequencia + "d");
        setText(statStreakSub,      "🔥 Recorde: " + s.sequencia + " dias");
        // statHoras é atualizado pelo timer — não sobrescrever
    }

    // ════════════════════════════════════════════════════════════════════
    // GRÁFICO DE BARRAS 7 DIAS
    // ════════════════════════════════════════════════════════════════════

    private void atualizarGrafico7Dias(Stats s) {
        if (barChartBox == null) return;
        barChartBox.getChildren().clear();

        int maxVal = 1;
        for (int v : s.questoesPorDia) if (v > maxVal) maxVal = v;

        double W = 340, barH = 110, barW = 30, gap = (W - 7 * barW) / 8.0;
        Canvas c = new Canvas(W, barH + 30);
        GraphicsContext gc = c.getGraphicsContext2D();

        String[] nomes = {"Dom","Seg","Ter","Qua","Qui","Sex","Sáb"};
        int dow = (LocalDate.now().getDayOfWeek().getValue() % 7); // 0=Dom

        int total7 = 0;
        for (int i = 0; i < 7; i++) {
            total7 += s.questoesPorDia[i];
            double x = gap + i * (barW + gap);

            // Barra fundo
            gc.setFill(Color.web("#1f2937"));
            gc.fillRoundRect(x, 4, barW, barH - 4, 5, 5);

            // Barra total
            int q = s.questoesPorDia[i];
            if (q > 0) {
                double h = (q * (barH - 8.0) / maxVal);
                gc.setFill(i == 6 ? Color.web("#4f9cf9") : Color.web("#374151"));
                gc.fillRoundRect(x, barH - h, barW, h, 5, 5);

                // Barra acertos em cima
                int a = s.acertosPorDia[i];
                if (a > 0) {
                    double ah = (a * (barH - 8.0) / maxVal);
                    gc.setFill(Color.web("#38d9a9", 0.85));
                    gc.fillRoundRect(x, barH - ah, barW, ah, 5, 5);
                }

                // Valor
                gc.setFill(Color.web("#9ca3af")); gc.setFont(Font.font(9));
                gc.fillText(String.valueOf(q), x + barW/2 - 4, barH - h - 2);
            }

            // Label dia
            int diaSemana = (dow - (6 - i) + 7) % 7;
            gc.setFill(i == 6 ? Color.web("#4f9cf9") : Color.web("#6b7280"));
            gc.setFont(Font.font(9));
            gc.fillText(nomes[diaSemana], x + barW/2 - 8, barH + 18);
        }

        // Legenda
        HBox leg = new HBox(14);
        leg.setAlignment(Pos.CENTER_RIGHT);
        leg.setPadding(new Insets(2, 4, 0, 0));
        leg.getChildren().addAll(legItem("#374151","Total"), legItem("#38d9a9","Acertos"), legItem("#4f9cf9","Hoje"));

        double media = total7 / 7.0;
        setText(lblMediaDiaria, String.format("Média: %.1f questões/dia", media));

        barChartBox.getChildren().addAll(c, leg);
    }

    private HBox legItem(String cor, String txt) {
        Rectangle r = new Rectangle(10, 10);
        r.setArcWidth(3); r.setArcHeight(3); r.setFill(Color.web(cor));
        Label l = new Label(txt); l.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 10px;");
        HBox h = new HBox(4, r, l); h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ════════════════════════════════════════════════════════════════════
    // DONUT
    // ════════════════════════════════════════════════════════════════════

    private void atualizarDonut(Stats s) {
        if (donutBox == null) return;
        donutBox.getChildren().clear();

        double sz = 110;
        Canvas c = new Canvas(sz, sz);
        GraphicsContext gc = c.getGraphicsContext2D();

        double cx = sz/2, cy = sz/2, r = 40, sw = 16;
        int erros = s.total - s.acertos;

        if (s.total == 0) {
            gc.setStroke(Color.web("#374151")); gc.setLineWidth(sw);
            gc.strokeArc(cx-r, cy-r, r*2, r*2, 0, 360, javafx.scene.shape.ArcType.OPEN);
            gc.setFill(Color.web("#6b7280")); gc.setFont(Font.font("System", FontWeight.BOLD, 14));
            gc.fillText("0%", cx - 10, cy + 5);
        } else {
            double angAc = s.acertos * 360.0 / s.total;
            gc.setStroke(Color.web("#38d9a9")); gc.setLineWidth(sw);
            gc.strokeArc(cx-r, cy-r, r*2, r*2, 90, -angAc, javafx.scene.shape.ArcType.OPEN);
            if (erros > 0) {
                gc.setStroke(Color.web("#ef4444")); gc.setLineWidth(sw);
                gc.strokeArc(cx-r, cy-r, r*2, r*2, 90-angAc, -(360-angAc), javafx.scene.shape.ArcType.OPEN);
            }
            String pct = String.format("%.0f%%", s.taxa);
            gc.setFill(Color.web("#e8eaf0")); gc.setFont(Font.font("System", FontWeight.BOLD, 14));
            gc.fillText(pct, cx - (pct.length() > 3 ? 16 : 12), cy + 5);
        }

        VBox leg = new VBox(6);
        leg.setAlignment(Pos.CENTER_LEFT);
        leg.setPadding(new Insets(0, 0, 0, 10));
        leg.getChildren().addAll(
            legItemV("#38d9a9", "✓ Acertos: " + s.acertos),
            legItemV("#ef4444", "✗ Erros: " + erros),
            legItemV("#4f9cf9", "Total: " + s.total)
        );

        HBox row = new HBox(8, c, leg);
        row.setAlignment(Pos.CENTER_LEFT);
        donutBox.getChildren().add(row);
    }

    private HBox legItemV(String cor, String txt) {
        Rectangle r = new Rectangle(9, 9);
        r.setArcWidth(2); r.setArcHeight(2); r.setFill(Color.web(cor));
        Label l = new Label(txt); l.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11px;");
        HBox h = new HBox(5, r, l); h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ════════════════════════════════════════════════════════════════════
    // TABELA DE MATÉRIAS
    // ════════════════════════════════════════════════════════════════════

    private void atualizarTabela(Stats s) {
        if (tabelaMaterias == null) return;
        tabelaMaterias.getChildren().clear();
        setText(lblQtdMaterias, s.materias.size() + " matéria" + (s.materias.size() != 1 ? "s" : ""));

        if (s.materias.isEmpty()) {
            Label v = new Label("Nenhuma matéria com questões respondidas ainda.");
            v.setStyle("-fx-text-fill:#6b7280; -fx-font-size:12px; -fx-padding:12 0 12 14;");
            tabelaMaterias.getChildren().add(v);
            return;
        }

        for (MateriaStats ms : s.materias) {
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-border-color: transparent transparent #1f2937 transparent; -fx-border-width: 0 0 1 0;");

            // Nome + cor
            HBox nomeCell = new HBox(8);
            nomeCell.setPrefWidth(220); nomeCell.setAlignment(Pos.CENTER_LEFT);
            nomeCell.setPadding(new Insets(8, 8, 8, 14));
            Rectangle dot = new Rectangle(10, 10);
            dot.setArcWidth(3); dot.setArcHeight(3);
            try { dot.setFill(Color.web(ms.cor)); } catch (Exception ex) { dot.setFill(Color.web("#4f9cf9")); }
            String nomeExib = ms.nome.length() > 26 ? ms.nome.substring(0, 26) + "…" : ms.nome;
            Label lN = new Label(nomeExib); lN.setStyle("-fx-text-fill:#e8eaf0; -fx-font-size:12px;");
            nomeCell.getChildren().addAll(dot, lN);

            Label lQ = cell(String.valueOf(ms.questoes), 80, "#9ca3af");
            Label lA = cell(String.valueOf(ms.acertos),  70, "#38d9a9");
            Label lE = cell(String.valueOf(ms.questoes - ms.acertos), 70, "#ef4444");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

            double taxa = ms.taxa();
            String corTaxa = taxa >= 70 ? "#38d9a9" : taxa >= 50 ? "#ffd93d" : "#ef4444";
            Label lT = new Label(String.format("%.0f%%", taxa));
            lT.setStyle("-fx-text-fill:" + corTaxa + "; -fx-font-weight:bold; -fx-font-size:12px; -fx-padding:0 14 0 0;");

            // Mini barra de progresso
            StackPane barra = new StackPane();
            barra.setPrefWidth(80); barra.setAlignment(Pos.CENTER_LEFT);
            Region bg = new Region(); bg.setPrefHeight(6); bg.setPrefWidth(80);
            bg.setStyle("-fx-background-color:#1f2937; -fx-background-radius:3;");
            Region fg = new Region(); fg.setPrefHeight(6); fg.setPrefWidth(Math.max(4, taxa * 0.8));
            fg.setStyle("-fx-background-color:" + corTaxa + "; -fx-background-radius:3;");
            barra.getChildren().addAll(bg, fg);
            StackPane.setAlignment(fg, Pos.CENTER_LEFT);
            HBox barraBox = new HBox(barra); barraBox.setPadding(new Insets(0, 8, 0, 0));

            row.getChildren().addAll(nomeCell, lQ, lA, lE, sp, barraBox, lT);
            tabelaMaterias.getChildren().add(row);
        }
    }

    private Label cell(String txt, double w, String cor) {
        Label l = new Label(txt);
        l.setPrefWidth(w); l.setPadding(new Insets(8, 0, 8, 8));
        l.setStyle("-fx-text-fill:" + cor + "; -fx-font-size:12px;");
        return l;
    }

    // ════════════════════════════════════════════════════════════════════
    // PROGRESSO GERAL
    // ════════════════════════════════════════════════════════════════════

    private void atualizarProgresso(Stats s) {
        if (progressoBox == null) return;
        progressoBox.getChildren().clear();

        // Meta: 10 questões/dia (poderia ser configurável)
        int metaDiaria = 10;
        double pctHoje = Math.min(100, s.hoje * 100.0 / metaDiaria);
        double pctAcerto = s.taxa;

        // Horas estudadas hoje
        long hHoje = segundosSessao / 3600;
        long mHoje = (segundosSessao % 3600) / 60;
        int  metaHoras = 2; // 2h/dia
        double pctHoras = Math.min(100, segundosSessao * 100.0 / (metaHoras * 3600));

        progressoBox.getChildren().addAll(
            barraProgresso("Questões hoje", s.hoje + " / " + metaDiaria, pctHoje, "#4f9cf9"),
            barraProgresso("Tempo de estudo", hHoje + "h " + mHoje + "m / " + metaHoras + "h meta", pctHoras, "#38d9a9"),
            barraProgresso("Taxa de acerto (meta 70%)", String.format("%.0f%%", pctAcerto), pctAcerto, pctAcerto >= 70 ? "#38d9a9" : "#ffd93d")
        );
    }

    private VBox barraProgresso(String titulo, String subtitulo, double pct, String cor) {
        VBox vb = new VBox(4);
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label lT = new Label(titulo); lT.setStyle("-fx-text-fill:#9ca3af; -fx-font-size:11px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label lV = new Label(subtitulo); lV.setStyle("-fx-text-fill:#e8eaf0; -fx-font-size:11px; -fx-font-weight:bold;");
        header.getChildren().addAll(lT, sp, lV);

        StackPane track = new StackPane();
        track.setAlignment(Pos.CENTER_LEFT);
        Region bg = new Region(); bg.setPrefHeight(8); bg.setMaxWidth(Double.MAX_VALUE);
        bg.setStyle("-fx-background-color:#1f2937; -fx-background-radius:4;");
        Region fg = new Region(); fg.setPrefHeight(8);
        fg.setStyle("-fx-background-color:" + cor + "; -fx-background-radius:4;");
        // bind width to track
        track.widthProperty().addListener((obs, ov, nv) ->
            fg.setPrefWidth(Math.max(0, nv.doubleValue() * pct / 100.0)));
        track.getChildren().addAll(bg, fg);
        StackPane.setAlignment(fg, Pos.CENTER_LEFT);


        vb.getChildren().addAll(header, track);
        return vb;
    }

    // ════════════════════════════════════════════════════════════════════
    // TABS
    // ════════════════════════════════════════════════════════════════════

    @FXML private void onTabGeral(MouseEvent e)   { setTab(tabGeral); }
    @FXML private void onTabSemana(MouseEvent e)  { setTab(tabSemana); }
    @FXML private void onTabMateria(MouseEvent e) { setTab(tabMateria); }

    private void setTab(Label ativa) {
        for (Label t : new Label[]{tabGeral, tabSemana, tabMateria})
            if (t != null) t.getStyleClass().remove("tab-active");
        if (ativa != null) ativa.getStyleClass().add("tab-active");
    }

    // ════════════════════════════════════════════════════════════════════
    // HANDLERS (mantidos para compatibilidade com FXML antigo)
    // ════════════════════════════════════════════════════════════════════

    @FXML private void onOption1(MouseEvent e) {}
    @FXML private void onOption2(MouseEvent e) {}
    @FXML private void onOption3(MouseEvent e) {}
    @FXML private void onOption4(MouseEvent e) {}
    @FXML private void onConfirmAnswer()       {}
    @FXML private void onSaveQuestion()        {}
    @FXML private void onReportQuestion()      {}
    @FXML private void onNextQuestion(MouseEvent e) {}
    @FXML private void onVerDesempenho(MouseEvent e) {}
    @FXML private void onImportPdf()   {}
    @FXML private void onNewQuestion() {}

    // ════════════════════════════════════════════════════════════════════
    // BANCO
    // ════════════════════════════════════════════════════════════════════

    private void garantirTabelas() {
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS resposta (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    questao_id INTEGER NOT NULL,
                    alternativa_escolhida INTEGER NOT NULL,
                    acertou INTEGER NOT NULL,
                    created_at TEXT DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY (questao_id) REFERENCES questao(id)
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS sessao_estudo (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    inicio TEXT NOT NULL, fim TEXT,
                    duracao_segundos INTEGER DEFAULT 0
                )""");
        } catch (SQLException ignored) {}
    }

    // ════════════════════════════════════════════════════════════════════
    // UTIL
    // ════════════════════════════════════════════════════════════════════

    private void setText(Label l, String v) { if (l != null) l.setText(v); }
}
