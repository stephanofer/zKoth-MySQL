package com.stephanofer.zKothData;

import com.stephanofer.zKothData.Listeners.KothWinEvent;
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
        getServer().getPluginManager().registerEvents(new KothWinEvent(this), this);
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
        // Verificar si PlaceholderAPI está presente
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI encontrado, registrando placeholders...");
            new KothStatsExpansion(this).register();
            getLogger().info("Placeholders registrados correctamente");
        } else {
            getLogger().info("PlaceholderAPI no encontrado, los placeholders no estarán disponibles");
        }
    }

}
