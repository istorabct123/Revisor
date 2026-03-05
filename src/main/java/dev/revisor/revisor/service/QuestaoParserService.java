package dev.revisor.revisor.service;

import dev.revisor.revisor.db.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parser de apostilas militares (EsSA, EsPCEx, CN…).
 *
 * Estratégia:
 *  1. Extrair nomes de seção do SUMÁRIO (se houver) → lista de âncoras confiáveis
 *  2. Para cada âncora: localizar o bloco no corpo do texto até o próximo "Gabarito"
 *  3. Parsear questões + cruzar com o gabarito do próprio bloco
 *  4. Fallback para heurística se não houver sumário
 */
public class QuestaoParserService {

    // ── Padrões ──────────────────────────────────────────────────────────────
    private static final Pattern P_BANCA = Pattern.compile(
        "(?i)\\b(CESPE|CEBRASPE|FGV|CESGRANRIO|EsSA|EsPCEx|Col[eé]gio\\s+Naval|" +
        "FCC|AOCP|IBFC|ESAF|IADES|QUADRIX|IDECAN|VUNESP|UFPE|UFRGS|UFC|UECE|UEPA|" +
        "UNCISAL|TJ[\\-\\s]SC|Albert\\s+Einstein|ENEM|CPCON|EsFCEx|" +
        "PAS[\\-\\s]DF|IDHTEC|CEV[\\-\\s]URCA|AEVSF|FACAPE|" +
        "Crescer\\s+Consultorias|Comando\\s+do)\\b"
    );
    private static final Pattern P_ANO         = Pattern.compile("\\b(19[89]\\d|20[012]\\d)\\b");
    private static final Pattern P_INICIO_Q    = Pattern.compile("(?m)^[ \\t]*(\\d{1,3})[.)][ \\t]+");
    private static final Pattern P_ALTERNATIVA = Pattern.compile(
        "(?m)^[ \\t]*[\\(]?([A-Ea-e])[\\).][ \\t]+(.+?)(?=\\n[ \\t]*[\\(]?[A-Ea-e][\\).][ \\t]|\\n[ \\t]*(?:gabarito|gab\\.|resposta)|\\z)",
        Pattern.DOTALL
    );
    private static final Pattern P_GAB_TITULO  = Pattern.compile(
        "(?im)^[ \\t]*gabaritos?[ \\t]*$"
    );
    private static final Pattern P_GAB_LINHA   = Pattern.compile(
        "(?m)^[ \\t]*(\\d{1,3})[.)][ \\t]+([A-Ea-e])[ \\t]*$"
    );
    private static final Pattern P_GAB_INLINE  = Pattern.compile(
        "(?i)(?:gabarito|gab\\.?|resposta)[:\\s]+([A-Ea-e]|certo|errado)"
    );
    private static final Pattern P_CAB_QUESTAO = Pattern.compile(
        "\\(([^)]{2,40})\\s+(\\d{4})\\)"
    );
    // Linha do sumário: "Nome da Seção ---- 99"
    private static final Pattern P_LINHA_SUMARIO = Pattern.compile(
        "^[•➢\\s]*(.+?)\\s*[-–—\\s]{3,}\\s*\\d+\\s*$"
    );
    private static final Pattern P_IGNORAR_SUMARIO = Pattern.compile(
        "(?i)^(conteúdo program|relação de|top \\d|gabaritos?|sumário)"
    );

    // ══════════════════════════════════════════════════════════════════════════
    // MODELOS
    // ══════════════════════════════════════════════════════════════════════════

    public static class QuestaoRaw {
        public Integer materiaId;
        public String  assunto, enunciado, banca, tipo;
        public Integer ano, alternativaCorreta;
        public List<Alternativa> alternativas = new ArrayList<>();
        @Override public String toString() {
            return String.format("[%s %s | %s] %s", banca, ano, assunto,
                enunciado != null && enunciado.length() > 60 ? enunciado.substring(0, 60) + "…" : enunciado);
        }
    }

    public static class Alternativa {
        public String letra, texto;
        public Alternativa(String l, String t) { this.letra = l.toUpperCase(); this.texto = t.trim(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ══════════════════════════════════════════════════════════════════════════

    public static List<QuestaoRaw> parsear(String textoExtraido, Integer materiaId) {
        if (textoExtraido == null || textoExtraido.isBlank()) return List.of();

        String texto = normalizarTexto(textoExtraido);

        // 1. Tentar extrair seções do SUMÁRIO
        List<String> nomesSumario = extrairNomesSumario(texto);

        List<QuestaoRaw> resultado;
        if (!nomesSumario.isEmpty()) {
            System.out.printf("   Modo SUMÁRIO: %d seções encontradas%n", nomesSumario.size());
            resultado = parsearComSumario(texto, nomesSumario, materiaId);
        } else {
            System.out.println("   Modo HEURÍSTICO (sem sumário)");
            resultado = parsearHeuristico(texto, materiaId);
        }

        System.out.printf("   ✅ Total: %d questões%n", resultado.size());
        return resultado;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODO 1: COM SUMÁRIO
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Extrai nomes de seção do bloco "Sumário" do PDF.
     * Formato esperado: "Nome da Seção ---- 99"
     */
    private static List<String> extrairNomesSumario(String texto) {
        // Localizar bloco do sumário (do início até a primeira questão "1) ")
        int fimSumario = encontrarFimSumario(texto);
        if (fimSumario <= 0) return List.of();

        String blocoSumario = texto.substring(0, fimSumario);
        List<String> nomes = new ArrayList<>();

        for (String linha : blocoSumario.split("\\n")) {
            linha = linha.trim();
            Matcher m = P_LINHA_SUMARIO.matcher(linha);
            if (!m.matches()) continue;
            String nome = m.group(1).strip().replaceAll("^[•➢\\s]+", "");
            if (nome.isBlank() || P_IGNORAR_SUMARIO.matcher(nome).find()) continue;
            nomes.add(nome);
        }
        return nomes;
    }

    /** Encontra onde o sumário termina (onde a primeira questão "1) " aparece). */
    private static int encontrarFimSumario(String texto) {
        // Procurar o índice do sumário primeiro
        int idxSumario = texto.toLowerCase().indexOf("sumário");
        if (idxSumario < 0) idxSumario = texto.toLowerCase().indexOf("sumario");
        if (idxSumario < 0) return -1;

        // Primeira questão após o sumário
        Matcher m = P_INICIO_Q.matcher(texto);
        while (m.find()) {
            if (m.start() > idxSumario) return m.start();
        }
        return -1;
    }

    /**
     * Parseia o texto usando os nomes do sumário como âncoras.
     * Para cada nome: localiza no corpo, extrai bloco até o próximo Gabarito,
     * parseia questões e cruza com gabarito do próprio bloco.
     */
    private static List<QuestaoRaw> parsearComSumario(String texto, List<String> nomes, Integer materiaId) {
        // Remover o bloco do sumário do texto de trabalho
        int fimSumario = encontrarFimSumario(texto);
        String corpo = fimSumario > 0 ? texto.substring(fimSumario) : texto;

        String bancaGlobal = detectarBanca(corpo);
        List<QuestaoRaw> resultado = new ArrayList<>();

        // Localizar cada nome no corpo
        List<int[]> posicoes = new ArrayList<>(); // [posInicio, posNomeFim, idxNome]
        for (int i = 0; i < nomes.size(); i++) {
            String nome = nomes.get(i);
            // Buscar pelo prefixo (ignorar sufixos de data como "(1500-1822)")
            String prefixo = nome.split("\\(")[0].strip();
            if (prefixo.length() < 4) prefixo = nome;
            try {
                Pattern p = Pattern.compile("(?m)^[ \\t]*" + Pattern.quote(prefixo) + ".*$");
                Matcher m = p.matcher(corpo);
                if (m.find()) posicoes.add(new int[]{m.start(), m.end(), i});
            } catch (PatternSyntaxException ignored) {}
        }
        posicoes.sort(Comparator.comparingInt(a -> a[0]));

        System.out.printf("   Seções localizadas no corpo: %d de %d%n", posicoes.size(), nomes.size());

        // Processar cada segmento
        for (int p = 0; p < posicoes.size(); p++) {
            int iniSec   = posicoes.get(p)[0];
            int fimSec   = p + 1 < posicoes.size() ? posicoes.get(p + 1)[0] : corpo.length();
            int idxNome  = posicoes.get(p)[2];
            String nomeSecao = nomes.get(idxNome);
            String bloco = corpo.substring(iniSec, fimSec);

            // Separar questões do gabarito (pegar o último "Gabarito" no bloco)
            Matcher gm = P_GAB_TITULO.matcher(bloco);
            int inicioGab = -1;
            while (gm.find()) inicioGab = gm.start(); // pega o último

            String textoQuestoes = inicioGab >= 0 ? bloco.substring(0, inicioGab) : bloco;
            Map<Integer, Character> gabMapa = new LinkedHashMap<>();
            if (inicioGab >= 0) {
                Matcher gl = P_GAB_LINHA.matcher(bloco.substring(inicioGab));
                while (gl.find())
                    gabMapa.put(Integer.parseInt(gl.group(1)), Character.toUpperCase(gl.group(2).charAt(0)));
            }

            // Parsear blocos de questão dentro deste segmento
            List<int[]> blocos = posicionarBlocos(textoQuestoes);
            int extraidas = 0;
            for (int b = 0; b < blocos.size(); b++) {
                int ini    = blocos.get(b)[0];
                int fim    = b + 1 < blocos.size() ? blocos.get(b + 1)[0] : textoQuestoes.length();
                int numQ   = blocos.get(b)[1];
                String bq  = textoQuestoes.substring(ini, fim).trim();

                QuestaoRaw q = parsearBloco(bq);
                if (q == null || q.enunciado == null || q.enunciado.isBlank()) continue;

                q.materiaId = materiaId;
                q.assunto   = nomeSecao;
                if (q.banca == null) q.banca = bancaGlobal;

                // Cruzar gabarito
                if (q.alternativaCorreta == null && gabMapa.containsKey(numQ)) {
                    char lf = gabMapa.get(numQ);
                    q.alternativaCorreta = lf - 'A';
                    for (int i = 0; i < q.alternativas.size(); i++)
                        if (q.alternativas.get(i).letra.equalsIgnoreCase(String.valueOf(lf))) {
                            q.alternativaCorreta = i; break;
                        }
                }
                resultado.add(q);
                extraidas++;
            }
            System.out.printf("   [%s] %d questões, gabarito %d respostas%n",
                nomeSecao.length() > 40 ? nomeSecao.substring(0, 40) : nomeSecao,
                extraidas, gabMapa.size());
        }
        return resultado;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODO 2: HEURÍSTICO (fallback sem sumário)
    // ══════════════════════════════════════════════════════════════════════════

    private static List<QuestaoRaw> parsearHeuristico(String texto, Integer materiaId) {
        // Extrair gabarito tabular global
        int idxGab = -1;
        for (String m : new String[]{"gabarito", "gabrito", "respostas"}) {
            int idx = texto.toLowerCase().lastIndexOf(m);
            if (idx > idxGab) idxGab = idx;
        }
        Map<Integer, Character> gabMapa = new LinkedHashMap<>();
        String textoLimpo = texto;
        if (idxGab >= 0) {
            Matcher gl = P_GAB_LINHA.matcher(texto.substring(idxGab));
            while (gl.find()) gabMapa.putIfAbsent(Integer.parseInt(gl.group(1)), Character.toUpperCase(gl.group(2).charAt(0)));
            textoLimpo = texto.substring(0, idxGab).trim();
        }

        // Seções heurísticas (linha anterior em branco + próxima é questão)
        List<int[]> posSecoes = detectarSecoesHeuristico(textoLimpo);
        List<String> nomesSecoes = new ArrayList<>();
        for (int[] s : posSecoes) nomesSecoes.add(textoLimpo.substring(s[0], s[1]).trim());

        String bancaGlobal = detectarBanca(textoLimpo);
        List<int[]> blocos = posicionarBlocos(textoLimpo);
        List<QuestaoRaw> resultado = new ArrayList<>();

        for (int b = 0; b < blocos.size(); b++) {
            int ini = blocos.get(b)[0], fim = b + 1 < blocos.size() ? blocos.get(b + 1)[0] : textoLimpo.length();
            int numQ = blocos.get(b)[1];
            QuestaoRaw q = parsearBloco(textoLimpo.substring(ini, fim).trim());
            if (q == null || q.enunciado == null || q.enunciado.isBlank()) continue;
            q.materiaId = materiaId;
            if (q.banca == null) q.banca = bancaGlobal;
            q.assunto = secaoAtiva(nomesSecoes, posSecoes, ini);
            if (q.alternativaCorreta == null && gabMapa.containsKey(numQ)) {
                char lf = gabMapa.get(numQ);
                q.alternativaCorreta = lf - 'A';
                for (int i = 0; i < q.alternativas.size(); i++)
                    if (q.alternativas.get(i).letra.equalsIgnoreCase(String.valueOf(lf))) { q.alternativaCorreta = i; break; }
            }
            resultado.add(q);
        }
        return resultado;
    }

    private static List<int[]> detectarSecoesHeuristico(String texto) {
        List<int[]> res = new ArrayList<>();
        String[] linhas = texto.split("\\n");
        int pos = 0;
        for (int i = 0; i < linhas.length; i++) {
            String l = linhas[i].trim(), ant = i > 0 ? linhas[i-1].trim() : "", prx = i < linhas.length-1 ? linhas[i+1].trim() : "";
            if (l.length() >= 5 && l.length() <= 80 && !l.matches("^\\d.*") && !l.matches("^[a-eA-E][.)].+")
                    && !l.matches("(?i)^(página|page|\\d+|gabarito).*") && l.matches(".*[A-ZÁÀÂÃÉÊÍÓÔÕÚÇ].*")
                    && ant.isBlank() && (prx.isBlank() || prx.matches("\\d+[.)].*")))
                res.add(new int[]{pos, pos + linhas[i].length()});
            pos += linhas[i].length() + 1;
        }
        return res;
    }

    private static String secaoAtiva(List<String> nomes, List<int[]> pos, int posBloco) {
        String ultima = null;
        for (int i = 0; i < pos.size(); i++) { if (pos.get(i)[0] <= posBloco) ultima = nomes.get(i); else break; }
        return ultima;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PARSE DE BLOCO
    // ══════════════════════════════════════════════════════════════════════════

    private static List<int[]> posicionarBlocos(String texto) {
        List<int[]> lista = new ArrayList<>();
        Matcher m = P_INICIO_Q.matcher(texto);
        while (m.find()) {
            int num = Integer.parseInt(m.group(1));
            int fim = texto.indexOf('\n', m.end()); if (fim < 0) fim = texto.length();
            String resto = texto.substring(m.end(), fim).trim();
            if (resto.length() == 1 && Character.isLetter(resto.charAt(0))) continue;
            lista.add(new int[]{m.start(), num});
        }
        return lista;
    }

    private static QuestaoRaw parsearBloco(String bloco) {
        if (bloco == null || bloco.isBlank()) return null;
        QuestaoRaw q = new QuestaoRaw();
        Matcher mCab = P_CAB_QUESTAO.matcher(bloco);
        if (mCab.find()) {
            q.banca = normalizarBanca(mCab.group(1).trim());
            try { q.ano = Integer.parseInt(mCab.group(2)); } catch (NumberFormatException ignored) {}
        }
        if (q.banca == null) q.banca = detectarBanca(bloco);
        if (q.ano   == null) q.ano   = detectarAno(bloco);

        Matcher mAlt = P_ALTERNATIVA.matcher(bloco);
        int fimEnunc = bloco.length();
        while (mAlt.find()) {
            if (q.alternativas.isEmpty()) fimEnunc = mAlt.start();
            // Limpar quebras internas do texto da alternativa
            String textoAlt = mAlt.group(2).replaceAll("\\s*\\n\\s*", " ").trim();
            q.alternativas.add(new Alternativa(mAlt.group(1), textoAlt));
        }
        String enunc = bloco.substring(0, fimEnunc).trim();
        enunc = enunc.replaceFirst("^[ \\t]*\\d{1,3}[.)][ \\t]+(?:\\([^)]+\\s+\\d{4}\\))?[ \\t]*", "");
        q.enunciado = enunc.trim();
        q.tipo = !q.alternativas.isEmpty() ? "multipla_escolha"
               : (bloco.toLowerCase().contains("certo") || bloco.toLowerCase().contains("errado")) ? "certo_errado" : "dissertativa";
        Matcher mGab = P_GAB_INLINE.matcher(bloco);
        if (mGab.find()) {
            String r = mGab.group(1).toUpperCase();
            q.alternativaCorreta = r.equals("CERTO") ? 0 : r.equals("ERRADO") ? 1 : r.charAt(0) - 'A';
        }
        return q;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BANCO
    // ══════════════════════════════════════════════════════════════════════════

    public static int salvarNoBanco(List<QuestaoRaw> questoes, Integer arquivoId) throws SQLException {
        if (questoes == null || questoes.isEmpty()) return 0;
        garantirEstruturaBanco();
        Connection conn = DatabaseManager.getConnection();
        conn.setAutoCommit(false);
        String sqlQ = "INSERT INTO questao (materia_id, arquivo_id, assunto, enunciado, banca, ano, tipo, alternativa_correta) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlA = "INSERT INTO alternativa (questao_id, letra, texto, correta) VALUES (?, ?, ?, ?)";
        int count = 0;
        try (PreparedStatement psQ = conn.prepareStatement(sqlQ, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psA = conn.prepareStatement(sqlA)) {
            for (QuestaoRaw q : questoes) {
                setOrNull(psQ, 1, q.materiaId, Types.INTEGER);
                setOrNull(psQ, 2, arquivoId,  Types.INTEGER);
                psQ.setString(3, q.assunto); psQ.setString(4, limpar(q.enunciado));
                psQ.setString(5, q.banca); setOrNull(psQ, 6, q.ano, Types.INTEGER);
                psQ.setString(7, q.tipo); setOrNull(psQ, 8, q.alternativaCorreta, Types.INTEGER);
                psQ.executeUpdate();
                int qId;
                try (ResultSet keys = psQ.getGeneratedKeys()) { if (!keys.next()) continue; qId = keys.getInt(1); }
                for (int i = 0; i < q.alternativas.size(); i++) {
                    Alternativa alt = q.alternativas.get(i);
                    psA.setInt(1, qId); psA.setString(2, alt.letra); psA.setString(3, limpar(alt.texto));
                    psA.setInt(4, (q.alternativaCorreta != null && q.alternativaCorreta == i) ? 1 : 0);
                    psA.addBatch();
                }
                if (!q.alternativas.isEmpty()) psA.executeBatch();
                count++;
            }
            conn.commit();
        } catch (SQLException e) { conn.rollback(); throw e; }
        finally { conn.setAutoCommit(true); }
        return count;
    }

    private static void garantirEstruturaBanco() throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        try (Statement st = conn.createStatement()) {
            for (String col : new String[]{
                    "ALTER TABLE questao ADD COLUMN assunto TEXT",
                    "ALTER TABLE questao ADD COLUMN tipo TEXT",
                    "ALTER TABLE questao ADD COLUMN alternativa_correta INTEGER",
                    "ALTER TABLE questao ADD COLUMN arquivo_id INTEGER REFERENCES arquivo(id)"
            })
                try { st.execute(col); } catch (SQLException ignored) {}
            boolean temLetra = colunaExiste(conn,"alternativa","letra"), temCorreta = colunaExiste(conn,"alternativa","correta");
            if (!temLetra || !temCorreta) {
                st.execute("DROP TABLE IF EXISTS alternativa");
                st.execute("CREATE TABLE alternativa (id INTEGER PRIMARY KEY AUTOINCREMENT, questao_id INTEGER NOT NULL, letra TEXT NOT NULL, texto TEXT NOT NULL, correta INTEGER NOT NULL DEFAULT 0, FOREIGN KEY (questao_id) REFERENCES questao(id) ON DELETE CASCADE)");
                System.out.println("✅ Tabela 'alternativa' recriada.");
            }
        }
    }

    private static boolean colunaExiste(Connection conn, String tabela, String coluna) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery("PRAGMA table_info(" + tabela + ")")) {
            while (rs.next()) if (coluna.equalsIgnoreCase(rs.getString("name"))) return true;
        }
        return false;
    }

    // ── Utilitários ──────────────────────────────────────────────────────────
    private static String detectarBanca(String t) { Matcher m = P_BANCA.matcher(t); return m.find() ? normalizarBanca(m.group(1)) : null; }
    private static String normalizarBanca(String b) { if (b==null) return null; return b.trim().replaceAll("\\s+"," ").replace("ESSA","EsSA").replace("ESPCEX","EsPCEx").replace("ESFCEX","EsFCEx"); }
    private static Integer detectarAno(String t) { Matcher m = P_ANO.matcher(t); while (m.find()) { int a = Integer.parseInt(m.group(1)); if (a>=1990&&a<=2030) return a; } return null; }
    private static String normalizarTexto(String t) { return t.replace("\r\n","\n").replace("\r","\n").replaceAll("-\\n([a-záàâãéêíóôõúç])","$1").replaceAll("\\n{3,}","\n\n").replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]"," ").trim(); }
    private static String limpar(String t) { if (t==null) return null; return t.replaceAll("\\s{2,}"," ").replace("\n"," ").trim(); }
    private static void setOrNull(PreparedStatement ps, int idx, Object val, int sqlType) throws SQLException { if (val==null) ps.setNull(idx,sqlType); else if (val instanceof Integer) ps.setInt(idx,(Integer)val); else ps.setObject(idx,val); }
}
