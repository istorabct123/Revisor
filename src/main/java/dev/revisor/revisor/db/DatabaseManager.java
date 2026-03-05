package dev.revisor.revisor.db;

import java.io.BufferedReader;
import java.io.InputStream;
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
            new java.io.File(DB_DIR).mkdirs();
            connection = DriverManager.getConnection(JDBC_URL);
            migrarSchema();
            System.out.println("Banco em: " + connection.getMetaData().getURL());
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inicializar banco: " + e.getMessage(), e);
        }
    }


    private static void executarSchema() {
        System.out.println("Carregando schema...");
        InputStream sis = DatabaseManager.class.getResourceAsStream("/db/schema.sql");
        System.out.println("InputStream: " + sis);
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
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar schema: " + e.getMessage(), e);
        }
    }
    private static final int SCHEMA_VERSION = 1; // incremente a cada mudança no schema

    private static void migrarSchema() throws SQLException {
        int versaoAtual;
        try (var rs = connection.createStatement().executeQuery("PRAGMA user_version")) {
            versaoAtual = rs.next() ? rs.getInt(1) : 0;
        }

        if (versaoAtual == 0) {
            try {
                connection.setAutoCommit(false);

                executarSchema();

                connection.createStatement()
                        .execute("PRAGMA user_version = " + SCHEMA_VERSION);

                connection.commit();
                System.out.println("Schema migrado com sucesso.");

            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
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
    public static void debugBanco() {
        try {
            DatabaseManager.init();
            var conn = DatabaseManager.getConnection();

            System.out.println("Arquivo existe: " +
                    new java.io.File(DatabaseManager.getDbPath()).exists());

            System.out.println("Tamanho (bytes): " +
                    new java.io.File(DatabaseManager.getDbPath()).length());

            var rs = conn.createStatement().executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table'"
            );

            System.out.println("Tabelas encontradas:");
            while (rs.next()) {
                System.out.println("- " + rs.getString(1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Caminho onde o arquivo .db está armazenado. */
    public static String getDbPath() {
        return DB_PATH;
    }
}
