package com.stephanofer.zKothData.hook;

import com.stephanofer.zKothData.KothDataCache;
import com.stephanofer.zKothData.ZKothData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class KothStatsExpansion extends PlaceholderExpansion {
    private final ZKothData plugin;
    private final KothDataCache kothDataCache;

    private volatile List<Map<String, Object>> topPlayersCache = Collections.emptyList();
    private long lastTopPlayersCacheUpdate = 0;
    private final long topPlayersCacheExpiryMs;

    public KothStatsExpansion(ZKothData plugin) {
        this.plugin = plugin;
        this.kothDataCache = plugin.getDatabaseManager().getKothDataCache();

        int refreshSeconds = plugin.getConfig().getInt("cache.top-players-refresh", 60);
        this.topPlayersCacheExpiryMs = TimeUnit.SECONDS.toMillis(refreshSeconds);

        // Initial population of cache
        refreshTopPlayersCache();


        plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::refreshTopPlayersCache,
                refreshSeconds * 20L, // Convert to ticks
                refreshSeconds * 20L  // Convert to ticks
        );
    }


    private void refreshTopPlayersCache() {
        int topLimit = plugin.getConfig().getInt("top-players.limit", 10);

        // Get top players from the cache manager
        plugin.getDatabaseManager().getTopPlayers(topLimit)
                .thenAccept(topPlayers -> {
                    this.topPlayersCache = topPlayers;
                    this.lastTopPlayersCacheUpdate = System.currentTimeMillis();
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[PAPI] Top players cache refreshed with " + topPlayers.size() + " entries");
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Error refreshing top players cache: " + ex.getMessage(), ex);
                    return null;
                });
    }



     @Override
    public @NotNull String getIdentifier() {
        return "zkoth";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
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
        UUID uuid = player.getUniqueId();


        // Placeholder para total de victorias: %zkothstats_total_wins%
        if (identifier.equals("total_wins")) {
            return String.valueOf(getTotalWins(uuid));
        }

        // Specific KotH wins placeholder
        if (identifier.startsWith("wins_")) {
            String kothName = identifier.substring(5); // Remove "wins_" to get kothName
            return String.valueOf(getKothWins(uuid, kothName));
        }

        // Top player placeholders
        if (identifier.startsWith("top_")) {
            return handleTopPlaceholder(identifier);
        }

        return null;
    }

    /**
     * Get total wins for a player using cache when available
     * @param uuid Player UUID
     * @return Total wins count
     */
    private int getTotalWins(UUID uuid) {
        // Check cache first for better performance
        int cachedTotal = kothDataCache.getTotalWins(uuid);
        if (cachedTotal > 0) {
            return cachedTotal;
        }

        // If not in cache, load from database
        try {
            Map<String, Integer> stats = plugin.getDatabaseManager().getPlayerStats(uuid).get();
            return stats.values().stream().mapToInt(Integer::intValue).sum();
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading player stats: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get wins for a specific KotH
     * @param uuid Player UUID
     * @param kothName KotH name
     * @return Win count
     */
    private int getKothWins(UUID uuid, String kothName) {
        // Check cache first
        int cachedWins = kothDataCache.getKothWins(uuid, kothName);
        if (cachedWins > 0) {
            return cachedWins;
        }

        // If not in cache, load from database
        try {
            Map<String, Integer> stats = plugin.getDatabaseManager().getPlayerStats(uuid).get();
            return stats.getOrDefault(kothName, 0);
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading player stats: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Handle top player placeholders
     * Format: top_<position>_<field>
     * Examples:
     * - zkoth_top_1_name
     * - zkoth_top_1_wins
     * @param identifier The full identifier
     * @return The placeholder value
     */
    private String handleTopPlaceholder(String identifier) {
        // Parse the identifier
        String[] parts = identifier.split("_");
        if (parts.length < 3) {
            return "0";
        }

        try {
            int position = Integer.parseInt(parts[1]);
            String field = parts[2];

            // Use our locally cached top players instead of hitting DB or even the KothDataCache
            List<Map<String, Object>> topPlayers = this.topPlayersCache;

            // Check if position is valid
            if (position <= 0 || position > topPlayers.size()) {
                return field.equals("name") ? "Ninguno" : "0";
            }

            // Get player at position (adjust for 0-based index)
            Map<String, Object> playerData = topPlayers.get(position - 1);

            // Return requested field
            switch (field) {
                case "name":
                    return (String) playerData.get("name");
                case "wins":
                    return String.valueOf(playerData.get("totalWins"));
                default:
                    return "0";
            }
        } catch (NumberFormatException e) {
            return "0";
        }
    }

}
