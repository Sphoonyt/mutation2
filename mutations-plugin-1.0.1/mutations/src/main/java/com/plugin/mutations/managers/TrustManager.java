package com.plugin.mutations.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages per-player trust lists with YAML persistence.
 * If player A trusts player B, A's abilities will not affect B.
 * Trust data is saved to trust.yml on every change and loaded on startup.
 */
public class TrustManager {

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration config;

    // owner UUID -> set of trusted player UUIDs (in-memory cache)
    private final Map<UUID, Set<UUID>> trustMap = new HashMap<>();

    public TrustManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "trust.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    /** Add a trusted player and persist immediately */
    public void trust(UUID owner, UUID target) {
        trustMap.computeIfAbsent(owner, k -> new HashSet<>()).add(target);
        saveOwner(owner);
    }

    /** Remove a trusted player and persist immediately */
    public void untrust(UUID owner, UUID target) {
        Set<UUID> set = trustMap.get(owner);
        if (set != null) {
            set.remove(target);
            saveOwner(owner);
        }
    }

    /** Check if owner trusts target */
    public boolean isTrusted(UUID owner, UUID target) {
        Set<UUID> set = trustMap.get(owner);
        return set != null && set.contains(target);
    }

    /** Get all trusted UUIDs for an owner */
    public Set<UUID> getTrusted(UUID owner) {
        return trustMap.getOrDefault(owner, Collections.emptySet());
    }

    /** Clear all trust entries for an owner and persist immediately */
    public void clearTrust(UUID owner) {
        trustMap.remove(owner);
        config.set(owner.toString(), null);
        saveFile();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadAll() {
        for (String key : config.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                List<String> list = config.getStringList(key);
                Set<UUID> trusted = new HashSet<>();
                for (String s : list) {
                    try { trusted.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                }
                if (!trusted.isEmpty()) trustMap.put(owner, trusted);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveOwner(UUID owner) {
        Set<UUID> set = trustMap.get(owner);
        if (set == null || set.isEmpty()) {
            config.set(owner.toString(), null);
        } else {
            List<String> list = new ArrayList<>();
            for (UUID uuid : set) list.add(uuid.toString());
            config.set(owner.toString(), list);
        }
        saveFile();
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save trust.yml: " + e.getMessage());
        }
    }
}
