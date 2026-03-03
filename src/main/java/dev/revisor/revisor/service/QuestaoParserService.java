package dev.revisor.revisor.service;

import dev.revisor.revisor.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser inteligente de texto extraído de PDFs de provas/questões.
 *
 * <p>Detecta automaticamente o formato da banca (CESPE/CEBRASPE, FGV, CESGRANRIO,
 * VUNESP, FCC, AOCP, IBFC, NC-UFPR etc.) e extrai:
 * <ul>
 *   <li>Enunciado da questão</li>
 *   <li>Alternativas A–E (quando presentes)</li>
 *   <li>Alternativa correta (quando há gabarito no texto)</li>
 *   <li>Banca, Ano e Assunto (inferidos do cabeçalho ou bloco da questão)</li>
 * </ul>
 *
 * <p>Uso:
 * <pre>
 *   List&lt;QuestaoParserService.QuestaoRaw&gt; questoes =
 *       QuestaoParserService.parsear(textoExtraido, materiaId);
 *   QuestaoParserService.salvarNoBanco(questoes);
 * </pre>
 */
public class QuestaoParserService {

    // ── PADRÕES DE BANCAS ──────────────────────────────────────────────────

    private static final Pattern P_BANCA = Pattern.compile(
            "(?i)\\b(CESPE|CEBRASPE|FGV|CESGRANRIO|VUNESP|FCC|AOCP|IBFC|ESAF|" +
            "IADES|QUADRIX|IDECAN|NC[\\-\\s]UFPR|COPS|FAUEL|FUNIVERSA|UNIFESP|" +
            "FEPESE|OBAFEMI|INEP|ANPAD|FUNDATEC|AVANÇA[\\s]SP|CS[\\-\\s]UFG)\\b"
    );

    private static final Pattern P_ANO = Pattern.compile(
            "\\b(19[89]\\d|20[012]\\d)\\b"
    );

    // ── PADRÃO: INÍCIO DE QUESTÃO ──────────────────────────────────────────
    // Reconhece: "1.", "1 -", "Questão 1", "QUESTÃO 01", "Q.1", "01)"
    private static final Pattern P_INICIO_QUESTAO = Pattern.compile(
            "(?m)^\\s*(?:(?:questão|questao|q\\.)\\s*)?(?:0*)(\\d{1,3})\\s*[.)\\-–]\\s+",
            Pattern.CASE_INSENSITIVE
    );

    // ── PADRÃO: ALTERNATIVAS ───────────────────────────────────────────────
    // Reconhece: "a)", "A)", "a.", "A.", "(a)", "(A)", "a -"
    private static final Pattern P_ALTERNATIVA = Pattern.compile(
            "(?m)^\\s*[\\(]?([A-Ea-e])[\\).]\\s+(.+?)(?=\\n\\s*[\\(]?[A-Ea-e][\\).]\\s|\\z)",
            Pattern.DOTALL
    );

    // ── PADRÃO: GABARITO ───────────────────────────────────────────────────
    // Reconhece blocos como "Gabarito: A", "GAB.: B", "Resposta: C",
    // "01 - C  02 - A  03 - B" (gabarito tabular), além de "CERTO/ERRADO"
    private static final Pattern P_GABARITO_INLINE = Pattern.compile(
            "(?i)(?:gabarito|gab\\.?|resposta|answer)[:\\s]+([A-Ea-e]|certo|errado|c|e)"
    );

    private static final Pattern P_GABARITO_TABULAR = Pattern.compile(
            "(?i)(?:(?:0*)([0-9]{1,3})\\s*[-–.]\\s*([A-Ea-e]))(?:\\s|$)"
    );

    // ── PADRÃO: ASSUNTO/SEÇÃO ──────────────────────────────────────────────
    // Títulos em maiúsculo ou precedidos por "ASSUNTO:", "TEMA:", "ÁREA:"
    private static final Pattern P_ASSUNTO_CABECALHO = Pattern.compile(
            "(?im)^\\s*(?:assunto|tema|área|area|disciplina|matéria|materia)[:\\s]+(.+)$"
    );

    // ── PADRÃO: CESPE (Certo/Errado) ──────────────────────────────────────
    private static final Pattern P_CESPE_CE = Pattern.compile(
            "(?i)\\b(certo|errado)\\b"
    );

    // ══════════════════════════════════════════════════════════════════════
    // MODELO DE DADOS INTERMEDIÁRIO
    // ══════════════════════════════════════════════════════════════════════

    public static class QuestaoRaw {
        public Integer materiaId;
        public String assunto;
        public String enunciado;
        public String banca;
        public Integer ano;
        public String tipo;                          // "multipla_escolha" | "certo_errado"
        public List<Alternativa> alternativas = new ArrayList<>();
        public Integer alternativaCorreta;           // índice 0-based (0=A,1=B...)

        @Override
        public String toString() {
            return String.format("[%s %s] %s... (%d alts)", banca, ano,
                    enunciado != null && enunciado.length() > 60
                            ? enunciado.substring(0, 60) : enunciado,
                    alternativas.size());
        }
    }

    public static class Alternativa {
        public String letra;
        public String texto;

        public Alternativa(String letra, String texto) {
            this.letra = letra.toUpperCase();
            this.texto = texto.trim();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Parseia o texto bruto extraído de um PDF e retorna questões estruturadas.
     *
     * @param textoExtraido texto bruto retornado pelo PdfExtractorService
     * @param materiaId     ID da matéria no banco (pode ser null)
     * @return lista de questões identificadas
     */
    public static List<QuestaoRaw> parsear(String textoExtraido, Integer materiaId) {
        if (textoExtraido == null || textoExtraido.isBlank()) return List.of();

        String texto = normalizarTexto(textoExtraido);

        // 1. Detectar metadados globais do documento
        String bancaGlobal = detectarBanca(texto);
        Integer anoGlobal  = detectarAno(texto);
        String assuntoGlobal = detectarAssuntoGlobal(texto);

        // 2. Montar mapa de gabarito tabular (nº questão → letra)
        int[][] gabaritoTabular = extrairGabaritoTabular(texto);

        // 3. Segmentar em blocos de questão
        List<String> blocos = segmentarBlocos(texto);

        List<QuestaoRaw> resultado = new ArrayList<>();
        int numSeq = 1;

        for (String bloco : blocos) {
            QuestaoRaw q = parsearBloco(bloco, numSeq);
            if (q == null || q.enunciado == null || q.enunciado.isBlank()) {
                numSeq++;
                continue;
            }

            q.materiaId = materiaId;

            // Preencher com globais se ausente
            if (q.banca == null)  q.banca  = bancaGlobal;
            if (q.ano   == null)  q.ano    = anoGlobal;
            if (q.assunto == null) q.assunto = assuntoGlobal;

            // Gabarito tabular
            if (q.alternativaCorreta == null && gabaritoTabular != null) {
                for (int[] par : gabaritoTabular) {
                    if (par[0] == numSeq) {
                        q.alternativaCorreta = par[1]; // 0=A,1=B...
                        break;
                    }
                }
            }

            resultado.add(q);
            numSeq++;
        }

        return resultado;
    }

    /**
     * Persiste as questões parseadas no banco SQLite do projeto.
     * Insere nas tabelas `questao` e `alternativa`.
     *
     * @param questoes lista retornada por {@link #parsear}
     * @return número de questões inseridas com sucesso
     */
    public static int salvarNoBanco(List<QuestaoRaw> questoes) throws SQLException {
        if (questoes == null || questoes.isEmpty()) return 0;

        garantirEstruturaBanco();

        Connection conn = DatabaseManager.getConnection();
        conn.setAutoCommit(false);

        String sqlQuestao = """
            INSERT INTO questao (materia_id, assunto, enunciado, banca, ano, tipo, alternativa_correta)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        String sqlAlternativa = """
            INSERT INTO alternativa (questao_id, letra, texto, correta)
            VALUES (?, ?, ?, ?)
        """;

        int count = 0;
        try (PreparedStatement psQ = conn.prepareStatement(sqlQuestao, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psA = conn.prepareStatement(sqlAlternativa)) {

            for (QuestaoRaw q : questoes) {
                // ── Inserir questão ──
                setOrNull(psQ, 1, q.materiaId, Types.INTEGER);
                psQ.setString(2, q.assunto);
                psQ.setString(3, limpar(q.enunciado));
                psQ.setString(4, q.banca);
                setOrNull(psQ, 5, q.ano, Types.INTEGER);
                psQ.setString(6, q.tipo);
                setOrNull(psQ, 7, q.alternativaCorreta, Types.INTEGER);
                psQ.executeUpdate();

                int questaoId;
                try (ResultSet keys = psQ.getGeneratedKeys()) {
                    if (!keys.next()) continue;
                    questaoId = keys.getInt(1);
                }

                // ── Inserir alternativas ──
                for (int i = 0; i < q.alternativas.size(); i++) {
                    Alternativa alt = q.alternativas.get(i);
                    psA.setInt(1, questaoId);
                    psA.setString(2, alt.letra);
                    psA.setString(3, limpar(alt.texto));
                    psA.setInt(4, q.alternativaCorreta != null && q.alternativaCorreta == i ? 1 : 0);
                    psA.addBatch();
                }
                psA.executeBatch();
                count++;
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }

        return count;
    }

    // ══════════════════════════════════════════════════════════════════════
    // LÓGICA INTERNA: SEGMENTAÇÃO
    // ══════════════════════════════════════════════════════════════════════

    private static List<String> segmentarBlocos(String texto) {
        List<String> blocos = new ArrayList<>();
        Matcher m = P_INICIO_QUESTAO.matcher(texto);

        int ultimoInicio = -1;
        while (m.find()) {
            if (ultimoInicio >= 0) {
                blocos.add(texto.substring(ultimoInicio, m.start()).trim());
            }
            ultimoInicio = m.start();
        }
        if (ultimoInicio >= 0) {
            blocos.add(texto.substring(ultimoInicio).trim());
        }

        // Fallback: se não encontrou padrão de numeração, trata o texto inteiro como um bloco
        if (blocos.isEmpty() && !texto.isBlank()) {
            blocos.add(texto.trim());
        }

        return blocos;
    }

    // ══════════════════════════════════════════════════════════════════════
    // LÓGICA INTERNA: PARSE DE BLOCO INDIVIDUAL
    // ══════════════════════════════════════════════════════════════════════

    private static QuestaoRaw parsearBloco(String bloco, int numSeq) {
        if (bloco == null || bloco.isBlank()) return null;

        QuestaoRaw q = new QuestaoRaw();

        // ── Banca e Ano do bloco ──
        q.banca = detectarBanca(bloco);
        q.ano   = detectarAno(bloco);

        // ── Assunto do bloco (linha de cabeçalho antes das alternativas) ──
        q.assunto = detectarAssuntoBloco(bloco);

        // ── Separar enunciado das alternativas ──
        Matcher mAlt = P_ALTERNATIVA.matcher(bloco);
        int fimEnunciado = bloco.length();
        List<int[]> altPositions = new ArrayList<>();

        while (mAlt.find()) {
            if (altPositions.isEmpty()) fimEnunciado = mAlt.start();
            altPositions.add(new int[]{mAlt.start(), mAlt.end(), mAlt.group(1).charAt(0) - 'A'});
            q.alternativas.add(new Alternativa(mAlt.group(1), mAlt.group(2).trim()));
        }

        // ── Enunciado ──
        String enunciado = bloco.substring(0, fimEnunciado).trim();
        // Remove cabeçalho de número da questão no início
        enunciado = enunciado.replaceFirst("^\\s*(?:questão|questao|q\\.)?\\s*0*\\d{1,3}\\s*[.)\\-–]\\s*", "");
        q.enunciado = enunciado.trim();

        // ── Tipo de questão ──
        if (!q.alternativas.isEmpty()) {
            q.tipo = "multipla_escolha";
        } else if (P_CESPE_CE.matcher(bloco).find()) {
            q.tipo = "certo_errado";
        } else {
            q.tipo = "dissertativa";
        }

        // ── Gabarito inline ──
        Matcher mGab = P_GABARITO_INLINE.matcher(bloco);
        if (mGab.find()) {
            String resposta = mGab.group(1).toUpperCase();
            if (resposta.equals("CERTO") || resposta.equals("C")) {
                q.alternativaCorreta = 0; // convenção: 0=CERTO
            } else if (resposta.equals("ERRADO") || resposta.equals("E")) {
                q.alternativaCorreta = 1; // convenção: 1=ERRADO
            } else {
                q.alternativaCorreta = resposta.charAt(0) - 'A';
            }
        }

        return q;
    }

    // ══════════════════════════════════════════════════════════════════════
    // DETECÇÃO DE METADADOS
    // ══════════════════════════════════════════════════════════════════════

    private static String detectarBanca(String texto) {
        Matcher m = P_BANCA.matcher(texto);
        return m.find() ? m.group(1).toUpperCase().replace(" ", "-") : null;
    }

    private static Integer detectarAno(String texto) {
        Matcher m = P_ANO.matcher(texto);
        // Pega o primeiro ano encontrado, mas dá preferência a anos que aparecem
        // próximos a palavras-chave de concurso
        Integer anoGeral = null;
        while (m.find()) {
            int ano = Integer.parseInt(m.group(1));
            if (ano >= 1990 && ano <= 2030) {
                anoGeral = ano;
                // Tenta priorizar anos próximos à palavra "ano" ou banca
                String vizinho = texto.substring(Math.max(0, m.start() - 30), m.start());
                if (vizinho.matches("(?i).*\\b(ano|year|edição|concurso|prova).*")) {
                    return ano;
                }
            }
        }
        return anoGeral;
    }

    private static String detectarAssuntoGlobal(String texto) {
        Matcher m = P_ASSUNTO_CABECALHO.matcher(texto);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String detectarAssuntoBloco(String bloco) {
        // Tenta encontrar linha de assunto dentro do bloco
        Matcher m = P_ASSUNTO_CABECALHO.matcher(bloco);
        if (m.find()) return m.group(1).trim();

        // Tenta linha em MAIÚSCULO antes do enunciado (ex: "DIREITO ADMINISTRATIVO")
        String[] linhas = bloco.split("\\n");
        for (String linha : linhas) {
            String l = linha.trim();
            if (l.length() > 4 && l.length() < 80
                    && l.equals(l.toUpperCase())
                    && l.matches("[A-ZÁÀÂÃÉÊÍÓÔÕÚÇ ]+")) {
                return capitalize(l);
            }
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // GABARITO TABULAR (ex: "01-C  02-A  03-B")
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Retorna array de [numero_questao, indice_alternativa(0=A)] ou null se não encontrado.
     */
    private static int[][] extrairGabaritoTabular(String texto) {
        // Localiza bloco de gabarito
        int idxGab = texto.toLowerCase().indexOf("gabarito");
        if (idxGab < 0) return null;

        String blocoGab = texto.substring(idxGab, Math.min(idxGab + 2000, texto.length()));
        Matcher m = P_GABARITO_TABULAR.matcher(blocoGab);

        List<int[]> pares = new ArrayList<>();
        while (m.find()) {
            int num  = Integer.parseInt(m.group(1));
            int letra = m.group(2).toUpperCase().charAt(0) - 'A';
            pares.add(new int[]{num, letra});
        }

        return pares.isEmpty() ? null : pares.toArray(new int[0][]);
    }

    // ══════════════════════════════════════════════════════════════════════
    // BANCO: garantir colunas que o parser precisa
    // ══════════════════════════════════════════════════════════════════════

    private static void garantirEstruturaBanco() throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        try (Statement st = conn.createStatement()) {

            // Tabela questao — colunas extras usadas pelo parser
            for (String col : new String[]{
                    "ALTER TABLE questao ADD COLUMN assunto TEXT",
                    "ALTER TABLE questao ADD COLUMN tipo TEXT",
                    "ALTER TABLE questao ADD COLUMN alternativa_correta INTEGER"
            }) {
                try { st.execute(col); } catch (SQLException ignored) {}
            }

            // Tabela alternativa (pode não existir em bancos antigos)
            st.execute("""
                CREATE TABLE IF NOT EXISTS alternativa (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    questao_id INTEGER NOT NULL REFERENCES questao(id) ON DELETE CASCADE,
                    letra      TEXT NOT NULL,
                    texto      TEXT NOT NULL,
                    correta    INTEGER NOT NULL DEFAULT 0
                )
            """);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ══════════════════════════════════════════════════════════════════════

    /** Normaliza espaços, quebras de linha e caracteres especiais do PDF. */
    private static String normalizarTexto(String texto) {
        return texto
                .replace("\r\n", "\n")
                .replace("\r",   "\n")
                // Remove hifenização de fim de linha comum em PDFs
                .replaceAll("-\\n([a-záàâãéêíóôõúç])", "$1")
                // Colapsa mais de 2 linhas em branco
                .replaceAll("\\n{3,}", "\n\n")
                // Remove caracteres de controle exceto \n e \t
                .replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", " ")
                .trim();
    }

    /** Remove espaços extras e quebras desnecessárias dentro de um texto. */
    private static String limpar(String texto) {
        if (texto == null) return null;
        return texto
                .replaceAll("\\s{2,}", " ")
                .replaceAll("\\n", " ")
                .trim();
    }

    private static String capitalize(String texto) {
        if (texto == null || texto.isBlank()) return texto;
        String[] palavras = texto.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        String[] stopWords = {"de", "do", "da", "dos", "das", "e", "ou", "a", "o", "em", "no", "na"};
        for (int i = 0; i < palavras.length; i++) {
            String p = palavras[i];
            boolean stop = false;
            if (i > 0) {
                for (String sw : stopWords) if (p.equals(sw)) { stop = true; break; }
            }
            sb.append(stop ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1));
            if (i < palavras.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    private static void setOrNull(PreparedStatement ps, int idx, Object val, int sqlType) throws SQLException {
        if (val == null) ps.setNull(idx, sqlType);
        else if (val instanceof Integer) ps.setInt(idx, (Integer) val);
        else ps.setObject(idx, val);
    }
}
