package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller da aba Desempenho.
 * Mostra KPIs, atividade semanal, desempenho por matéria,
 * metas e histórico recente — tudo consultado em tempo real no banco.
 */
public class DesempenhoViewController implements Initializable {

    // ── KPIs ──────────────────────────────────────────────────────────────
    @FXML private Label kpiQuestoes, kpiQuestoesDelta;
    @FXML private Label kpiAcerto,   kpiAcertoDelta;
    @FXML private Label kpiSequencia, kpiRecorde;
    @FXML private Label kpiHoras,    kpiMetaHoras;

    // ── TABS ──────────────────────────────────────────────────────────────
    @FXML private Label tabGeral, tabSemana, tabMateria;

    // ── FILTRO ────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> filtroPeriodo;

    // ── GRÁFICO SEMANAL ───────────────────────────────────────────────────
    @FXML private HBox barChartBox, barLabelsBox;
    @FXML private Label lblMediaDiaria;

    // ── DISTRIBUIÇÃO ──────────────────────────────────────────────────────
    @FXML private VBox distribuicaoBox;

    // ── TABELA MATÉRIAS ───────────────────────────────────────────────────
    @FXML private VBox tabelaMaterias, emptyMaterias;
    @FXML private Label lblTotalMaterias;

    // ── METAS ─────────────────────────────────────────────────────────────
    @FXML private VBox metasBox;

    // ── PROGRESSO GERAL ───────────────────────────────────────────────────
    @FXML private ProgressBar progQuestoes, progHoras, progAcertoAlvo;
    @FXML private Label lblPctQuestoes, lblPctHoras, lblPctAcertoAlvo;

    // ── HISTÓRICO ─────────────────────────────────────────────────────────
    @FXML private VBox historicoBox, emptyHistorico;

    // ── METAS CONFIGURÁVEIS (em memória por enquanto) ─────────────────────
    private int metaQuestoesSemana  = 100;
    private int metaHorasMes        = 60;
    private int metaAcertoAlvo      = 70;   // %

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        filtroPeriodo.getItems().addAll(
            "Hoje", "Últimos 7 dias", "Últimos 30 dias", "Este mês", "Todo o período"
        );
        filtroPeriodo.setValue("Últimos 30 dias");
        filtroPeriodo.valueProperty().addListener((obs, o, n) -> recarregar());

        recarregar();
    }

    private void recarregar() {
        try {
            carregarKpis();
            carregarGraficoSemanal();
            carregarDistribuicao();
            carregarTabelaMaterias();
            carregarMetas();
            carregarHistorico();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // KPIs
    // ══════════════════════════════════════════════════════════════════════

    private void carregarKpis() throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        // Total de questões respondidas
        int total = 0, acertos = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) total, SUM(acertou) acertos FROM resposta")) {
            if (rs.next()) {
                total   = rs.getInt("total");
                acertos = rs.getInt("acertos");
            }
        }
        kpiQuestoes.setText(String.format("%,d", total));
        int taxa = total > 0 ? (int) (acertos * 100.0 / total) : 0;
        kpiAcerto.setText(taxa + "%");

        // Hoje
        int hoje = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM resposta WHERE date(respondido_em) = date('now')")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) hoje = rs.getInt(1);
        }
        kpiQuestoesDelta.setText((hoje > 0 ? "+" : "") + hoje + " hoje");
        kpiQuestoesDelta.getStyleClass().removeAll("delta-up","delta-down");
        kpiQuestoesDelta.getStyleClass().add(hoje > 0 ? "delta-up" : "delta-neutral");

        // Taxa delta vs semana passada
        int taxaSemanaPassada = taxaPeriodo(conn, 14, 7);
        int taxaAtual         = taxaPeriodo(conn, 7, 0);
        int delta = taxaAtual - taxaSemanaPassada;
        String sinal = delta >= 0 ? "↑ +" : "↓ ";
        kpiAcertoDelta.setText(sinal + delta + "% vs semana passada");
        kpiAcertoDelta.getStyleClass().removeAll("delta-up","delta-down","delta-neutral");
        kpiAcertoDelta.getStyleClass().add(delta >= 0 ? "delta-up" : "delta-down");

        // Sequência e recorde
        int sequencia = calcularSequencia(conn);
        kpiSequencia.setText(sequencia + "d");
        kpiRecorde.setText("🏆 Recorde: " + sequencia + " dias");

        // Horas estudadas no mês
        int minutosMes = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(minutos),0) FROM sessao_estudo " +
                "WHERE strftime('%Y-%m', data) = strftime('%Y-%m', 'now')")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) minutosMes = rs.getInt(1);
        }
        int horas = minutosMes / 60;
        kpiHoras.setText(horas + "h");
        kpiMetaHoras.setText("Meta mensal: " + metaHorasMes + "h");

        // Progresso geral
        double pctQ = Math.min(1.0, total / (double) Math.max(1, metaQuestoesSemana * 4));
        double pctH = Math.min(1.0, horas / (double) metaHorasMes);
        double pctA = Math.min(1.0, taxa  / (double) metaAcertoAlvo);

        progQuestoes.setProgress(pctQ);
        progHoras.setProgress(pctH);
        progAcertoAlvo.setProgress(pctA);

        lblPctQuestoes.setText((int)(pctQ * 100) + "%");
        lblPctHoras.setText((int)(pctH * 100) + "%");
        lblPctAcertoAlvo.setText(taxa + "%");
    }

    private int taxaPeriodo(Connection conn, int diasAtras, int diasAteHoje) throws SQLException {
        String sql = """
            SELECT COUNT(*) total, SUM(acertou) acertos FROM resposta
            WHERE date(respondido_em) BETWEEN date('now', ? || ' days') AND date('now', ? || ' days')
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "-" + diasAtras);
            ps.setString(2, diasAteHoje == 0 ? "0" : "-" + diasAteHoje);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int t = rs.getInt("total");
                int a = rs.getInt("acertos");
                return t > 0 ? (int)(a * 100.0 / t) : 0;
            }
        }
        return 0;
    }

    private int calcularSequencia(Connection conn) throws SQLException {
        // Conta dias consecutivos até hoje com pelo menos 1 resposta
        int streak = 0;
        LocalDate dia = LocalDate.now();
        for (int i = 0; i < 365; i++) {
            String d = dia.minusDays(i).toString();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM resposta WHERE date(respondido_em) = ?")) {
                ps.setString(1, d);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) streak++;
                else if (i > 0) break; // interrompeu a sequência
            }
        }
        return streak;
    }

    // ══════════════════════════════════════════════════════════════════════
    // GRÁFICO SEMANAL (barras)
    // ══════════════════════════════════════════════════════════════════════

    private void carregarGraficoSemanal() throws SQLException {
        barChartBox.getChildren().clear();
        barLabelsBox.getChildren().clear();

        Connection conn = DatabaseManager.getConnection();
        String[] dias = {"Dom","Seg","Ter","Qua","Qui","Sex","Sáb"};
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        int[] contagens = new int[7];
        int max = 1;

        for (int i = 6; i >= 0; i--) {
            String d = LocalDate.now().minusDays(i).format(fmt);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM resposta WHERE date(respondido_em) = ?")) {
                ps.setString(1, d);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) contagens[6 - i] = rs.getInt(1);
            }
            max = Math.max(max, contagens[6 - i]);
        }

        int total7 = Arrays.stream(contagens).sum();
        lblMediaDiaria.setText(String.format("Média: %.0f questões/dia", total7 / 7.0));

        final int barMaxH = 100;
        for (int i = 0; i < 7; i++) {
            int cnt = contagens[i];
            double ratio = cnt / (double) max;
            int height = Math.max(4, (int)(ratio * barMaxH));

            // Barra
            VBox barCol = new VBox();
            barCol.setAlignment(Pos.BOTTOM_CENTER);
            barCol.setPrefWidth(30);
            barCol.setPrefHeight(barMaxH);

            // Valor
            Label val = new Label(cnt > 0 ? String.valueOf(cnt) : "");
            val.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af; -fx-font-family: 'Courier New';");

            Rectangle bar = new Rectangle(22, height);
            bar.setArcWidth(5);
            bar.setArcHeight(5);
            boolean isToday = (i == 6);
            bar.setStyle("-fx-fill: " + (isToday ? "#4f9cf9" : (cnt > 0 ? "#2a4a7f" : "#1a1e2a")) + ";");

            barCol.getChildren().addAll(val, bar);
            barChartBox.getChildren().add(barCol);

            // Label dia
            LocalDate diaLabel = LocalDate.now().minusDays(6 - i);
            Label lblDia = new Label(dias[diaLabel.getDayOfWeek().getValue() % 7]);
            lblDia.setPrefWidth(30);
            lblDia.setAlignment(Pos.CENTER);
            lblDia.setStyle("-fx-font-size: 10px; -fx-text-fill: " +
                    (isToday ? "#4f9cf9" : "#6b7280") + "; -fx-font-family: 'Courier New';");
            barLabelsBox.getChildren().add(lblDia);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DISTRIBUIÇÃO POR MATÉRIA
    // ══════════════════════════════════════════════════════════════════════

    private void carregarDistribuicao() throws SQLException {
        distribuicaoBox.getChildren().clear();

        String sql = """
            SELECT m.nome, m.cor,
                   COUNT(r.id)   AS total,
                   SUM(r.acertou) AS acertos
            FROM resposta r
            JOIN questao q ON q.id = r.questao_id
            JOIN materia m ON m.id = q.materia_id
            GROUP BY m.id
            ORDER BY total DESC
            LIMIT 6
        """;

        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            boolean temDados = false;
            while (rs.next()) {
                temDados = true;
                String nome   = rs.getString("nome");
                String cor    = rs.getString("cor");
                int total     = rs.getInt("total");
                int acertos   = rs.getInt("acertos");
                int taxa      = total > 0 ? (int)(acertos * 100.0 / total) : 0;

                HBox linha = new HBox(10);
                linha.setAlignment(Pos.CENTER_LEFT);
                linha.setStyle("-fx-padding: 6 0 6 0; -fx-border-color: rgba(255,255,255,0.05); -fx-border-width: 0 0 1 0;");

                Rectangle dot = new Rectangle(8, 8);
                dot.setArcWidth(2);
                dot.setArcHeight(2);
                dot.setStyle("-fx-fill: " + cor + ";");

                Label lNome = new Label(nome);
                lNome.setPrefWidth(120);
                lNome.setStyle("-fx-font-size: 12px; -fx-text-fill: #e8eaf0;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Label lTotal = new Label(total + " q");
                lTotal.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280; -fx-font-family: 'Courier New';");

                String taxaCor = taxa >= 70 ? "#38d9a9" : taxa >= 50 ? "#ffd93d" : "#ff6b6b";
                Label lTaxa = new Label(taxa + "%");
                lTaxa.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + taxaCor +
                        "; -fx-font-family: 'Courier New'; -fx-min-width: 36; -fx-alignment: CENTER_RIGHT;");

                linha.getChildren().addAll(dot, lNome, spacer, lTotal, lTaxa);
                distribuicaoBox.getChildren().add(linha);
            }

            if (!temDados) {
                Label vazio = new Label("Nenhum dado disponível.");
                vazio.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px; -fx-padding: 12 0 12 0;");
                distribuicaoBox.getChildren().add(vazio);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TABELA POR MATÉRIA
    // ══════════════════════════════════════════════════════════════════════

    private void carregarTabelaMaterias() throws SQLException {
        tabelaMaterias.getChildren().clear();

        String sql = """
            SELECT m.nome, m.cor,
                   COUNT(r.id)      AS total,
                   SUM(r.acertou)   AS acertos,
                   COUNT(r.id) - SUM(r.acertou) AS erros
            FROM resposta r
            JOIN questao q ON q.id = r.questao_id
            JOIN materia m ON m.id = q.materia_id
            GROUP BY m.id
            ORDER BY total DESC
        """;

        int count = 0;
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                count++;
                String nome   = rs.getString("nome");
                String cor    = rs.getString("cor");
                int total     = rs.getInt("total");
                int acertos   = rs.getInt("acertos");
                int erros     = rs.getInt("erros");
                int taxa      = total > 0 ? (int)(acertos * 100.0 / total) : 0;
                double progresso = total > 0 ? acertos / (double) total : 0;

                HBox row = new HBox(0);
                row.getStyleClass().add("table-row");
                row.setAlignment(Pos.CENTER_LEFT);

                HBox nomeBox = new HBox(8);
                nomeBox.setAlignment(Pos.CENTER_LEFT);
                nomeBox.setPrefWidth(200);
                nomeBox.setStyle("-fx-padding: 0 0 0 14;");

                Rectangle dot = new Rectangle(10, 10);
                dot.setArcWidth(3);
                dot.setArcHeight(3);
                dot.setStyle("-fx-fill: " + cor + ";");
                Label lNome = new Label(nome);
                lNome.getStyleClass().add("row-name");
                nomeBox.getChildren().addAll(dot, lNome);

                Label lTotal   = new Label(total + " questões");
                lTotal.getStyleClass().add("row-count");
                lTotal.setPrefWidth(110);

                Label lAcertos = new Label(String.valueOf(acertos));
                lAcertos.setStyle("-fx-font-size: 12px; -fx-text-fill: #38d9a9; -fx-font-family: 'Courier New'; -fx-pref-width: 90;");

                Label lErros = new Label(String.valueOf(erros));
                lErros.setStyle("-fx-font-size: 12px; -fx-text-fill: #ff6b6b; -fx-font-family: 'Courier New'; -fx-pref-width: 90;");

                ProgressBar pb = new ProgressBar(progresso);
                pb.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(pb, Priority.ALWAYS);
                String pbClass = taxa >= 70 ? "prog-green" : taxa >= 50 ? "prog-yellow" : "prog-red";
                pb.getStyleClass().addAll("prog-bar", pbClass);

                String taxaCor = taxa >= 70 ? "#38d9a9" : taxa >= 50 ? "#ffd93d" : "#ff6b6b";
                Label lTaxa = new Label(taxa + "%");
                lTaxa.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + taxaCor
                        + "; -fx-font-family: 'Courier New'; -fx-pref-width: 60; -fx-alignment: CENTER;");

                row.getChildren().addAll(nomeBox, lTotal, lAcertos, lErros, pb, lTaxa);
                tabelaMaterias.getChildren().add(row);
            }
        }

        emptyMaterias.setVisible(count == 0);
        emptyMaterias.setManaged(count == 0);
        lblTotalMaterias.setText(count + " matéria" + (count != 1 ? "s" : ""));
    }

    // ══════════════════════════════════════════════════════════════════════
    // METAS DA SEMANA
    // ══════════════════════════════════════════════════════════════════════

    private void carregarMetas() throws SQLException {
        metasBox.getChildren().clear();

        Connection conn = DatabaseManager.getConnection();

        // Questões respondidas nesta semana
        int questoesSemana = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM resposta WHERE date(respondido_em) >= date('now', 'weekday 0', '-7 days')")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) questoesSemana = rs.getInt(1);
        }

        // Horas esta semana
        int minutosSemana = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(minutos),0) FROM sessao_estudo WHERE date(data) >= date('now', 'weekday 0', '-7 days')")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) minutosSemana = rs.getInt(1);
        }
        int horasSemana = minutosSemana / 60;

        addMetaItem("Questões respondidas",  questoesSemana, metaQuestoesSemana, "#4f9cf9");
        addMetaItem("Horas de estudo",       horasSemana,    20,                 "#f093fb");
    }

    private void addMetaItem(String label, int atual, int meta, String cor) {
        double pct = Math.min(1.0, atual / (double) Math.max(1, meta));

        VBox item = new VBox(6);
        item.setStyle("-fx-padding: 8 18 8 18; -fx-border-color: rgba(255,255,255,0.05); -fx-border-width: 0 0 1 0;");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label lLabel = new Label(label);
        lLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #e8eaf0;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label lPct = new Label(atual + " / " + meta);
        lPct.setStyle("-fx-font-size: 12px; -fx-text-fill: " + cor + "; -fx-font-family: 'Courier New';");
        header.getChildren().addAll(lLabel, sp, lPct);

        ProgressBar pb = new ProgressBar(pct);
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setStyle("-fx-pref-height: 6; -fx-background-radius: 99; -fx-border-radius: 99;");

        // colorir de acordo com a cor passada
        item.getChildren().addAll(header, pb);
        metasBox.getChildren().add(item);
    }

    // ══════════════════════════════════════════════════════════════════════
    // HISTÓRICO RECENTE
    // ══════════════════════════════════════════════════════════════════════

    private void carregarHistorico() throws SQLException {
        historicoBox.getChildren().clear();

        String sql = """
            SELECT r.respondido_em, r.acertou,
                   q.enunciado, q.banca, q.ano,
                   m.nome AS materia
            FROM resposta r
            JOIN questao q ON q.id = r.questao_id
            LEFT JOIN materia m ON m.id = q.materia_id
            ORDER BY r.respondido_em DESC
            LIMIT 8
        """;

        int count = 0;
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                count++;
                boolean acertou  = rs.getInt("acertou") == 1;
                String enunciado = rs.getString("enunciado");
                String banca     = rs.getString("banca");
                String materia   = rs.getString("materia");
                String quando    = rs.getString("respondido_em");

                HBox row = new HBox(12);
                row.getStyleClass().add("activity-item");
                row.setAlignment(Pos.CENTER_LEFT);

                Label icon = new Label(acertou ? "✓" : "✗");
                icon.setStyle("-fx-min-width: 28; -fx-min-height: 28; -fx-max-width: 28; -fx-max-height: 28; " +
                        "-fx-background-radius: 7; -fx-alignment: CENTER; -fx-font-weight: bold; " +
                        "-fx-background-color: " + (acertou ? "rgba(56,217,169,0.15)" : "rgba(255,107,107,0.15)") + "; " +
                        "-fx-text-fill: " + (acertou ? "#38d9a9" : "#ff6b6b") + ";");

                VBox info = new VBox(2);
                HBox.setHgrow(info, Priority.ALWAYS);
                String resumo = enunciado != null && enunciado.length() > 70
                        ? enunciado.substring(0, 70) + "…" : enunciado;
                Label lRes = new Label(resumo);
                lRes.getStyleClass().add("act-title");
                lRes.setWrapText(false);
                Label lMeta = new Label(
                    (materia != null ? materia : "—") +
                    (banca != null ? "  ·  " + banca : "")
                );
                lMeta.getStyleClass().add("act-meta");
                info.getChildren().addAll(lRes, lMeta);

                Label lTempo = new Label(quando != null ? quando.substring(0, 10) : "");
                lTempo.getStyleClass().add("act-time");

                row.getChildren().addAll(icon, info, lTempo);
                historicoBox.getChildren().add(row);
            }
        }

        emptyHistorico.setVisible(count == 0);
        emptyHistorico.setManaged(count == 0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AÇÕES
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void onTabGeral(MouseEvent e)   { setActiveTab(tabGeral);   recarregar(); }
    @FXML private void onTabSemana(MouseEvent e)  { setActiveTab(tabSemana);  }
    @FXML private void onTabMateria(MouseEvent e) { setActiveTab(tabMateria); }

    private void setActiveTab(Label sel) {
        for (Label t : new Label[]{tabGeral, tabSemana, tabMateria}) {
            t.getStyleClass().remove("tab-active");
        }
        sel.getStyleClass().add("tab-active");
    }

    @FXML private void onEditarMetas() {
        TextInputDialog d = new TextInputDialog(String.valueOf(metaQuestoesSemana));
        d.setTitle("Meta Semanal");
        d.setHeaderText("Metas de estudo");
        d.setContentText("Questões por semana:");
        d.showAndWait().ifPresent(v -> {
            try { metaQuestoesSemana = Integer.parseInt(v.trim()); recarregar(); }
            catch (NumberFormatException ignored) {}
        });
    }

    @FXML private void onExportar() {
        System.out.println("📤 Exportar relatório (futuro: PDF/CSV)");
    }

    @FXML private void onVerTodoHistorico(MouseEvent e) {
        System.out.println("Abrir histórico completo");
    }
}
