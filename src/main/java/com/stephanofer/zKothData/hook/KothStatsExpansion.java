package com.stephanofer.zKothData.hook;

import com.stephanofer.zKothData.KothDataCache;
import com.stephanofer.zKothData.ZKothData;
import com.stephanofer.zKothData.database.DatabaseManager;
import com.stephanofer.zKothData.models.SortedPlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class KothStatsExpansion extends PlaceholderExpansion {
    private final ZKothData plugin;
    private final DatabaseManager databaseManager;
    private final KothDataCache kothDataCache;


    public KothStatsExpansion(ZKothData plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.kothDataCache = plugin.getDatabaseManager().getKothDataCache();
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

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) {
            return "";
        }
        UUID uuid = player.getUniqueId();

        if (identifier.equals("total_wins")) {
            return String.valueOf(getTotalWins(uuid));
        }

        if (identifier.startsWith("wins_")) {
            String kothName = identifier.substring(5);
            return String.valueOf(getKothWins(uuid, kothName));
        }

        if (identifier.startsWith("top_")) {
            return handleTopPlaceholder(identifier);
        }

        return null;
    }

    private int getTotalWins(UUID uuid) {
        return kothDataCache.getTotalWins(uuid);
    }

    private int getKothWins(UUID uuid, String kothName) {
       return kothDataCache.getKothWins(uuid, kothName);
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
        String[] parts = identifier.split("_");
        if (parts.length < 3) {
            return "0";
        }

        try {
            int position = Integer.parseInt(parts[1]);
            String field = parts[2];

            int topLimit = plugin.getConfig().getInt("top-players.limit", 10);
            List<SortedPlayer> topPlayers = databaseManager.getTopPlayers(topLimit).get();

            if (position <= 0 || position > topPlayers.size()) {
                return field.equals("name") ? "Ninguno" : "0";
            }

            SortedPlayer playerData = topPlayers.get(position - 1);

            switch (field) {
                case "name":
                    return playerData.getName();
                case "wins":
                    return String.valueOf(playerData.getTotalWins());
                default:
                    return "0";
            }
        } catch (NumberFormatException e) {
            return "0";
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading top players: " + e.getMessage(), e);
            return "0";
        }
    }

}
