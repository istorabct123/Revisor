package dev.revisor.revisor.db;

import dev.revisor.revisor.model.Questao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para a tabela questao, com filtros por matéria e assunto.
 */
public class QuestaoDao {

    public QuestaoDao() throws SQLException {
        garantirColunaAssunto();
    }

    private void garantirColunaAssunto() throws SQLException {
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            try {
                st.execute("ALTER TABLE questao ADD COLUMN assunto TEXT");
            } catch (SQLException e) {
                // Se a coluna já existir, ignoramos o erro (duplicate column)
            }
        }
    }

    public List<Questao> listar(Integer materiaIdFiltro, String assuntoFiltro) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT q.id, q.materia_id, q.assunto, q.enunciado, q.banca, q.ano, q.tipo, q.alternativa_correta, " +
                        "m.nome AS materia_nome " +
                        "FROM questao q LEFT JOIN materia m ON m.id = q.materia_id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (materiaIdFiltro != null) {
            sql.append("AND q.materia_id = ? ");
            params.add(materiaIdFiltro);
        }
        if (assuntoFiltro != null && !assuntoFiltro.isBlank()) {
            sql.append("AND q.assunto = ? ");
            params.add(assuntoFiltro.trim());
        }
        sql.append("ORDER BY m.nome, q.assunto, q.ano DESC, q.id DESC");

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Questao> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new Questao(
                            rs.getInt("id"),
                            (Integer) rs.getObject("materia_id"),
                            rs.getString("materia_nome"),
                            rs.getString("assunto"),
                            rs.getString("enunciado"),
                            rs.getString("banca"),
                            (Integer) rs.getObject("ano"),
                            rs.getString("tipo"),
                            (Integer) rs.getObject("alternativa_correta")
                    ));
                }
                return lista;
            }
        }
    }

    public List<String> listarAssuntosPorMateria(Integer materiaId) throws SQLException {
        String sql = "SELECT DISTINCT assunto FROM questao WHERE materia_id = ? AND assunto IS NOT NULL AND assunto <> '' ORDER BY assunto";
        List<String> assuntos = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, materiaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assuntos.add(rs.getString("assunto"));
                }
            }
        }
        return assuntos;
    }
}

