package dev.revisor.revisor.controller;


import dev.revisor.revisor.service.PdfExtractorService;
import dev.revisor.revisor.db.ArquivoDao;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;
/**
 * Controller do Dashboard principal.
 * Cada método marcado com @FXML corresponde a um evento no Dashboard.fxml.
 */
public class DashboardController implements Initializable {

    // ── FXML refs ─────────────────────────────────────────────────────────
    @FXML private HBox navDashboard;
    @FXML private HBox navMateriais;
    @FXML private HBox navQuestoes;
    @FXML private HBox navDesempenho;
    @FXML private HBox navPlanejamento;
    @FXML private HBox navRevisoes;

    @FXML private Label tabGeral;
    @FXML private Label tabSemana;
    @FXML private Label tabMateria;

    @FXML private Label statQuestoes;
    @FXML private Label statAcertos;
    @FXML private Label statHoras;
    @FXML private Label statSequencia;

    @FXML private HBox opt1, opt2, opt3, opt4;
    @FXML private VBox taskList;

    // ── INITIALIZE ────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Aqui você pode carregar dados reais do banco / arquivos
        setupNavHovers();
    }

    /**
     * Adiciona efeito de "active" ao clicar em itens de nav.
     * Remove a classe nav-active de todos e aplica no clicado.
     */
    private void setupNavHovers() {
        HBox[] navItems = {navDashboard, navMateriais, navQuestoes,
                           navDesempenho, navPlanejamento, navRevisoes};

        for (HBox item : navItems) {
            item.setOnMouseClicked(e -> {
                for (HBox n : navItems) {
                    n.getStyleClass().remove("nav-active");
                }
                item.getStyleClass().add("nav-active");
            });
        }
    }

    // ── TABS ──────────────────────────────────────────────────────────────
    @FXML
    private void onTabGeral(MouseEvent e) {
        setActiveTab(tabGeral);
        // Carregar dados gerais
    }

    @FXML
    private void onTabSemana(MouseEvent e) {
        setActiveTab(tabSemana);
        // Carregar dados semanais
    }

    @FXML
    private void onTabMateria(MouseEvent e) {
        setActiveTab(tabMateria);
        // Filtrar por matéria
    }

    private void setActiveTab(Label selected) {
        for (Label tab : new Label[]{tabGeral, tabSemana, tabMateria}) {
            tab.getStyleClass().remove("tab-active");
        }
        selected.getStyleClass().add("tab-active");
    }

    // ── QUESTÕES — OPÇÕES ────────────────────────────────────────────────
    // Ao clicar numa opção, remove seleção das demais e marca a clicada
    @FXML private void onOption1(MouseEvent e) { selectOption(opt1); }
    @FXML private void onOption2(MouseEvent e) { selectOption(opt2); }
    @FXML private void onOption3(MouseEvent e) { selectOption(opt3); }
    @FXML private void onOption4(MouseEvent e) { selectOption(opt4); }

    private void selectOption(HBox chosen) {
        for (HBox opt : new HBox[]{opt1, opt2, opt3, opt4}) {
            opt.getStyleClass().remove("q-option-selected");
            opt.getStyleClass().remove("q-option-correct");
            opt.getStyleClass().remove("q-option-wrong");
        }
        chosen.getStyleClass().add("q-option-selected");
    }

    // ── CONFIRMAR RESPOSTA ────────────────────────────────────────────────
    @FXML
    private void onConfirmAnswer() {
        // Identificar qual opção está selecionada
        HBox selected = null;
        for (HBox opt : new HBox[]{opt1, opt2, opt3, opt4}) {
            if (opt.getStyleClass().contains("q-option-selected")) {
                selected = opt;
                break;
            }
        }

        if (selected == null) {
            showAlert("Selecione uma alternativa antes de confirmar.");
            return;
        }

        // opt2 = resposta correta (exemplo)
        // Aqui você consultaria seu banco de dados/modelo
        boolean isCorrect = selected == opt2;

        selected.getStyleClass().remove("q-option-selected");

        if (isCorrect) {
            selected.getStyleClass().add("q-option-correct");
            showAlert("✅ Correto! Muito bem!");
        } else {
            selected.getStyleClass().add("q-option-wrong");
            opt2.getStyleClass().add("q-option-correct"); // mostra a certa
            showAlert("❌ Incorreto. A resposta certa era a alternativa B.");
        }
    }

    // ── NAVEGAÇÃO DE QUESTÕES ─────────────────────────────────────────────
    @FXML
    private void onNextQuestion(MouseEvent e) {
        // Carregar próxima questão do banco de dados
        // Exemplo: questionService.next();
        System.out.println("Próxima questão →");
    }

    // ── MATERIAIS ─────────────────────────────────────────────────────────
    @FXML
    private void onImportPdf() {

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Importar PDF");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        java.io.File file = chooser.showOpenDialog(null);

        if (file != null) {

            try {

                // 1️⃣ Extrair texto
                String texto = PdfExtractorService.extractText(file);

                // 2️⃣ Salvar no banco
                ArquivoDao dao = new ArquivoDao();
                dao.inserir(
                        null,
                        file.getAbsolutePath(),
                        file.getName(),
                        file.length(),
                        texto
                );

                showAlert("PDF importado e texto salvo com sucesso!");

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Erro ao importar PDF.");
            }
        }
    }

    // ── QUESTÕES ──────────────────────────────────────────────────────────
    @FXML
    private void onNewQuestion() {
        // Abrir nova janela/modal para criar questão
        System.out.println("Criar nova questão");
    }

    @FXML
    private void onSaveQuestion() {
        System.out.println("Questão salva nos favoritos 🔖");
    }

    @FXML
    private void onReportQuestion() {
        System.out.println("Questão reportada ⚑");
    }

    // ── TAREFAS ───────────────────────────────────────────────────────────
    @FXML
    private void onAddTask(MouseEvent e) {
        // Abrir diálogo para adicionar nova tarefa
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nova Tarefa");
        dialog.setHeaderText("Adicionar tarefa ao dia");
        dialog.setContentText("Tarefa:");
        // Estilizar o dialog
        dialog.getDialogPane().getStylesheets().add(
            getClass().getResource("styles.css").toExternalForm()
        );
        dialog.showAndWait().ifPresent(task -> {
            System.out.println("Nova tarefa: " + task);
            // Adicionar ao taskList dinamicamente
        });
    }

    // ── DESEMPENHO ────────────────────────────────────────────────────────
    @FXML
    private void onVerDesempenho(MouseEvent e) {
        System.out.println("Abrir tela de desempenho completo");
        // Trocar a cena/tela
    }

    // ── CONFIGURAÇÕES ─────────────────────────────────────────────────────
    @FXML
    private void onSettings(MouseEvent e) {
        System.out.println("Abrir configurações ⚙");
    }

    @FXML
    private void onAddSubject(MouseEvent e) {
        System.out.println("Adicionar nova matéria");
    }

    // ── UTILS ─────────────────────────────────────────────────────────────
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("StudyOS");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
