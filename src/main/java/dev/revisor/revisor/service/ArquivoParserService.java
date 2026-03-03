package dev.revisor.revisor.service;

import dev.revisor.revisor.db.DatabaseManager;

import java.io.File;
import java.sql.*;
import java.util.List;

/**
 * Orquestra o pipeline completo:
 *   arquivo.texto_extraido  →  QuestaoParserService  →  tabelas questao + alternativa
 *
 * <p>Exemplo de uso no controller:
 * <pre>
 *   // Ao importar um PDF:
 *   ArquivoParserService.processarArquivo(arquivo.getId());
 *
 *   // Para reprocessar todos os arquivos pendentes:
 *   int total = ArquivoParserService.processarPendentes();
 * </pre>
 */
public class ArquivoParserService {

    // ══════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Processa um único arquivo pelo seu ID.
     * Se o texto ainda não foi extraído, extrai do PDF no disco e grava no banco.
     * Em seguida parseia o texto e persiste as questões.
     *
     * @param arquivoId ID da linha na tabela `arquivo`
     * @return número de questões criadas
     */
    public static int processarArquivo(int arquivoId) throws Exception {
        DadosArquivo dados = buscarDados(arquivoId);
        if (dados == null) {
            System.out.printf("⚠️  Arquivo %d não encontrado%n", arquivoId);
            return 0;
        }

        // Se ainda não tem texto extraído, extrair do PDF e salvar no banco
        if (dados.textoExtraido == null || dados.textoExtraido.isBlank()) {
            if (dados.caminho == null || dados.caminho.isBlank()) {
                System.out.printf("⚠️  Arquivo %d sem caminho — ignorado%n", arquivoId);
                return 0;
            }
            File file = new File(dados.caminho);
            if (!file.exists()) {
                System.out.printf("⚠️  Arquivo não encontrado no disco: %s%n", dados.caminho);
                return 0;
            }
            System.out.printf("📄 Extraindo texto do PDF: %s%n", dados.nomeOriginal);
            String texto = PdfExtractorService.extractText(file);
            if (texto == null || texto.isBlank()) {
                System.out.printf("⚠️  Nenhum texto extraído de %s%n", dados.nomeOriginal);
                return 0;
            }
            atualizarTextoExtraido(arquivoId, texto);
            dados.textoExtraido = texto;
        }

        System.out.printf("🔍 Parseando arquivo %d (%s)…%n", arquivoId, dados.nomeOriginal);

        List<QuestaoParserService.QuestaoRaw> questoes =
                QuestaoParserService.parsear(dados.textoExtraido, dados.materiaId);

        System.out.printf("   ↳ %d questões identificadas%n", questoes.size());

        int salvas = QuestaoParserService.salvarNoBanco(questoes);
        marcarComoProcessado(arquivoId, salvas);

        System.out.printf("   ✅ %d questões salvas no banco%n", salvas);
        return salvas;
    }

    /**
     * Varre todos os arquivos ainda não processados (questoes_extraidas IS NULL)
     * e executa o parser para cada um.
     *
     * @return total de questões inseridas
     */
    public static int processarPendentes() throws SQLException {
        String sql = """
            SELECT id FROM arquivo
            WHERE (questoes_extraidas IS NULL OR questoes_extraidas = 0)
            ORDER BY id
        """;

        garantirColunaProcessado();

        int totalSalvo = 0;
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                int arquivoId = rs.getInt("id");
                try {
                    totalSalvo += processarArquivo(arquivoId);
                } catch (Exception e) {
                    System.err.printf("❌ Erro ao processar arquivo %d: %s%n", arquivoId, e.getMessage());
                }
            }
        }

        System.out.printf("%n🎉 Pipeline concluído: %d questões no total%n", totalSalvo);
        return totalSalvo;
    }

    /**
     * Versão conveniente: recebe o texto já extraído diretamente (sem precisar
     * que esteja no banco) — útil para prévia antes de salvar o arquivo.
     *
     * @param texto     texto bruto do PDF
     * @param materiaId matéria alvo (pode ser null)
     * @return lista de questões parseadas (não salvas ainda)
     */
    public static List<QuestaoParserService.QuestaoRaw> previsualizar(String texto, Integer materiaId) {
        return QuestaoParserService.parsear(texto, materiaId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUXILIARES PRIVADOS
    // ══════════════════════════════════════════════════════════════════════

    private static DadosArquivo buscarDados(int arquivoId) throws SQLException {
        garantirColunaTextoExtraido();
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
        String sql = "UPDATE arquivo SET texto_extraido = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, texto);
            ps.setInt(2, arquivoId);
            ps.executeUpdate();
        }
    }

    private static void garantirColunaTextoExtraido() throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            st.execute("ALTER TABLE arquivo ADD COLUMN texto_extraido TEXT");
        } catch (SQLException ignored) {
            // Coluna já existe
        }
    }

    private static void marcarComoProcessado(int arquivoId, int questoesSalvas) throws SQLException {
        garantirColunaProcessado();
        String sql = "UPDATE arquivo SET questoes_extraidas = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, questoesSalvas);
            ps.setInt(2, arquivoId);
            ps.executeUpdate();
        }
    }

    private static void garantirColunaProcessado() throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            st.execute("ALTER TABLE arquivo ADD COLUMN questoes_extraidas INTEGER DEFAULT NULL");
        } catch (SQLException ignored) {
            // Coluna já existe
        }
    }

    // ── DTO interno ────────────────────────────────────────────────────────
    private static class DadosArquivo {
        Integer materiaId;
        String  nomeOriginal;
        String  textoExtraido;
        String  caminho;
    }
}
