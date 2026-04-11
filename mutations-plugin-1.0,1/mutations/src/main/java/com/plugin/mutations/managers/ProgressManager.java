package com.plugin.mutations.managers;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Tracks per-player progress toward compound advancements:
 *  - Mutation Master: obtained every non-dragonborne mutation at least once
 *  - Master of Damage: used a damaging ability from every non-dragonborne mutation
 */
public class ProgressManager {

    private final MutationsPlugin plugin;
    private final File file;
    private final YamlConfiguration config;

    // Non-dragonborne mutations required for Mutation Master
    private static final Set<MutationType> MASTER_MUTATIONS = Set.of(
            MutationType.WIND, MutationType.FROZEN, MutationType.ROCK,
            MutationType.HELLFIRE, MutationType.BLOOD_SOLDIER, MutationType.BYPASS,
            MutationType.DEBUFF, MutationType.WITCH, MutationType.SHADOW,
            MutationType.LIGHTNING, MutationType.NATURE, MutationType.LOVE,
            MutationType.TRUE_SHOT
    );

    // Mutation types that have damaging abilities (non-dragonborne)
    public static final Set<String> DAMAGE_ABILITY_KEYS = Set.of(
            "wind", "frozen", "bypass", "rock", "hellfire",
            "blood_soldier", "true_shot", "love", "debuff",
            "witch", "shadow", "lightning", "nature"
    );

    public ProgressManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "progress.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    // ── Mutation Master ───────────────────────────────────────────────────────

    /** Record that a player obtained a mutation. Returns true if they just completed Mutation Master. */
    public boolean recordMutationObtained(Player player, MutationType type) {
        if (!MASTER_MUTATIONS.contains(type)) return false;
        String key = player.getUniqueId() + ".obtained";
        List<String> list = config.getStringList(key);
        if (!list.contains(type.getId())) {
            list.add(type.getId());
            config.set(key, list);
            saveFile();
        }
        // Check completion
        return list.containsAll(MASTER_MUTATIONS.stream().map(MutationType::getId).toList());
    }

    public boolean hasMutationMaster(Player player) {
        List<String> list = config.getStringList(player.getUniqueId() + ".obtained");
        return list.containsAll(MASTER_MUTATIONS.stream().map(MutationType::getId).toList());
    }

    // ── Master of Damage ──────────────────────────────────────────────────────

    /** Record that a player used a damaging ability from the given mutation. Returns true if now complete. */
    public boolean recordDamageAbilityUsed(Player player, String mutationId) {
        if (!DAMAGE_ABILITY_KEYS.contains(mutationId)) return false;
        String key = player.getUniqueId() + ".damage_abilities";
        List<String> list = config.getStringList(key);
        if (!list.contains(mutationId)) {
            list.add(mutationId);
            config.set(key, list);
            saveFile();
        }
        return list.containsAll(DAMAGE_ABILITY_KEYS);
    }

    public boolean hasMasterOfDamage(Player player) {
        List<String> list = config.getStringList(player.getUniqueId() + ".damage_abilities");
        return list.containsAll(DAMAGE_ABILITY_KEYS);
    }

    private void saveFile() {
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save progress.yml: " + e.getMessage()); }
    }
}
