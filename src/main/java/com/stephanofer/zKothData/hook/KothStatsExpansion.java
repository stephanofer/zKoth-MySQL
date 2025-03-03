package com.stephanofer.zKothData.hook;

import com.stephanofer.zKothData.ZKothData;
import com.stephanofer.zKothData.database.DatabaseManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class KothStatsExpansion extends PlaceholderExpansion {
    private final ZKothData plugin;
    private final DatabaseManager databaseManager;



    // Caché para mejorar el rendimiento y evitar consultas excesivas a la base de datos
    private final Map<UUID, Map<String, Integer>> statsCache = new ConcurrentHashMap<>();
//    private final Map<String, String> topPlayersCache = new ConcurrentHashMap<>();

    // Tiempo para actualizar la caché (en milisegundos)
    private final long PLAYER_CACHE_EXPIRY = 60000; // 1 minuto
//    private final long TOP_CACHE_EXPIRY = 300000; // 5 minutos

//    private long lastTopCacheUpdate = 0;
    private final Map<UUID, Long> playerCacheTimestamps = new ConcurrentHashMap<>();

    public KothStatsExpansion(ZKothData plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredCache, 20 * 60, 20 * 60);

    }

     @Override
    public String getIdentifier() {
        return "zkothstats";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Queremos que esta expansión permanezca cargada en el servidor
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * Método que procesa los placeholders
     */
    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) {
            return "";
        }

        // Placeholder para total de victorias: %kothstats_total_wins%
        if (identifier.equals("total_wins")) {
            return String.valueOf(getTotalWins(player.getUniqueId()));
        }

        return null;
    }

    /**
     * Obtiene el total de victorias de un jugador
     */
    private int getTotalWins(UUID playerUuid) {
        try {
            ensurePlayerDataLoaded(playerUuid);

            Map<String, Integer> playerStats = statsCache.getOrDefault(playerUuid, new HashMap<>());
            return playerStats.values().stream().mapToInt(Integer::intValue).sum();
        } catch (Exception e) {
            plugin.getLogger().warning("Error al obtener total de victorias para " + playerUuid + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Asegura que los datos del jugador estén cargados en caché
     */
    private void ensurePlayerDataLoaded(UUID playerUuid) throws ExecutionException, InterruptedException, SQLException {
        // Verificar si necesitamos actualizar la caché
        Long lastUpdate = playerCacheTimestamps.get(playerUuid);
        long now = System.currentTimeMillis();

        if (lastUpdate == null || (now - lastUpdate > PLAYER_CACHE_EXPIRY)) {
            // Necesitamos actualizar la caché
            CompletableFuture<ResultSet> future = databaseManager.getPlayerStats(playerUuid);
            ResultSet rs = future.get(); // Esperamos por los resultados

            if (rs != null) {
                Map<String, Integer> playerStats = new HashMap<>();

                while (rs.next()) {
                    String kothName = rs.getString("koth_name");
                    int wins = rs.getInt("wins");
                    playerStats.put(kothName, wins);
                }

                // Actualizar caché
                statsCache.put(playerUuid, playerStats);
                playerCacheTimestamps.put(playerUuid, now);

                // Cerrar recursos
                rs.getStatement().getConnection().close();
            }
        }
    }


    /**
     * Limpia la caché expirada para evitar fugas de memoria
     */
    private void cleanupExpiredCache() {
        long now = System.currentTimeMillis();

        // Limpiar caché de jugadores
        playerCacheTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > PLAYER_CACHE_EXPIRY) {
                // También eliminar las estadísticas
                statsCache.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
