package com.stephanofer.zKothData.database;

import com.stephanofer.zKothData.Listeners.KothWinEvent;
import com.stephanofer.zKothData.ZKothData;
import com.stephanofer.zKothData.models.KothWin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class DatabaseManager {

    private final ZKothData plugin;
    private final DatabaseConnector databaseConnector;

    private static final String CREATE_KOTH_PLAYERS_TABLE =
            "CREATE TABLE IF NOT EXISTS koth_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "name VARCHAR(16) NOT NULL, " +
                    "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");";

    private static final String CREATE_KOTH_WINS_TABLE =
            "CREATE TABLE IF NOT EXISTS koth_wins (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "koth_name VARCHAR(64) NOT NULL, " +
                    "win_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (player_uuid) REFERENCES koth_players(uuid) ON DELETE CASCADE" +
                    ");";

    private static final String CREATE_KOTH_STATS_TABLE =
            "CREATE TABLE IF NOT EXISTS koth_stats (" +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "koth_name VARCHAR(64) NOT NULL, " +
                    "wins INT DEFAULT 0, " +
                    "PRIMARY KEY (player_uuid, koth_name), " +
                    "FOREIGN KEY (player_uuid) REFERENCES koth_players(uuid) ON DELETE CASCADE" +
                    ");";
    private static final String INSERT_PLAYER =
            "INSERT INTO koth_players (uuid, name) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE name = ?, last_seen = CURRENT_TIMESTAMP";

    private static final String INSERT_WIN =
            "INSERT INTO koth_wins (player_uuid, koth_name) VALUES (?, ?)";

    private static final String UPDATE_STATS =
            "INSERT INTO koth_stats (player_uuid, koth_name, wins) VALUES (?, ?, 1) " +
                    "ON DUPLICATE KEY UPDATE wins = wins + 1";

    private static final String GET_PLAYER_STATS =
            "SELECT k.koth_name, s.wins FROM koth_stats s " +
                    "JOIN (SELECT DISTINCT koth_name FROM koth_wins) k " +
                    "ON s.koth_name = k.koth_name " +
                    "WHERE s.player_uuid = ? " +
                    "ORDER BY s.wins DESC";

    private static final String GET_TOP_PLAYERS =
            "SELECT p.name, SUM(s.wins) as total_wins " +
                    "FROM koth_stats s " +
                    "JOIN koth_players p ON s.player_uuid = p.uuid " +
                    "GROUP BY p.uuid, p.name " +
                    "ORDER BY total_wins DESC " +
                    "LIMIT ?";

    public DatabaseManager(ZKothData plugin) {
        this.plugin = plugin;


        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database", "minecraft");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");
        boolean useSSL = plugin.getConfig().getBoolean("database.useSSL", false);
        int poolSize = plugin.getConfig().getInt("database.connection.max-pool-size", 10);

        this.databaseConnector = new MySQLConnector(plugin, host, port, database,
                username, password, useSSL, poolSize);

        initializeTables();
    }


    private void initializeTables() {
        databaseConnector.connect(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_KOTH_PLAYERS_TABLE);
                plugin.getLogger().info("Tabla koth_players creada/verificada.");
                statement.executeUpdate(CREATE_KOTH_WINS_TABLE);
                plugin.getLogger().info("Tabla koth_wins creada/verificada.");
                statement.executeUpdate(CREATE_KOTH_STATS_TABLE);
                plugin.getLogger().info("Tabla koth_stats creada/verificada.");
            }
        });
    }

    public void close() {
        databaseConnector.closeConnection();
    }

    /**
     * Registra un jugador en la base de datos o actualiza su información
     * @param uuid UUID del jugador
     * @param name Nombre del jugador
     */
    public void registerPlayer(UUID uuid, String name) {
        databaseConnector.connect(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(INSERT_PLAYER)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.setString(3, name);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Registra una victoria de KotH y actualiza las estadísticas
     * @param win Objeto con la información de la victoria
     */
    public void registerWin(KothWin win) {
        databaseConnector.connect(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(INSERT_WIN)) {
                stmt.setString(1, win.getPlayerUuid().toString());
                stmt.setString(2, win.getKothName());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = connection.prepareStatement(UPDATE_STATS)) {
                stmt.setString(1, win.getPlayerUuid().toString());
                stmt.setString(2, win.getKothName());
                stmt.executeUpdate();
            }
        }, true);
    }


    /**
     * Obtiene las estadísticas de un jugador
     * @param uuid UUID del jugador
     * @return CompletableFuture con los resultados
     */
    public CompletableFuture<Map<String, Integer>> getPlayerStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
//            Map<String, Integer> cachedStats = cache.getPlayerStats(uuid);
//            if (cachedStats != null) {
////                return cachedStats;
////            }

            // Si no está en caché, consultamos la base de datos
            Map<String, Integer> stats = new HashMap<>();
            AtomicReference<Map<String, Integer>> resultStats = new AtomicReference<>(stats);

            databaseConnector.connect(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(GET_PLAYER_STATS)) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        String kothName = rs.getString("koth_name");
                        int wins = rs.getInt("wins");
                        stats.put(kothName, wins);
                    }

                    // Almacenamos en caché para futuras consultas
//                    cache.setPlayerStats(uuid, stats);
                    resultStats.set(stats);
                }
            });

            return resultStats.get();
        });
    }

    /**
     * Obtiene los mejores jugadores
     * @param limit Número máximo de jugadores a retornar
     * @return CompletableFuture con los resultados
     */
    public CompletableFuture<List<Map<String, Object>>> getTopPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            // Verificar si tenemos resultados recientes en caché
//            List<Map<String, Object>> cachedResults = cache.getTopPlayers(limit);
//            if (cachedResults != null) {
//                return cachedResults;
//            }

            List<Map<String, Object>> results = new ArrayList<>();
            AtomicReference<List<Map<String, Object>>> resultList = new AtomicReference<>(results);

            databaseConnector.connect(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(GET_TOP_PLAYERS)) {
                    stmt.setInt(1, limit);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        Map<String, Object> playerData = new HashMap<>();
                        String name = rs.getString("name");
                        String uuidStr = rs.getString("uuid");
                        int totalWins = rs.getInt("total_wins");

                        playerData.put("name", name);
                        playerData.put("uuid", UUID.fromString(uuidStr));
                        playerData.put("totalWins", totalWins);

                        results.add(playerData);
                    }

                    // Almacenamos en caché para futuras consultas
//                    cache.setTopPlayers(limit, results);
                    resultList.set(results);
                }
            });

            return resultList.get();
        });
    }

    /**
     * Obtiene el número total de victorias para un KotH específico
     *
     * @param kothName Nombre del KotH
     * @return CompletableFuture con el número total de victorias
     */
    public CompletableFuture<Integer> getTotalWinsForKoth(String kothName) {
        return CompletableFuture.supplyAsync(() -> {
            AtomicInteger totalWins = new AtomicInteger(0);

            databaseConnector.connect(connection -> {
                String query = "SELECT COUNT(*) FROM koth_wins WHERE koth_name = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, kothName);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        totalWins.set(rs.getInt(1));
                    }
                }
            });

            return totalWins.get();
        });
    }

    /**
     * Método para ejecutar lotes de operaciones de manera eficiente
     * Útil para importar/exportar grandes cantidades de datos
     *
     * @param players Mapa de UUID -> nombre del jugador
     */
    public void bulkInsertPlayers(Map<UUID, String> players) {
        if (players.isEmpty()) return;

        databaseConnector.connect(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(INSERT_PLAYER)) {
                for (Map.Entry<UUID, String> entry : players.entrySet()) {
                    stmt.setString(1, entry.getKey().toString());
                    stmt.setString(2, entry.getValue());
                    stmt.setString(3, entry.getValue());
                    stmt.addBatch(); // Agregar a lote en lugar de ejecutar uno por uno
                }
                stmt.executeBatch(); // Ejecutar todo el lote de una vez
            }
        });
    }
}
