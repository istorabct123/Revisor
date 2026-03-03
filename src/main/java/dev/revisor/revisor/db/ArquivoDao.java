package dev.revisor.revisor.db;

import dev.revisor.revisor.model.Arquivo;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para a tabela arquivo, que armazena PDFs e outros materiais importados.
 */
public class ArquivoDao {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Arquivo inserir(Integer materiaId,
                           String caminho,
                           String nomeOriginal,
                           long tamanho,
                           String textoExtraido) throws SQLException {

        garantirTabela();

        String sql = """
        INSERT INTO arquivo (materia_id, caminho, nome_original, tamanho, texto_extraido)
        VALUES (?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (materiaId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, materiaId);
            }

            ps.setString(2, caminho);
            ps.setString(3, nomeOriginal);
            ps.setLong(4, tamanho);
            ps.setString(5, textoExtraido);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                int id = rs.next() ? rs.getInt(1) : -1;
                return new Arquivo(id, materiaId, caminho, nomeOriginal, tamanho, null);
            }
        }
    }

    public List<Arquivo> listarTodos() throws SQLException {
        garantirTabela();
        String sql = "SELECT id, materia_id, caminho, nome_original, tamanho, created_at FROM arquivo ORDER BY created_at DESC";
        List<Arquivo> lista = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDateTime createdAt = null;
                String ts = rs.getString("created_at");
                if (ts != null) {
                    createdAt = LocalDateTime.parse(ts.replace('T', ' '), FMT);
                }
                lista.add(new Arquivo(
                        rs.getInt("id"),
                        (Integer) rs.getObject("materia_id"),
                        rs.getString("caminho"),
                        rs.getString("nome_original"),
                        rs.getLong("tamanho"),
                        createdAt
                ));
            }
        }
        return lista;
    }


    /** Garante que a tabela arquivo exista mesmo que o schema antigo não a tenha criado. */
    private void garantirTabela() throws SQLException {
        String ddl = """
        CREATE TABLE IF NOT EXISTS arquivo (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            materia_id INTEGER,
            caminho TEXT NOT NULL,
            nome_original TEXT NOT NULL,
            tamanho INTEGER,
            texto_extraido TEXT,
            questoes_extraidas INTEGER,
            created_at TEXT DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY (materia_id) REFERENCES materia(id)
        )
        """;

        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            st.execute(ddl);
        }

        // Garante colunas caso banco antigo já exista
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            st.execute("ALTER TABLE arquivo ADD COLUMN texto_extraido TEXT");
        } catch (SQLException ignored) { }
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            st.execute("ALTER TABLE arquivo ADD COLUMN questoes_extraidas INTEGER");
        } catch (SQLException ignored) { }
    }
}

