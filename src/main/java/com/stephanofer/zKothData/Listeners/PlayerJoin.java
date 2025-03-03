package com.stephanofer.zKothData.Listeners;

import com.stephanofer.zKothData.ZKothData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class PlayerJoin implements Listener {

    private final ZKothData plugin;

    public PlayerJoin(ZKothData plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(AsyncPlayerPreLoginEvent event){
        event.getName();
    }
}
