package com.stephanofer.zKothData;

import com.stephanofer.zKothData.Listeners.onKothWin;
import com.stephanofer.zKothData.Listeners.PlayerJoin;
import com.stephanofer.zKothData.database.DatabaseManager;
import com.stephanofer.zKothData.hook.KothStatsExpansion;
import fr.maxlego08.koth.KothPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZKothData extends JavaPlugin {
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {

        KothPlugin kothPlugin = (KothPlugin) getServer().getPluginManager().getPlugin("zKoth");
        if (kothPlugin == null ) {
            getServer().getPluginManager().disablePlugin(this);
        }

        saveDefaultConfig();
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.databaseManager = new DatabaseManager(this);

        int topLimit = getConfig().getInt("top-players.limit", 10);
        int playerRefresh = getConfig().getInt("cache.top-players-refresh", 180);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            databaseManager.getTopPlayers(topLimit).thenAccept(resul -> {
                if (getConfig().getBoolean("debug", false)) {
                    getLogger().info("Se cargó correctamente los tops");
                }
            });
        }, 0L, playerRefresh * 20L);

        getServer().getPluginManager().registerEvents(new onKothWin(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoin(this), this);
        registerPlaceholders();



        getLogger().info("Se Inicio todo correctamente");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Plugin desactivado correctamente.");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI encontrado, registrando placeholders...");
            new KothStatsExpansion(this).register();
            getLogger().info("Placeholders registrados correctamente");
        } else {
            getLogger().info("PlaceholderAPI no encontrado, los placeholders no estarán disponibles");
        }
    }

}
