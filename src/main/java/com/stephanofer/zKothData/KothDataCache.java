package com.stephanofer.zKothData;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.stephanofer.zKothData.database.DatabaseManager;
import com.stephanofer.zKothData.models.SortedPlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class KothDataCache {

    private final ZKothData plugin;
    private final DatabaseManager databaseManager;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");


    private final Cache<UUID, Map<String, Integer>> playerStatsCache;

    private final List<SortedPlayer> topPlayersCache;
    private final int topPlayersExpiry;


    private long lastTopPlayersUpdate = 0;
    private final Object topPlayersLock = new Object();
    private final int maxTopPlayersSize;

    private int cacheHits = 0;
    private int cacheMisses = 0;
    private int cacheUpdates = 0;
    private int topPlayersRefreshes = 0;


    public KothDataCache(ZKothData plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        ConfigurationSection cacheConfig = plugin.getConfig().getConfigurationSection("cache");
        int playerStatsExpiry = cacheConfig != null ? cacheConfig.getInt("player-stats-expiry", 5) : 5;
        this.topPlayersExpiry = cacheConfig != null ? cacheConfig.getInt("top-players-refresh", 60) : 60;
        this.maxTopPlayersSize = cacheConfig != null ? cacheConfig.getInt("top-players-max-size", 100) : 10;

        this.playerStatsCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
//                .expireAfterAccess(playerStatsExpiry, TimeUnit.MINUTES)
                .recordStats()
                .build();

        //new CacheLoader<UUID, Map<String, Integer>>() {
        //                    @Override
        //                    public Map<String, Integer> load(UUID key) throws Exception {
        //                        cacheMisses++;
        //                        return databaseManager.getPlayerStats(key).get();
        //                    }
        //                }


        this.topPlayersCache = new ArrayList<>();

        plugin.getLogger().info("Cache initialized: Player stats expire after " + playerStatsExpiry +
                " minutes, Top players refresh after " + topPlayersExpiry + " seconds");

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                    this::logCacheStatistics, 1200L, 1200L);
        }
    }

    public Map<String, Integer> getPlayerStats(UUID uuid) {
        Map<String, Integer> stats = playerStatsCache.getIfPresent(uuid);
        if(stats != null) {
            cacheHits++;
            return stats;
        }else {
            cacheMisses++;
            return null;
        }
    }


    public void setPlayerStats(UUID uuid, Map<String, Integer> stats) {
        cacheUpdates++;
        playerStatsCache.put(uuid, stats);
    }

    public void invalidatePlayerStats(UUID uuid) {
        playerStatsCache.invalidate(uuid);
    }

    public List<SortedPlayer> getTopPlayers() {
        synchronized (topPlayersLock) {
            if (topPlayersCache.isEmpty()) {
                return Collections.emptyList();
            }

            return topPlayersCache;
        }
    }

    public void updateTopPlayers(List<SortedPlayer> players) {
        synchronized (topPlayersLock) {
            topPlayersCache.clear();
            int count = Math.min(players.size(), maxTopPlayersSize);
            topPlayersCache.addAll(players.subList(0, count));
            lastTopPlayersUpdate = System.currentTimeMillis();
            topPlayersRefreshes++;
        }
    }

    public boolean needsTopPlayersRefresh() {
        return System.currentTimeMillis() - lastTopPlayersUpdate > this.topPlayersExpiry * 1000L;
    }

    public int getTotalWins(UUID uuid) {
        Map<String, Integer> stats = getPlayerStats(uuid);
        if (stats == null) {
            return 0;
        }

        return stats.values().stream().mapToInt(Integer::intValue).sum();
    }


    public int getKothWins(UUID uuid, String kothName) {
        Map<String, Integer> stats = getPlayerStats(uuid);
        if (stats == null) {
            return 0;
        }

        return stats.getOrDefault(kothName, 0);
    }


    public void incrementKothWin(UUID uuid, String kothName) {
        Map<String, Integer> stats = getPlayerStats(uuid);
        if (stats == null) {
            stats = new HashMap<>();
        }

        int oldValue = stats.getOrDefault(kothName, 0);
        stats.put(kothName, oldValue + 1);
        setPlayerStats(uuid, stats);
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


    private void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    private String getCurrentTime() {
        return timeFormat.format(new Date());
    }

    private String getFormattedTime(long timestamp) {
        return timeFormat.format(new Date(timestamp));
    }

}