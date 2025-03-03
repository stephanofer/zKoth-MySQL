package com.stephanofer.zKothData.database;

import com.stephanofer.zKothData.ZKothData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Implementación del DatabaseConnector para MySQL usando HikariCP
 * para un pool de conexiones de alto rendimiento.
 */
public class MySQLConnector implements DatabaseConnector {
    private final ZKothData plugin;
    private HikariDataSource hikari;
    // Contador atómico para conexiones abiertas (thread-safe)
    private final AtomicInteger openConnections;
    // Objeto para sincronización
    private final Object lock;

    /**
     * Constructor para configurar la conexión a MySQL
     */
    public MySQLConnector(ZKothData plugin, String hostname, int port, String database,
                          String username, String password, boolean useSSL, int poolSize) {
        this.plugin = plugin;
        this.openConnections = new AtomicInteger();
        this.lock = new Object();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + hostname + ":" + port + "/" + database +
                "?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");


        // Optimizaciones de HikariCP para MySQL
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        try { // Intentar usar el driver nuevo
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e1) {
            try { // Si no, usar el driver antiguo
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException ignored) { }
        }

        try {
            this.hikari = new HikariDataSource(config);
            plugin.getLogger().info("Conexión a base de datos establecida correctamente");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Error al conectar con MySQL. ¿Están correctas las credenciales?", ex);
        }
    }

    @Override
    public void closeConnection() {
        if (this.hikari != null && !this.hikari.isClosed()) {
            this.hikari.close();
        }
    }

    @Override
    public void connect(ConnectionCallback callback) {
        this.openConnections.incrementAndGet();
        try (Connection connection = this.hikari.getConnection()) {
            callback.accept(connection);
        } catch (SQLException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "Error al ejecutar consulta MySQL: " + ex.getMessage(), ex);
        } finally {
            int open = this.openConnections.decrementAndGet();
            synchronized (this.lock) {
                if (open == 0)
                    this.lock.notify();
            }
        }
    }

    @Override
    public void connect(ConnectionCallback callback, boolean useTransaction) {
        // Incrementa el contador de conexiones abiertas
        this.openConnections.incrementAndGet();

        try (Connection connection = this.hikari.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            if (useTransaction) {
                connection.setAutoCommit(false);
            }

            try {
                // Ejecuta el callback con la conexión
                callback.accept(connection);

                // Si estamos usando transacción, hacemos commit
                if (useTransaction) {
                    connection.commit();
                }
            } catch (SQLException ex) {
                // Si hay error y estamos usando transacción, hacemos rollback
                if (useTransaction) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackEx) {
                        this.plugin.getLogger().log(Level.SEVERE, "Error al hacer rollback: " + rollbackEx.getMessage(), rollbackEx);
                    }
                }
                throw ex;
            } finally {
                // Restauramos el estado autoCommit original
                if (useTransaction) {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
        } catch (SQLException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "Error al ejecutar consulta MySQL: " + ex.getMessage(), ex);
        } finally {
            // Decrementa el contador de conexiones abiertas
            int open = this.openConnections.decrementAndGet();
            // Si no hay más conexiones abiertas, notifica a los hilos en espera
            synchronized (this.lock) {
                if (open == 0)
                    this.lock.notify();
            }
        }
    }

    @Override
    public Connection connect() throws SQLException {
        return this.hikari.getConnection();
    }

    @Override
    public Object getLock() {
        return this.lock;
    }

    @Override
    public boolean isFinished() {
        return this.openConnections.get() == 0;
    }

    @Override
    public void cleanup() {
    }
}