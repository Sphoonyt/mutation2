package com.plugin.mutations.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    // UUID -> (abilityKey -> expiryMillis)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public CooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isOnCooldown(UUID player, String ability) {
        Map<String, Long> map = cooldowns.get(player);
        if (map == null) return false;
        Long expiry = map.get(ability);
        if (expiry == null) return false;
        return System.currentTimeMillis() < expiry;
    }

    public long getRemainingSeconds(UUID player, String ability) {
        Map<String, Long> map = cooldowns.get(player);
        if (map == null) return 0;
        Long expiry = map.get(ability);
        if (expiry == null) return 0;
        long rem = expiry - System.currentTimeMillis();
        return rem <= 0 ? 0 : (rem / 1000) + 1;
    }

    public void setCooldown(UUID player, String ability, int seconds) {
        cooldowns.computeIfAbsent(player, k -> new HashMap<>())
                 .put(ability, System.currentTimeMillis() + (seconds * 1000L));
    }

    /** Clear ALL cooldowns for a player (admin /mutation clear command only) */
    public void clearCooldowns(UUID player) {
        cooldowns.remove(player);
        config.set(player.toString(), null);
        saveFile();
    }

    public void clearAbility(UUID player, String ability) {
        Map<String, Long> map = cooldowns.get(player);
        if (map != null) map.remove(ability);
    }

    /** Persist cooldowns to disk when a player quits (so they survive re-equip and relog) */
    public void savePlayer(UUID uuid) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null || map.isEmpty()) {
            config.set(uuid.toString(), null);
        } else {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : map.entrySet()) {
                // Only save unexpired cooldowns
                if (entry.getValue() > now) {
                    config.set(uuid + "." + entry.getKey(), entry.getValue());
                }
            }
        }
        saveFile();
    }

    /** Load cooldowns from disk when a player joins */
    public void loadPlayer(UUID uuid) {
        if (!config.contains(uuid.toString())) return;
        Map<String, Long> map = cooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        for (String key : config.getConfigurationSection(uuid.toString()).getKeys(false)) {
            long expiry = config.getLong(uuid + "." + key);
            if (expiry > now) { // only load if still active
                map.put(key, expiry);
            }
        }
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save cooldowns.yml: " + e.getMessage());
        }
    }
}
