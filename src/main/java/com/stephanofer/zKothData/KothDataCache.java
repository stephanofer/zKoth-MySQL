package com.stephanofer.zKothData;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.configuration.ConfigurationSection;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class KothDataCache {

    private final ZKothData plugin;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final Cache<UUID, Map<String, Integer>> playerStatsCache;
    private final List<Map<String, Object>> topPlayersCache;
    private long lastTopPlayersUpdate = 0;
    private final Object topPlayersLock = new Object();
    private final int maxTopPlayersSize;



    private int cacheHits = 0;
    private int cacheMisses = 0;
    private int cacheUpdates = 0;
    private int topPlayersRefreshes = 0;


    public KothDataCache(ZKothData plugin) {
        this.plugin = plugin;

        ConfigurationSection cacheConfig = plugin.getConfig().getConfigurationSection("cache");
        int playerStatsExpiry = cacheConfig != null ? cacheConfig.getInt("player-stats-expiry", 5) : 5;
        int topPlayersExpiry = cacheConfig != null ? cacheConfig.getInt("top-players-refresh", 60) : 60;
        this.maxTopPlayersSize = cacheConfig != null ? cacheConfig.getInt("top-players-max-size", 100) : 10;

        this.playerStatsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(playerStatsExpiry, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build();

        this.topPlayersCache = new ArrayList<>();

        plugin.getLogger().info("Cache initialized: Player stats expire after " + playerStatsExpiry +
                " minutes, Top players refresh after " + topPlayersExpiry + " seconds");

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                    this::logCacheStatistics, 1200L, 1200L);
        }
    }

    private void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    private void logDebug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[CACHE] " + message);
        }
    }

    private String getCurrentTime() {
        return timeFormat.format(new Date());
    }

    private String getFormattedTime(long timestamp) {
        return timeFormat.format(new Date(timestamp));
    }

    public Map<String, Integer> getPlayerStats(UUID uuid) {
        Map<String, Integer> stats = playerStatsCache.getIfPresent(uuid);

        if(stats != null) {
            cacheHits++;
            logDebug("CACHE HIT - Retrieved stats for player "  + uuid + " : " + cacheHits);
            return stats;
        }else {
            cacheMisses++;
            logDebug("CACHE MISS - Retrieved stats for player "  + uuid);
            return null;
        }
    }


    public void setPlayerStats(UUID uuid, Map<String, Integer> stats) {
        cacheUpdates++;
        playerStatsCache.put(uuid, stats);

        logDebug("CACHE UPDATE - Stored stats for player " + uuid + " with " +
                stats.size() + " KotH entries");
    }

    public void invalidatePlayerStats(UUID uuid) {
        playerStatsCache.invalidate(uuid);
        logDebug("CACHE INVALIDATE - Removed stats for player " + uuid);
    }

    public List<Map<String, Object>> getTopPlayers(int limit) {
        synchronized (topPlayersLock) {
            if (topPlayersCache.isEmpty()) {
                logDebug("TOP PLAYERS CACHE - Empty cache requested");
                return Collections.emptyList();
            }

            logDebug("TOP PLAYERS CACHE - Retrieved " +
                    Math.min(limit, topPlayersCache.size()) + " players from cache");
            return topPlayersCache.subList(0, Math.min(limit, topPlayersCache.size()));
        }
    }

    public void updateTopPlayers(List<Map<String, Object>> players) {
        synchronized (topPlayersLock) {
            topPlayersCache.clear();
            int count = Math.min(players.size(), maxTopPlayersSize);
            topPlayersCache.addAll(players.subList(0, count));
            lastTopPlayersUpdate = System.currentTimeMillis();
            topPlayersRefreshes++;
            logDebug("TOP PLAYERS CACHE - Updated with " + players.size() +
                    " players at " + getCurrentTime());
        }
    }

    public boolean needsTopPlayersRefresh(int refreshSeconds) {
        boolean needsRefresh = System.currentTimeMillis() - lastTopPlayersUpdate > refreshSeconds * 1000L;
        if (needsRefresh) {
            logDebug("TOP PLAYERS CACHE - Refresh needed (last update: " +
                    getFormattedTime(lastTopPlayersUpdate) + ")");
        }
        return needsRefresh;
    }

    public int getTotalWins(UUID uuid) {
        Map<String, Integer> stats = getPlayerStats(uuid);
        if (stats == null) {
            return 0;
        }

        int totalWins = stats.values().stream().mapToInt(Integer::intValue).sum();
        logDebug("CACHE STATS - Player " + uuid + " has " + totalWins + " total wins");
        return totalWins;
    }


    public int getKothWins(UUID uuid, String kothName) {
        Map<String, Integer> stats = getPlayerStats(uuid);
        if (stats == null) {
            return 0;
        }

        int wins = stats.getOrDefault(kothName, 0);
        logDebug("CACHE STATS - Player " + uuid + " has " + wins +
                " wins for KotH " + kothName);
        return wins;
    }


    public void incrementKothWin(UUID uuid, String kothName) {
        Map<String, Integer> stats = getPlayerStats(uuid);
        if (stats == null) {
            stats = new HashMap<>();
            logDebug("CACHE CREATE - New stats map created for player " + uuid);
        }

        int oldValue = stats.getOrDefault(kothName, 0);
        stats.put(kothName, oldValue + 1);
        setPlayerStats(uuid, stats);

        logDebug("CACHE INCREMENT - Player " + uuid + " KotH " + kothName +
                " wins updated from " + oldValue + " to " + (oldValue + 1));
    }

    private void logCacheStatistics() {
        logInfo("=== CACHE STATISTICS ===");
        logInfo("Current time: " + getCurrentTime());
        logInfo("Cache size: " + playerStatsCache.size() + " players");
        logInfo("Cache hits: " + cacheHits + ", misses: " + cacheMisses +
                ", hit ratio: " + (cacheHits + cacheMisses > 0 ?
                (cacheHits * 100 / (cacheHits + cacheMisses)) + "%" : "N/A"));
        logInfo("Cache updates: " + cacheUpdates);
        logInfo("Top players refreshes: " + topPlayersRefreshes);
        logInfo("Last top players update: " +
                (lastTopPlayersUpdate > 0 ? getFormattedTime(lastTopPlayersUpdate) : "Never"));
        logInfo("Guava stats: " + playerStatsCache.stats().toString());
        logInfo("========================");
    }
}