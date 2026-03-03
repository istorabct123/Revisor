package dev.revisor.revisor.db;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Gerencia a conexão com o banco SQLite e a criação do schema.
 * O arquivo revisor.db é criado no diretório do usuário (ex: %APPDATA%/Revisor/revisor.db).
 */
public class DatabaseManager {

    private static final String DB_DIR = System.getProperty("user.home") + "/.revisor";
    private static final String DB_PATH = DB_DIR + "/revisor.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    private static Connection connection;

    /**
     * Inicializa o banco: cria diretório se necessário e executa o schema.
     * Deve ser chamado uma vez no startup da aplicação.
     */
    public static void init() {
        try {
            java.io.File dir = new java.io.File(DB_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            connection = DriverManager.getConnection(JDBC_URL);
            executarSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar banco: " + e.getMessage(), e);
        }
    }

    private static void executarSchema() {
        try (var is = DatabaseManager.class.getResourceAsStream("/db/schema.sql")) {
            if (is == null) {
                throw new RuntimeException("Schema não encontrado: /db/schema.sql");
            }
            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // Executa cada statement separadamente (SQLite não suporta múltiplos em um execute)
            String[] statements = sql.split(";");
            try (Statement stmt = connection.createStatement()) {
                for (String s : statements) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        stmt.execute(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar schema: " + e.getMessage(), e);
        }
    }

    /**
     * Retorna a conexão ativa. init() deve ter sido chamado antes.
     */
    public static Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("DatabaseManager.init() deve ser chamado primeiro.");
        }
        return connection;
    }

    /**
     * Fecha a conexão. Útil ao encerrar a aplicação.
     */
    public static void close() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    /** Caminho onde o arquivo .db está armazenado. */
    public static String getDbPath() {
        return DB_PATH;
    }
}
