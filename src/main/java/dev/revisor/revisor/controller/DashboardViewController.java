package dev.revisor.revisor.controller;

import dev.revisor.revisor.db.ArquivoDao;
import dev.revisor.revisor.model.Arquivo;
import dev.revisor.revisor.service.ArquivoParserService;
import dev.revisor.revisor.service.PdfExtractorService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ResourceBundle;

/** Controller para o conteúdo da tela Dashboard (stats, questão do dia, etc). */
public class DashboardViewController implements Initializable {

    @FXML private Label tabGeral;
    @FXML private Label tabSemana;
    @FXML private Label tabMateria;

    @FXML private HBox opt1, opt2, opt3, opt4;
    @FXML private VBox taskList;

    private final ArquivoDao arquivoDao = new ArquivoDao();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Nada específico por enquanto
    }

    @FXML
    private void onTabGeral(MouseEvent e) {
        setActiveTab(tabGeral);
    }

    @FXML
    private void onTabSemana(MouseEvent e) {
        setActiveTab(tabSemana);
    }

    @FXML
    private void onTabMateria(MouseEvent e) {
        setActiveTab(tabMateria);
    }

    private void setActiveTab(Label selected) {
        for (Label tab : new Label[]{tabGeral, tabSemana, tabMateria}) {
            tab.getStyleClass().remove("tab-active");
        }
        selected.getStyleClass().add("tab-active");
    }

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

    @FXML
    private void onConfirmAnswer() {
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
        boolean isCorrect = selected == opt2;
        selected.getStyleClass().remove("q-option-selected");
        if (isCorrect) {
            selected.getStyleClass().add("q-option-correct");
            showAlert("✅ Correto! Muito bem!");
        } else {
            selected.getStyleClass().add("q-option-wrong");
            opt2.getStyleClass().add("q-option-correct");
            showAlert("❌ Incorreto. A resposta certa era a alternativa B.");
        }
    }

    @FXML
    private void onNextQuestion(MouseEvent e) {
        System.out.println("Próxima questão →");
    }

    @FXML
    private void onImportPdf() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar PDF");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = chooser.showOpenDialog(null);
        if (file == null) return;

        try {
            // 1️⃣ Copiar para pasta da aplicação
            Path destino = copiarParaDiretorioAplicacao(file);

            // 2️⃣ Extrair texto do PDF
            String textoExtraido = PdfExtractorService.extractText(file);

            // 3️⃣ Salvar no banco (AGORA COM TEXTO)
            Arquivo arquivo = arquivoDao.inserir(
                    null,
                    destino.toString(),
                    file.getName(),
                    file.length(),
                    textoExtraido
            );

            // 4️⃣ Processar questões automaticamente
            ArquivoParserService.processarArquivo(arquivo.getId());

            showAlert("PDF importado e processado com sucesso!\n\n" + file.getName());

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Erro ao importar PDF: " + e.getMessage());
        }
    }

    @FXML
    private void onNewQuestion() {
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

    @FXML
    private void onVerDesempenho(MouseEvent e) {
        System.out.println("Abrir tela de desempenho completo");
    }

    @FXML
    private void onAddTask(MouseEvent e) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nova Tarefa");
        dialog.setHeaderText("Adicionar tarefa ao dia");
        dialog.setContentText("Tarefa:");
        dialog.getDialogPane().getStylesheets().add(
            getClass().getResource("/dev/revisor/revisor/styles.css").toExternalForm()
        );
        dialog.showAndWait().ifPresent(task -> System.out.println("Nova tarefa: " + task));
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Revisor");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Path copiarParaDiretorioAplicacao(File origem) throws IOException {
        String baseDir = System.getProperty("user.home") + "/.revisor/pdfs";
        Path dir = Path.of(baseDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path destino = dir.resolve(origem.getName());
        int contador = 1;
        while (Files.exists(destino)) {
            String nome = origem.getName();
            int dot = nome.lastIndexOf('.');
            String baseName = (dot > 0) ? nome.substring(0, dot) : nome;
            String ext = (dot > 0) ? nome.substring(dot) : "";
            destino = dir.resolve(baseName + "_" + contador + ext);
            contador++;
        }
        Files.copy(origem.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        return destino;
    }
}
