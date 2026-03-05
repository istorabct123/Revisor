package dev.revisor.revisor.service;

import dev.revisor.revisor.db.DatabaseManager;

import java.io.File;
import java.sql.*;
import java.util.List;

/**
 * Orquestra o pipeline:
 *   arquivo.caminho  →  PdfExtractorService  →  QuestaoParserService  →  banco
 *
 * Esta versão tem logging detalhado em System.out para facilitar diagnóstico.
 * Todos os pontos de retorno antecipado são logados.
 */
public class ArquivoParserService {

    // ══════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Processa um único arquivo: extrai texto do PDF (se necessário), parseia
     * e persiste as questões no banco.
     *
     * @param arquivoId ID da linha na tabela `arquivo`
     * @return número de questões criadas (0 se nenhuma encontrada)
     */
    public static int processarArquivo(int arquivoId) throws Exception {
        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("▶ processarArquivo(%d)%n", arquivoId);

        // ── Passo 1: buscar dados do arquivo ────────────────────────────
        garantirColunas();
        DadosArquivo dados = buscarDados(arquivoId);

        if (dados == null) {
            System.out.printf("⚠️  Arquivo %d não encontrado na tabela 'arquivo'%n", arquivoId);
            return 0;
        }
        System.out.printf("📂 Arquivo: %s%n", dados.nomeOriginal);
        System.out.printf("   caminho:        %s%n", dados.caminho);
        System.out.printf("   materia_id:     %s%n", dados.materiaId);
        System.out.printf("   texto_extraido: %s%n",
            dados.textoExtraido == null ? "null" :
            dados.textoExtraido.isBlank() ? "(vazio)" :
            dados.textoExtraido.length() + " chars");

        // ── Passo 2: extrair texto do PDF se ainda não extraído ──────────
        if (dados.textoExtraido == null || dados.textoExtraido.isBlank()) {
            System.out.println("📄 Texto ainda não extraído — iniciando extração do PDF…");

            if (dados.caminho == null || dados.caminho.isBlank()) {
                System.out.println("❌ Caminho do arquivo está vazio no banco. Impossível extrair.");
                return 0;
            }

            File file = new File(dados.caminho);
            if (!file.exists()) {
                System.out.printf("❌ Arquivo não encontrado no disco: %s%n", dados.caminho);
                System.out.println("   Dica: o arquivo pode ter sido movido após a importação.");
                return 0;
            }

            System.out.printf("   Tamanho no disco: %.1f KB%n", file.length() / 1024.0);

            String texto = PdfExtractorService.extractText(file);

            if (texto == null || texto.isBlank()) {
                System.out.println("❌ PdfExtractorService retornou texto vazio.");
                System.out.println("   Possível causa: PDF é uma imagem escaneada (sem texto embutido).");
                return 0;
            }

            System.out.printf("✅ Texto extraído: %d chars%n", texto.length());
            System.out.printf("   Primeiros 200 chars: %s%n", texto.substring(0, Math.min(200, texto.length())).replace("\n", "↵"));

            atualizarTextoExtraido(arquivoId, texto);
            dados.textoExtraido = texto;
        } else {
            System.out.println("♻️  Usando texto já extraído do banco.");
        }

        // ── Passo 3: parsear ─────────────────────────────────────────────
        System.out.println("🔍 Iniciando parser…");
        List<QuestaoParserService.QuestaoRaw> questoes =
                QuestaoParserService.parsear(dados.textoExtraido, dados.materiaId);

        System.out.printf("   Parser retornou %d questões%n", questoes.size());

        if (questoes.isEmpty()) {
            System.out.println("⚠️  Nenhuma questão encontrada pelo parser.");
            System.out.println("   Dica: verifique se o PDF segue o formato: '1) (Banca Ano) Enunciado'");
            // Ainda marca como processado (com 0) para não ficar em loop
            marcarComoProcessado(arquivoId, 0);
            return 0;
        }

        // Log das primeiras questões para diagnóstico
        int logMax = Math.min(3, questoes.size());
        for (int i = 0; i < logMax; i++) {
            QuestaoParserService.QuestaoRaw q = questoes.get(i);
            System.out.printf("   Q%d: banca=%s ano=%s assunto=%s alts=%d correta=%s%n",
                i + 1, q.banca, q.ano, q.assunto,
                q.alternativas.size(), q.alternativaCorreta);
            System.out.printf("        enunciado: %s%n",
                q.enunciado != null && q.enunciado.length() > 80
                    ? q.enunciado.substring(0, 80) + "…" : q.enunciado);
        }

        // ── Passo 4: salvar no banco ─────────────────────────────────────
        System.out.println("💾 Salvando no banco…");
        int salvas = QuestaoParserService.salvarNoBanco(questoes, arquivoId);
        marcarComoProcessado(arquivoId, salvas);

        System.out.printf("✅ %d questões salvas para arquivo %d (%s)%n",
            salvas, arquivoId, dados.nomeOriginal);
        System.out.println("══════════════════════════════════════════\n");
        return salvas;
    }

    /**
     * Processa todos os arquivos pendentes (questoes_extraidas IS NULL ou = 0).
     */
    public static int processarPendentes() throws SQLException {
        garantirColunas();
        String sql = """
            SELECT id FROM arquivo
            WHERE (questoes_extraidas IS NULL OR questoes_extraidas = 0)
            ORDER BY id
        """;
        int total = 0;
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                try { total += processarArquivo(id); }
                catch (Exception e) {
                    System.err.printf("❌ Erro no arquivo %d: %s%n", id, e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        System.out.printf("%n🎉 Pipeline concluído: %d questões no total%n", total);
        return total;
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVADOS
    // ══════════════════════════════════════════════════════════════════════

    private static DadosArquivo buscarDados(int arquivoId) throws SQLException {
        String sql = "SELECT materia_id, nome_original, texto_extraido, caminho FROM arquivo WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, arquivoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                DadosArquivo d = new DadosArquivo();
                d.materiaId     = (Integer) rs.getObject("materia_id");
                d.nomeOriginal  = rs.getString("nome_original");
                d.textoExtraido = rs.getString("texto_extraido");
                d.caminho       = rs.getString("caminho");
                return d;
            }
        }
    }

    private static void atualizarTextoExtraido(int arquivoId, String texto) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "UPDATE arquivo SET texto_extraido = ? WHERE id = ?")) {
            ps.setString(1, texto);
            ps.setInt(2, arquivoId);
            ps.executeUpdate();
        }
    }

    private static void marcarComoProcessado(int arquivoId, int qtd) throws SQLException {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "UPDATE arquivo SET questoes_extraidas = ? WHERE id = ?")) {
            ps.setInt(1, qtd);
            ps.setInt(2, arquivoId);
            ps.executeUpdate();
        }
    }

    /**
     * Garante que as colunas extras existem na tabela arquivo.
     * Usa try/catch por coluna — se já existir, ignora o erro.
     */
    private static void garantirColunas() {
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            try { st.execute("ALTER TABLE arquivo ADD COLUMN texto_extraido TEXT"); }
            catch (SQLException ignored) {}
            try { st.execute("ALTER TABLE arquivo ADD COLUMN questoes_extraidas INTEGER DEFAULT NULL"); }
            catch (SQLException ignored) {}
        } catch (SQLException e) {
            System.err.println("⚠️  Erro ao garantir colunas: " + e.getMessage());
        }
    }

    private static class DadosArquivo {
        Integer materiaId;
        String  nomeOriginal;
        String  textoExtraido;
        String  caminho;
    }
}
