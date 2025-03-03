package com.stephanofer.zKothData;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Clase para gestionar la caché de datos del plugin zKothData.
 * Usa Google Guava para implementar una caché eficiente con expiración.
 */
public class KothDataCache {

    // Caché para nombres de jugadores (UUID -> Nombre)
    private final Cache<UUID, String> playerNameCache;

    // Caché para estadísticas de jugador (UUID -> Map<KothName, Wins>)
    private final Cache<UUID, Map<String, Integer>> playerStatsCache;

    // Caché para top jugadores (limit -> List<PlayerData>)
    private final Cache<Integer, List<Map<String, Object>>> topPlayersCache;

    // Caché para victorias por Koth (kothName -> total)
    private final Cache<String, Integer> kothTotalWinsCache;

    public KothDataCache() {
        // Configuración de caché para nombres (expira después de 1 hora)
        this.playerNameCache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(1000)
                .build();

        // Configuración de caché para estadísticas (expira después de 5 minutos)
        this.playerStatsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200)
                .build();

        // Configuración de caché para top jugadores (expira después de 2 minutos)
        this.topPlayersCache = CacheBuilder.newBuilder()
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .maximumSize(10) // Pocos tamaños diferentes de límite
                .build();

        // Configuración de caché para totales por Koth (expira después de 5 minutos)
        this.kothTotalWinsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();
    }

    /**
     * Actualiza el nombre de un jugador en la caché
     *
     * @param uuid UUID del jugador
     * @param name Nombre del jugador
     */
    public void updatePlayerName(UUID uuid, String name) {
        playerNameCache.put(uuid, name);
    }

    /**
     * Obtiene el nombre de un jugador desde la caché
     *
     * @param uuid UUID del jugador
     * @return Nombre del jugador o null si no está en caché
     */
    public String getPlayerName(UUID uuid) {
        return playerNameCache.getIfPresent(uuid);
    }

    /**
     * Establece las estadísticas completas de un jugador en la caché
     *
     * @param uuid UUID del jugador
     * @param stats Mapa de estadísticas (KothName -> Wins)
     */
    public void setPlayerStats(UUID uuid, Map<String, Integer> stats) {
        playerStatsCache.put(uuid, new HashMap<>(stats));
    }

    /**
     * Obtiene las estadísticas de un jugador desde la caché
     *
     * @param uuid UUID del jugador
     * @return Mapa de estadísticas o null si no está en caché
     */
    public Map<String, Integer> getPlayerStats(UUID uuid) {
        Map<String, Integer> stats = playerStatsCache.getIfPresent(uuid);
        if (stats != null) {
            return new HashMap<>(stats); // Devolver copia para evitar modificaciones externas
        }
        return null;
    }

    /**
     * Incrementa el contador de victorias de un jugador en un KotH específico
     *
     * @param uuid UUID del jugador
     * @param kothName Nombre del KotH
     */
    public void incrementPlayerWins(UUID uuid, String kothName) {
        Map<String, Integer> stats = playerStatsCache.getIfPresent(uuid);
        if (stats != null) {
            stats.put(kothName, stats.getOrDefault(kothName, 0) + 1);
        }

        // También invalidamos la caché de top players ya que podrían cambiar
        topPlayersCache.invalidateAll();

        // Actualizamos o invalidamos la caché de totales para este KotH
        Integer currentTotal = kothTotalWinsCache.getIfPresent(kothName);
        if (currentTotal != null) {
            kothTotalWinsCache.put(kothName, currentTotal + 1);
        } else {
            kothTotalWinsCache.invalidate(kothName);
        }
    }

    /**
     * Establece la lista de top jugadores en la caché
     *
     * @param limit Límite usado para la consulta
     * @param players Lista de datos de jugadores
     */
    public void setTopPlayers(int limit, List<Map<String, Object>> players) {
        List<Map<String, Object>> copy = new ArrayList<>();
        // Creamos copias defensivas de cada mapa
        for (Map<String, Object> player : players) {
            copy.add(new HashMap<>(player));
        }
        topPlayersCache.put(limit, copy);
    }

    /**
     * Obtiene la lista de top jugadores desde la caché
     *
     * @param limit Límite usado para la consulta
     * @return Lista de datos de jugadores o null si no está en caché
     */
    public List<Map<String, Object>> getTopPlayers(int limit) {
        List<Map<String, Object>> players = topPlayersCache.getIfPresent(limit);
        if (players != null) {
            List<Map<String, Object>> copy = new ArrayList<>();
            // Crear copias defensivas de cada mapa
            for (Map<String, Object> player : players) {
                copy.add(new HashMap<>(player));
            }
            return copy;
        }
        return null;
    }

    /**
     * Invalida todas las cachés
     * Útil cuando se realiza una operación que afecta a muchos datos
     */
    public void invalidateAll() {
        playerNameCache.invalidateAll();
        playerStatsCache.invalidateAll();
        topPlayersCache.invalidateAll();
        kothTotalWinsCache.invalidateAll();
    }
}