package com.stephanofer.zKothData.database;

import com.stephanofer.zKothData.KothDataCache;
import com.stephanofer.zKothData.ZKothData;
import com.stephanofer.zKothData.models.KothWinDTO;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DatabaseManager {

    private final ZKothData plugin;
    private final DatabaseConnector databaseConnector;
    private final KothDataCache kothDataCache;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private final Map<String, Long> queryTotalTime = new HashMap<>();
    private final Map<String, Integer> queryCount = new HashMap<>();

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

    private static final String GET_TOP_PLAYERS_QUERY =
            "SELECT p.uuid, p.name, SUM(s.wins) as total_wins " +
                    "FROM koth_players p " +
                    "JOIN koth_stats s ON p.uuid = s.player_uuid " +
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
        int connectionTimeout  = plugin.getConfig().getInt("database.connection.timeout", 5000);

        logInfo("Initializing database connection to MySQL: " + host + ":" + port + "/" + database);
        logInfo("Connection pool size: " + poolSize + ", SSL: " + useSSL);

        this.databaseConnector = new MySQLConnector(plugin, host, port, database,
                username, password, useSSL, poolSize, connectionTimeout);
        this.kothDataCache = new KothDataCache(plugin);

        initializeTables();

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                    this::logQueryPerformance, 6000L, 6000L);
        }
    }

    private void initializeTables() {
        logInfo("Initializing database tables at " + getCurrentTime());
        long startTime = System.currentTimeMillis();

        databaseConnector.connect(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_KOTH_PLAYERS_TABLE);
                logInfo("Table koth_players created/verified.");
                statement.executeUpdate(CREATE_KOTH_WINS_TABLE);
                logInfo("Table koth_wins created/verified.");
                statement.executeUpdate(CREATE_KOTH_STATS_TABLE);
                logInfo("Table koth_stats created/verified.");
            }
        });

        long duration = System.currentTimeMillis() - startTime;
        logInfo("Tables initialization completed in " + duration + "ms");
    }

    public void close() {
        logInfo("Closing database connection at " + getCurrentTime());
        databaseConnector.closeConnection();
        logQueryPerformance();
    }

    public KothDataCache getKothDataCache() {
        return kothDataCache;
    }

    public CompletableFuture<Boolean> registerPlayerAsync(UUID uuid, String name) {

        return CompletableFuture.supplyAsync(() -> {
            AtomicBoolean success = new AtomicBoolean(false);

            databaseConnector.connect(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(INSERT_PLAYER)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, name);
                    stmt.setString(3, name);
                    int rows = stmt.executeUpdate();
                    logDebug("Player registration affected " + rows + " rows");
                    success.set(true);
                }
            });

            return success.get();
        });
    }

    public CompletableFuture<Boolean> registerWinAsync(KothWinDTO win) {

        return CompletableFuture.supplyAsync(() -> {
            AtomicBoolean success = new AtomicBoolean(false);

            databaseConnector.connect(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(INSERT_WIN)) {
                    stmt.setString(1, win.getPlayerUuid().toString());
                    stmt.setString(2, win.getKothName());
                    int rows = stmt.executeUpdate();
                    logDebug("Win registration affected " + rows + " rows");
                }

                try (PreparedStatement stmt = connection.prepareStatement(UPDATE_STATS)) {
                    stmt.setString(1, win.getPlayerUuid().toString());
                    stmt.setString(2, win.getKothName());
                    int rows = stmt.executeUpdate();
                    logDebug("Stats update affected " + rows + " rows");
                }

                success.set(true);
            }, true);

            return success.get();
        }).thenApply(success -> {
            if (success) {
                kothDataCache.incrementKothWin(win.getPlayerUuid(), win.getKothName());
                logDebug("Cache updated for " + win.getPlayerName() + " after successful DB transaction");
            } else {
                logDebug("Database transaction failed, skipping cache update for " + win.getPlayerName());
            }
            return success;
        });
    }

    public CompletableFuture<Map<String, Integer>> getPlayerStats(UUID uuid) {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, Integer> cachedStats = kothDataCache.getPlayerStats(uuid);
            if (cachedStats != null) {
                logDebug("Player stats found in cache for " + uuid);
                return cachedStats;
            }

            Map<String, Integer> stats = new HashMap<>();
            AtomicReference<Map<String, Integer>> resultStats = new AtomicReference<>(stats);
            logDebug("Player stats not in cache, querying database for " + uuid);

            databaseConnector.connect(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(GET_PLAYER_STATS)) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();

                    int kothCount = 0;
                    while (rs.next()) {
                        String kothName = rs.getString("koth_name");
                        int wins = rs.getInt("wins");
                        stats.put(kothName, wins);
                        kothCount++;
                    }

                    logDebug("Retrieved " + kothCount + " KotH stats for player " + uuid);
                    kothDataCache.setPlayerStats(uuid, stats);
                    resultStats.set(stats);
                }
            });

            return resultStats.get();
        });
    }

    public CompletableFuture<List<Map<String, Object>>> getTopPlayers(int limit) {

        return CompletableFuture.supplyAsync(() -> {
            int refreshSeconds = plugin.getConfig().getInt("cache.top-players-refresh", 60);

            if (!kothDataCache.needsTopPlayersRefresh(refreshSeconds)) {
                List<Map<String, Object>> cachedResults = kothDataCache.getTopPlayers(limit);
                if (!cachedResults.isEmpty()) {
                    logDebug("Top players found in cache, returning " + cachedResults.size() + " players");
                    return cachedResults;
                }
            }

            logDebug("Top players not in cache or expired, querying database");
            List<Map<String, Object>> results = new ArrayList<>();
            AtomicReference<List<Map<String, Object>>> resultList = new AtomicReference<>(results);

            databaseConnector.connect(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(GET_TOP_PLAYERS_QUERY)) {
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

                    logDebug("Retrieved " + results.size() + " top players from database");
                    // Update cache
                    kothDataCache.updateTopPlayers(results);
                    resultList.set(results);
                }
            });

            return resultList.get();
        });
    }

    public void bulkInsertPlayers(Map<UUID, String> players) {
        if (players.isEmpty()) return;

        databaseConnector.connect(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(INSERT_PLAYER)) {
                int count = 0;
                for (Map.Entry<UUID, String> entry : players.entrySet()) {
                    stmt.setString(1, entry.getKey().toString());
                    stmt.setString(2, entry.getValue());
                    stmt.setString(3, entry.getValue());
                    stmt.addBatch();
                    count++;

                    // Log progress for large batches
                    if (count % 100 == 0) {
                        logDebug("Prepared " + count + "/" + players.size() + " players for batch insert");
                    }
                }
                int[] results = stmt.executeBatch();
                logDebug("Bulk insert completed for " + results.length + " players");
            }
        });

    }


    private void logQueryPerformance() {
        synchronized (queryTotalTime) {
            if (queryCount.isEmpty()) {
                return;
            }

            logInfo("=== DATABASE PERFORMANCE STATISTICS ===");
            logInfo("Current time: " + getCurrentTime());
            logInfo("Total queries executed: " + queryCount.values().stream().mapToInt(Integer::intValue).sum());

            queryTotalTime.entrySet().stream()
                    .sorted((e1, e2) -> {
                        double avg1 = e1.getValue() / (double) queryCount.get(e1.getKey());
                        double avg2 = e2.getValue() / (double) queryCount.get(e2.getKey());
                        return Double.compare(avg2, avg1);
                    })
                    .forEach(entry -> {
                        String operation = entry.getKey();
                        long totalTime = entry.getValue();
                        int count = queryCount.get(operation);
                        double avgTime = totalTime / (double) count;

                        logInfo(String.format("Operation: %-25s | Count: %-5d | Avg time: %.2fms | Total: %dms",
                                operation, count, avgTime, totalTime));
                    });

            logInfo("=======================================");
        }
    }


    private void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    private void logDebug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DB] " + message);
        }
    }

    private String getCurrentTime() {
        return timeFormat.format(new Date());
    }

}
