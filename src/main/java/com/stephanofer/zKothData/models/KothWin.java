package com.stephanofer.zKothData.models;

import java.time.LocalDateTime;
import java.util.UUID;

public class KothWin {

    private final UUID playerUuid;
    private final String playerName;
    private final String kothName;
    private final LocalDateTime winTime;

    public KothWin(UUID playerUuid, String playerName, String kothName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.kothName = kothName;
        this.winTime = LocalDateTime.now();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getKothName() {
        return kothName;
    }

    public LocalDateTime getWinTime() {
        return winTime;
    }

    @Override
    public String toString(){
        return "KothWin{" +
                "playerName='" + playerName + '\'' +
                ", kothName='" + kothName + '\'' +
                ", winTime=" + winTime +
                '}';
    }
}
