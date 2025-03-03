package com.stephanofer.zKothData.Listeners;


import com.stephanofer.zKothData.ZKothData;
import com.stephanofer.zKothData.database.DatabaseManager;
import com.stephanofer.zKothData.models.KothWin;
import fr.maxlego08.koth.api.Koth;
import fr.maxlego08.koth.zcore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class KothWinEvent implements Listener {

    private final ZKothData plugin;
    private final DatabaseManager databaseManager;

    public KothWinEvent(ZKothData plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKothWin(fr.maxlego08.koth.api.events.KothWinEvent event) {

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

        databaseManager.registerPlayer(player.getUniqueId(), player.getName());
        KothWin win = new KothWin(player.getUniqueId(), player.getName(), kothName);
        databaseManager.registerWin(win);

//        String message = plugin.getConfig().getString(
//                "messages.win",
//                "&a¡Has ganado el KotH &6{koth}&a! Tienes &6{wins}&a victorias en total.");

//        Bukkit.getScheduler().runTaskLater(plugin, () -> {
//            databaseManager.getPlayerStats(player.getUniqueId()).thenAccept(resultSet -> {
//                try {
//                    int totalWins = 0;
//                    int kothSpecificWins = 0;
//
//                    while (resultSet.next()) {
//                        String currentKoth = resultSet.getString("koth_name");
//                        int wins = resultSet.getInt("wins");
//                        totalWins += wins;
//
//                        if (currentKoth.equals(kothName)) {
//                            kothSpecificWins = wins;
//                        }
//                    }
//
//                    String finalMessage = message
//                            .replace("{koth}", kothName)
//                            .replace("{wins}", String.valueOf(kothSpecificWins))
//                            .replace("{total_wins}", String.valueOf(totalWins));
//
//                    player.sendMessage(finalMessage);
//
//                    // Cerramos el ResultSet y liberamos recursos
//                    resultSet.getStatement().getConnection().close();
//                } catch (Exception e) {
//                    plugin.getLogger().warning("Error al procesar estadísticas para notificación: " + e.getMessage());
//                }
//            });
//        }, 20L); // 1 segundo después (20 ticks)
    }

}