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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uuid = event.getUniqueId();
        String name = event.getName();

        databaseManager.registerPlayerAsync(uuid, name);

        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            databaseManager.getPlayerStats(uuid).thenAccept(stats -> {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Preloaded stats for player: " + name);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            databaseManager.registerPlayerAsync(player.getUniqueId(), player.getName());

            if (databaseManager.getKothDataCache().getPlayerStats(player.getUniqueId()) == null) {
                databaseManager.getPlayerStats(player.getUniqueId());
            }
        });
    }
}