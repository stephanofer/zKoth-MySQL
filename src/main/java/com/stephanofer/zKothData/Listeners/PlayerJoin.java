package com.stephanofer.zKothData.Listeners;

import com.stephanofer.zKothData.ZKothData;
import com.stephanofer.zKothData.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoin implements Listener {

    private final ZKothData plugin;
    private final DatabaseManager databaseManager;

    public PlayerJoin(ZKothData plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    /**
     * Handle player registration and data preloading during the async login phase
     * This is more efficient than waiting for PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uuid = event.getUniqueId();
        String name = event.getName();

        // Register the player in the database (name update)
        databaseManager.registerPlayerAsync(uuid, name);

        // Preload player stats asynchronously if PlaceholderAPI is enabled
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            databaseManager.getPlayerStats(uuid).thenAccept(stats -> {
                // Stats are now cached for quick access
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Preloaded stats for player: " + name);
                }
            });
        }
    }

    /**
     * Fallback handler for PlayerJoinEvent
     * This ensures player data is registered even if AsyncPlayerPreLoginEvent was missed
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Register the player in the database, but do it asynchronously to avoid lag
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.registerPlayerAsync(player.getUniqueId(), player.getName());

            // If stats weren't preloaded, load them now
            if (databaseManager.getKothDataCache().getPlayerStats(player.getUniqueId()) == null) {
                databaseManager.getPlayerStats(player.getUniqueId());
            }
        });
    }
}