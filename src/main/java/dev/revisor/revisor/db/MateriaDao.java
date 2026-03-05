package dev.revisor.revisor.db;

import dev.revisor.revisor.model.Materia;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MateriaDao {

    public Materia inserir(String nome, String cor) throws SQLException {
        String sql = "INSERT INTO materia (nome, cor) VALUES (?, ?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setString(2, cor);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int id = rs.next() ? rs.getInt(1) : -1;
                return new Materia(id, nome, cor);
            }
        }
    }

    public void atualizar(int id, String novoNome, String novaCor) throws SQLException {
        String sql = "UPDATE materia SET nome = ?, cor = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, novoNome);
            ps.setString(2, novaCor);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public List<Materia> listar() throws SQLException {
        List<Materia> lista = new ArrayList<>();
        String sql = "SELECT id, nome, cor FROM materia ORDER BY nome";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Materia(
                        rs.getInt("id"),
                        rs.getString("nome"),
                        rs.getString("cor")
                ));
            }
        }
        return lista;
    }
}
