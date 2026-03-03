package dev.revisor.revisor.controller;

import dev.revisor.revisor.service.PdfExtractorService;
import dev.revisor.revisor.service.QuestaoParserService;
import dev.revisor.revisor.service.QuestaoParserService.QuestaoRaw;
import dev.revisor.revisor.service.QuestaoParserService.Alternativa;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller do pipeline de debug: Extração → Parser → Salvar
 *
 * Etapa 1 — "Testar extração": lê o PDF com Apache Tika, mostra o texto bruto.
 * Etapa 2 — "Testar parser":   processa o texto com QuestaoParserService, lista as questões.
 * Etapa 3 — "Salvar no banco": persiste apenas depois da confirmação do usuário.
 */
public class PipelineDebugController implements Initializable {

    // ── TOPBAR ────────────────────────────────────────────────────────────
    @FXML private Label  lblArquivoSelecionado;
    @FXML private Button btnExtrair;

    // ── STEPPER ───────────────────────────────────────────────────────────
    @FXML private VBox   step1Box, step2Box, step3Box;
    @FXML private Label  step1Num, step2Num, step3Num;
    @FXML private Region connector1, connector2;

    // ── ETAPA 1 ───────────────────────────────────────────────────────────
    @FXML private VBox   panelEtapa1;
    @FXML private Label  lblEtapa1Status;
    @FXML private TextArea textoExtraido;
    @FXML private Label  lblCharCount;

    // ── ETAPA 2 ───────────────────────────────────────────────────────────
    @FXML private VBox   panelEtapa2;
    @FXML private Label  lblEtapa2Status;
    @FXML private Button btnParsear;
    @FXML private Label  statTotalQ, statComAlts, statCE, statBanca, statAno;
    @FXML private VBox   listaQuestoesParser;
    @FXML private Label  lblConfirmTitle;

    // ── ETAPA 3 ───────────────────────────────────────────────────────────
    @FXML private VBox   panelEtapa3;
    @FXML private Label  lblEtapa3Status;
    @FXML private Button btnSalvar;
    @FXML private VBox   logSalvamento;
    @FXML private HBox   resultadoFinal;
    @FXML private Label  lblResultadoFinal;

    // ── ESTADO ────────────────────────────────────────────────────────────
    private File   arquivoSelecionado;
    private List<QuestaoRaw> questoesParseadas;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        textoExtraido.textProperty().addListener((obs, o, n) -> {
            int chars = n == null ? 0 : n.length();
            lblCharCount.setText(chars > 0 ? chars + " chars" : "");
            btnParsear.setDisable(chars < 10);
            if (chars >= 10) ativarStep(2);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // SELEÇÃO DO PDF
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void onSelecionarPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar PDF para debug");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        File f = chooser.showOpenDialog(lblArquivoSelecionado.getScene().getWindow());
        if (f == null) return;

        arquivoSelecionado = f;
        lblArquivoSelecionado.setText(f.getName());
        btnExtrair.setDisable(false);

        // Reseta pipeline
        resetarPipeline();
    }

    // ══════════════════════════════════════════════════════════════════════
    // ETAPA 1 — EXTRAÇÃO
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void onTestarExtracao() {
        if (arquivoSelecionado == null) return;

        setStatus(lblEtapa1Status, "⏳ Extraindo...", "status-running");
        btnExtrair.setDisable(true);
        textoExtraido.clear();
        listaQuestoesParser.getChildren().clear();
        desativarStep(2);
        desativarStep(3);

        // Roda em thread separada para não travar a UI
        new Thread(() -> {
            try {
                String texto = PdfExtractorService.extractText(arquivoSelecionado);

                Platform.runLater(() -> {
                    textoExtraido.setText(texto);
                    setStatus(lblEtapa1Status,
                        "✓ " + texto.length() + " chars extraídos", "status-ok");
                    btnExtrair.setDisable(false);
                    ativarStep(2);
                    habilitarPainel(panelEtapa2);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    textoExtraido.setText("ERRO: " + e.getMessage());
                    setStatus(lblEtapa1Status, "✗ Erro na extração", "status-error");
                    btnExtrair.setDisable(false);
                });
            }
        }, "extrator-thread").start();
    }

    @FXML
    private void onCopiarTexto() {
        javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
        cc.putString(textoExtraido.getText());
        cb.setContent(cc);
    }

    @FXML
    private void onLimparTexto() {
        textoExtraido.clear();
        listaQuestoesParser.getChildren().clear();
        desativarStep(2);
        desativarStep(3);
        habilitarPainel(panelEtapa1); // mantém etapa 1 ativa
    }

    // ══════════════════════════════════════════════════════════════════════
    // ETAPA 2 — PARSER
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void onTestarParser() {
        String texto = textoExtraido.getText();
        if (texto == null || texto.isBlank()) return;

        setStatus(lblEtapa2Status, "⏳ Parseando...", "status-running");
        btnParsear.setDisable(true);
        listaQuestoesParser.getChildren().clear();
        desativarStep(3);

        new Thread(() -> {
            try {
                List<QuestaoRaw> questoes = QuestaoParserService.parsear(texto, null);

                Platform.runLater(() -> {
                    questoesParseadas = questoes;
                    renderizarQuestoes(questoes);
                    btnParsear.setDisable(false);

                    if (questoes.isEmpty()) {
                        setStatus(lblEtapa2Status, "⚠ Nenhuma questão detectada", "status-warn");
                        btnSalvar.setDisable(true);
                    } else {
                        setStatus(lblEtapa2Status,
                            "✓ " + questoes.size() + " questão(ões) identificada(s)", "status-ok");
                        ativarStep(3);
                        habilitarPainel(panelEtapa3);
                        btnSalvar.setDisable(false);
                        lblConfirmTitle.setText(
                            "Pronto para salvar " + questoes.size() + " questão(ões)"
                        );
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    setStatus(lblEtapa2Status, "✗ Erro: " + e.getMessage(), "status-error");
                    btnParsear.setDisable(false);
                    adicionarLogItem(listaQuestoesParser,
                        "ERRO: " + e.getMessage(), "#ff6b6b");
                });
            }
        }, "parser-thread").start();
    }

    private void renderizarQuestoes(List<QuestaoRaw> questoes) {
        // Stats
        long comAlts = questoes.stream().filter(q -> !q.alternativas.isEmpty()).count();
        long ce      = questoes.stream().filter(q -> "certo_errado".equals(q.tipo)).count();
        String banca = questoes.stream()
            .map(q -> q.banca).filter(b -> b != null).findFirst().orElse("—");
        String ano   = questoes.stream()
            .map(q -> q.ano).filter(a -> a != null).map(String::valueOf).findFirst().orElse("—");

        statTotalQ.setText(String.valueOf(questoes.size()));
        statComAlts.setText(String.valueOf(comAlts));
        statCE.setText(String.valueOf(ce));
        statBanca.setText(banca);
        statAno.setText(ano);

        // Cards de questão
        for (int i = 0; i < questoes.size(); i++) {
            listaQuestoesParser.getChildren().add(
                criarCardQuestao(i + 1, questoes.get(i))
            );
        }
    }

    private VBox criarCardQuestao(int num, QuestaoRaw q) {
        VBox card = new VBox(8);
        card.setStyle(
            "-fx-background-color: #13161e; " +
            "-fx-background-radius: 10; " +
            "-fx-border-radius: 10; " +
            "-fx-border-color: rgba(255,255,255,0.07); " +
            "-fx-border-width: 1; " +
            "-fx-padding: 12 14 12 14;"
        );

        // Header do card
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label numLabel = new Label("#" + num);
        numLabel.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-text-fill: #4f9cf9; -fx-font-family: 'Courier New'; " +
            "-fx-background-color: rgba(79,156,249,0.12); " +
            "-fx-background-radius: 5; -fx-padding: 2 8 2 8;"
        );

        // Badges de metadados
        HBox badges = new HBox(5);
        badges.setAlignment(Pos.CENTER_LEFT);

        if (q.banca != null)
            badges.getChildren().add(criarBadge(q.banca, "#f093fb", "rgba(240,147,251,0.12)"));
        if (q.ano != null)
            badges.getChildren().add(criarBadge(String.valueOf(q.ano), "#38d9a9", "rgba(56,217,169,0.12)"));
        if (q.tipo != null)
            badges.getChildren().add(criarBadge(formatarTipo(q.tipo), "#ffd93d", "rgba(255,217,61,0.12)"));
        if (q.assunto != null)
            badges.getChildren().add(criarBadge(q.assunto, "#9ca3af", "rgba(156,163,175,0.12)"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Indicador de alternativa correta
        Label gabLabel = new Label();
        if (q.alternativaCorreta != null) {
            String gab = q.alternativas.isEmpty()
                ? (q.alternativaCorreta == 0 ? "CERTO" : "ERRADO")
                : "Gab: " + (char)('A' + q.alternativaCorreta);
            gabLabel.setText(gab);
            gabLabel.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold; " +
                "-fx-text-fill: #38d9a9; -fx-font-family: 'Courier New'; " +
                "-fx-background-color: rgba(56,217,169,0.12); " +
                "-fx-background-radius: 5; -fx-padding: 2 8 2 8;"
            );
        }

        header.getChildren().addAll(numLabel, badges, spacer, gabLabel);

        // Enunciado
        Label enunciado = new Label(q.enunciado);
        enunciado.setStyle("-fx-font-size: 13px; -fx-text-fill: #e8eaf0; -fx-line-spacing: 3;");
        enunciado.setWrapText(true);

        card.getChildren().addAll(header, enunciado);

        // Alternativas
        if (!q.alternativas.isEmpty()) {
            VBox altsBox = new VBox(4);
            altsBox.setStyle("-fx-padding: 4 0 0 0;");

            for (int i = 0; i < q.alternativas.size(); i++) {
                Alternativa alt = q.alternativas.get(i);
                boolean correta = q.alternativaCorreta != null && q.alternativaCorreta == i;

                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);

                Label letra = new Label(alt.letra);
                letra.setStyle(
                    "-fx-font-size: 10px; -fx-font-weight: bold; " +
                    "-fx-font-family: 'Courier New'; " +
                    "-fx-min-width: 22; -fx-alignment: CENTER; " +
                    "-fx-background-radius: 4; -fx-padding: 2 5 2 5; " +
                    (correta
                        ? "-fx-background-color: rgba(56,217,169,0.2); -fx-text-fill: #38d9a9;"
                        : "-fx-background-color: #222736; -fx-text-fill: #9ca3af;")
                );

                Label texto = new Label(alt.texto);
                texto.setStyle(
                    "-fx-font-size: 12px; -fx-text-fill: " +
                    (correta ? "#38d9a9" : "#9ca3af") + ";"
                );
                texto.setWrapText(false);
                HBox.setHgrow(texto, Priority.ALWAYS);

                row.getChildren().addAll(letra, texto);
                altsBox.getChildren().add(row);
            }

            card.getChildren().add(altsBox);
        }

        return card;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ETAPA 3 — SALVAR
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void onSalvarNoBanco() {
        if (questoesParseadas == null || questoesParseadas.isEmpty()) return;

        btnSalvar.setDisable(true);
        logSalvamento.getChildren().clear();
        resultadoFinal.getChildren().clear();

        setStatus(lblEtapa3Status, "⏳ Salvando...", "status-running");
        adicionarLogItem(logSalvamento, "Iniciando salvamento de "
            + questoesParseadas.size() + " questões...", "#9ca3af");

        new Thread(() -> {
            try {
                int salvas = QuestaoParserService.salvarNoBanco(questoesParseadas);

                Platform.runLater(() -> {
                    adicionarLogItem(logSalvamento,
                        "✓ " + salvas + " questões inseridas com sucesso.", "#38d9a9");
                    adicionarLogItem(logSalvamento,
                        "✓ Alternativas vinculadas.", "#38d9a9");

                    setStatus(lblEtapa3Status,
                        "✓ " + salvas + " salvas", "status-ok");

                    concluirStep(1);
                    concluirStep(2);
                    concluirStep(3);

                    lblResultadoFinal.setText(
                        "🎉  " + salvas + " questão(ões) adicionada(s) ao banco!"
                    );
                    lblResultadoFinal.setStyle(
                        "-fx-font-size: 15px; -fx-font-weight: bold; " +
                        "-fx-text-fill: #38d9a9;"
                    );
                    resultadoFinal.getChildren().add(lblResultadoFinal);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    adicionarLogItem(logSalvamento, "✗ ERRO: " + e.getMessage(), "#ff6b6b");
                    setStatus(lblEtapa3Status, "✗ Falha ao salvar", "status-error");
                    btnSalvar.setDisable(false);
                });
            }
        }, "salvar-thread").start();
    }

    // ══════════════════════════════════════════════════════════════════════
    // STEPPER HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void ativarStep(int step) {
        Label num  = stepNum(step);
        VBox  box  = stepBox(step);
        num.getStyleClass().setAll("step-num", "step-num-active");
        box.getStyleClass().setAll("step-box", "step-active");
        if (step == 2) connector1.getStyleClass().setAll("step-connector", "step-connector-active");
        if (step == 3) connector2.getStyleClass().setAll("step-connector", "step-connector-active");
    }

    private void desativarStep(int step) {
        Label num  = stepNum(step);
        VBox  box  = stepBox(step);
        num.getStyleClass().setAll("step-num", "step-num-pending");
        box.getStyleClass().setAll("step-box", "step-pending");
        if (step == 2) connector1.getStyleClass().setAll("step-connector");
        if (step == 3) connector2.getStyleClass().setAll("step-connector");
    }

    private void concluirStep(int step) {
        Label num = stepNum(step);
        VBox  box = stepBox(step);
        num.setText("✓");
        num.getStyleClass().setAll("step-num", "step-num-done");
        box.getStyleClass().setAll("step-box", "step-done");
        if (step >= 2) connector1.getStyleClass().setAll("step-connector", "step-connector-done");
        if (step >= 3) connector2.getStyleClass().setAll("step-connector", "step-connector-done");
    }

    private Label stepNum(int step) {
        return switch (step) { case 1 -> step1Num; case 2 -> step2Num; default -> step3Num; };
    }

    private VBox stepBox(int step) {
        return switch (step) { case 1 -> step1Box; case 2 -> step2Box; default -> step3Box; };
    }

    private void habilitarPainel(VBox painel) {
        painel.getStyleClass().remove("pipeline-panel-disabled");
    }

    private void resetarPipeline() {
        textoExtraido.clear();
        listaQuestoesParser.getChildren().clear();
        logSalvamento.getChildren().clear();
        questoesParseadas = null;

        setStatus(lblEtapa1Status, "", "");
        setStatus(lblEtapa2Status, "", "");
        setStatus(lblEtapa3Status, "", "");

        btnExtrair.setDisable(false);
        btnParsear.setDisable(true);
        btnSalvar.setDisable(true);

        desativarStep(2);
        desativarStep(3);
        ativarStep(1);

        step1Num.setText("1");
        step2Num.setText("2");
        step3Num.setText("3");

        habilitarPainel(panelEtapa1);
        panelEtapa2.getStyleClass().add("pipeline-panel-disabled");
        panelEtapa3.getStyleClass().add("pipeline-panel-disabled");
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITÁRIOS DE UI
    // ══════════════════════════════════════════════════════════════════════

    private void setStatus(Label label, String texto, String styleClass) {
        label.setText(texto);
        label.getStyleClass().setAll("status-badge", styleClass);
    }

    private Label criarBadge(String texto, String cor, String bg) {
        Label l = new Label(texto);
        l.setStyle(
            "-fx-font-size: 10px; -fx-font-weight: bold; " +
            "-fx-text-fill: " + cor + "; " +
            "-fx-background-color: " + bg + "; " +
            "-fx-background-radius: 999; -fx-padding: 2 8 2 8;"
        );
        return l;
    }

    private String formatarTipo(String tipo) {
        return switch (tipo != null ? tipo : "") {
            case "multipla_escolha" -> "Múltipla";
            case "certo_errado"     -> "Certo/Errado";
            case "dissertativa"     -> "Dissertativa";
            default -> tipo;
        };
    }

    private void adicionarLogItem(VBox container, String mensagem, String cor) {
        Label l = new Label("› " + mensagem);
        l.setStyle(
            "-fx-font-size: 12px; -fx-font-family: 'Courier New'; " +
            "-fx-text-fill: " + cor + "; -fx-padding: 2 0 2 0;"
        );
        container.getChildren().add(l);
    }
}
