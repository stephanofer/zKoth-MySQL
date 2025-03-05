package com.stephanofer.zKothData.models;

import java.util.UUID;

public class SortedPlayer {


    private final UUID uuid;
    private final String name;
    private final int totalWins;

    public SortedPlayer(UUID uuid, String name, int totalWins) {
        this.uuid = uuid;
        this.name = name;
        this.totalWins = totalWins;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getTotalWins() {
        return totalWins;
    }
}
