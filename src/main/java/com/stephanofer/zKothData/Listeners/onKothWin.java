package com.stephanofer.zKothData.Listeners;


import com.stephanofer.zKothData.ZKothData;
import com.stephanofer.zKothData.database.DatabaseManager;
import com.stephanofer.zKothData.models.KothWinDTO;
import fr.maxlego08.koth.api.Koth;
import fr.maxlego08.koth.api.events.KothWinEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Level;

public class onKothWin implements Listener {

    private final ZKothData plugin;
    private final DatabaseManager databaseManager;

    public onKothWin(ZKothData plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKothWinListener(KothWinEvent event) {

        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Koth koth = event.getKoth();
        String kothName = koth.getName();

        if (player == null) {
            plugin.getLogger().warning("Evento KothWinEvent recibido con jugador nulo para el koth: " + kothName);
            return;
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(String.format(
                    "Evento KothWinEvent recibido: Jugador=%s, UUID=%s, KotH=%s",
                    player.getName(), player.getUniqueId(), kothName
            ));
        }

        KothWinDTO win = new KothWinDTO(player.getUniqueId(), player.getName(), kothName);
        databaseManager.registerWinAsync(win).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE,
                    "Error registrando victoria para " + player.getName() +
                            " en KotH " + kothName + ": " + ex.getMessage(), ex);
            return false;
        });

    }

}