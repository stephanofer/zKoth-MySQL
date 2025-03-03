package com.stephanofer.zKothData.models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStats {

    private final UUID playerUuid;
    private final String playerName;
    private final Map<String, Integer> kothWins;
    private int totalWins;

    public PlayerStats(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.kothWins =  new HashMap<>();
        this.totalWins = 0;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void addKothWin(String kothName) {
        int currentWins = kothWins.getOrDefault(kothName, 0);
        kothWins.put(kothName, currentWins + 1);
        totalWins++;
    }

    public int getWinsForKoth(String kothName) {
        return kothWins.getOrDefault(kothName, 0);
    }

    public Map<String, Integer> getAllKothWins() {
        return new HashMap<>(kothWins);
    }

    public int getTotalWins() {
        return totalWins;
    }

    @Override
    public String toString() {
        return "PlayerStats{" +
                "playerName='" + playerName + '\'' +
                ", totalWins=" + totalWins +
                ", kothWins=" + kothWins +
                '}';
    }
}
