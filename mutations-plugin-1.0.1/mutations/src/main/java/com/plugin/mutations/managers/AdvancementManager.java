package com.plugin.mutations.managers;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

public class AdvancementManager {

    private final MutationsPlugin plugin;
    private static final String NAMESPACE = "mutations";

    // Track which mutations each player has ever obtained (for Mutation Master)
    // Persisted to avoid losing progress on relog — stored in a simple flat file
    private final Map<UUID, Set<MutationType>> obtainedMutations = new HashMap<>();
    private final File obtainedFile;

    // Mutations that count toward Mutation Master (Dragonborne excluded)
    private static final Set<MutationType> MASTER_POOL = Set.of(
        MutationType.WIND, MutationType.FROZEN, MutationType.ROCK, MutationType.HELLFIRE,
        MutationType.BLOOD_SOLDIER, MutationType.BYPASS, MutationType.DEBUFF, MutationType.WITCH,
        MutationType.SHADOW, MutationType.LIGHTNING, MutationType.NATURE, MutationType.LOVE,
        MutationType.TRUE_SHOT
    );

    // Track which damaging abilities each player has used (for Master of Damage)
    // Keys: "wind_a1", "frozen_a1", "bypass_a3", "rock_a1", "hellfire_a1", "hellfire_a2",
    //       "hellfire_a3", "light_a1", "light_a2", "light_a3", "love_a1", "love_a2",
    //       "shadow_a3", "lightning_a3", "nature_a2", "nature_a3", "blood_a1", "debuff_a1", "witch_a1"
    private final Map<UUID, Set<String>> usedAbilities = new HashMap<>();
    private static final Set<String> DAMAGE_ABILITY_POOL = Set.of(
        "wind_a1", "wind_a2",
        "frozen_a1",
        "bypass_a3",
        "rock_a1", "rock_a3",
        "hellfire_a1", "hellfire_a2", "hellfire_a3",
        "dragonborne_light_a1", "dragonborne_light_a2",
        "love_a1", "love_a2",
        "shadow_a3",
        "lightning_a3",
        "nature_a2", "nature_a3",
        "blood_a1",
        "debuff_a1"
    );

    public AdvancementManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.obtainedFile = new File(plugin.getDataFolder(), "obtained_mutations.dat");
        installDatapack();
        loadObtained();
    }

    // ── Datapack installation ─────────────────────────────────────────────────

    private void installDatapack() {
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        // 1.21.4+ uses "advancements" (plural)
        File advDir = new File(worldFolder, "datapacks/mutations/data/mutations/advancements/mutations");
        advDir.mkdirs();

        File mcmeta = new File(worldFolder, "datapacks/mutations/pack.mcmeta");
        writeResource("advancements/pack.mcmeta", mcmeta);

        String[] files = {
            "root", "mutation_master", "i_dont_think_so", "master_of_damage",
            "deeper_than_dark", "mutation_thief", "skill_issue", "graced_by_the_gods", "edge_walker"
        };
        for (String name : files) {
            writeResource("advancements/mutations/" + name + ".json", new File(advDir, name + ".json"));
        }
        plugin.getLogger().info("[Mutations] Advancements datapack installed.");
        // Schedule reload one tick after server startup so worlds are fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "datapack enable \"file/mutations\""),
        1L);
    }

    private void writeResource(String resourcePath, File dest) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) { plugin.getLogger().warning("Missing resource: " + resourcePath); return; }
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write " + dest.getName(), e);
        }
    }

    // ── Grant helpers ─────────────────────────────────────────────────────────

    public void grant(Player player, String advancementId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Key format: mutations:mutations/root → namespace=mutations, key=mutations/root
                NamespacedKey key = new NamespacedKey(NAMESPACE, NAMESPACE + "/" + advancementId);
                Advancement adv = Bukkit.getAdvancement(key);
                if (adv == null) {
                    plugin.getLogger().warning("[Mutations] Advancement not found: " + key + " (is the datapack loaded?)");
                    return;
                }
                AdvancementProgress prog = player.getAdvancementProgress(adv);
                for (String criterion : prog.getRemainingCriteria()) {
                    prog.awardCriteria(criterion);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to grant advancement " + advancementId, e);
            }
        });
    }

    // ── Mutation Master tracking ──────────────────────────────────────────────

    public void onMutationObtained(Player player, MutationType type) {
        grant(player, "root");
        if (!MASTER_POOL.contains(type)) return;
        UUID uuid = player.getUniqueId();
        Set<MutationType> set = obtainedMutations.computeIfAbsent(uuid, k -> new HashSet<>());
        set.add(type);
        saveObtained();
        if (set.containsAll(MASTER_POOL)) {
            grant(player, "mutation_master");
        }
    }

    // ── I Don't Think So!!! ───────────────────────────────────────────────────

    public void onCooldownHit(Player player) {
        grant(player, "i_dont_think_so");
    }

    // ── Master of Damage tracking ─────────────────────────────────────────────

    public void onDamagingAbilityUsed(Player player, String abilityKey) {
        UUID uuid = player.getUniqueId();
        Set<String> set = usedAbilities.computeIfAbsent(uuid, k -> new HashSet<>());
        set.add(abilityKey);
        if (set.containsAll(DAMAGE_ABILITY_POOL)) {
            grant(player, "master_of_damage");
        }
    }

    // ── Specific event grants ─────────────────────────────────────────────────

    public void onVoidDeath(Player player)         { grant(player, "deeper_than_dark"); }
    public void onMutationSteal(Player killer)     { grant(killer, "mutation_thief"); }
    public void onKilledByNonMutation(Player dead) { grant(dead, "skill_issue"); }
    public void onGraceJoin(Player player)         { grant(player, "graced_by_the_gods"); }
    public void onEdgeWalker(Player player)        { grant(player, "edge_walker"); }

    // ── Persistence for Mutation Master ──────────────────────────────────────

    private void loadObtained() {
        if (!obtainedFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(obtainedFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                try {
                    UUID uuid = UUID.fromString(parts[0]);
                    Set<MutationType> set = new HashSet<>();
                    for (String id : parts[1].split(",")) {
                        MutationType t = MutationType.fromId(id.trim());
                        if (t != null) set.add(t);
                    }
                    if (!set.isEmpty()) obtainedMutations.put(uuid, set);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load obtained_mutations.dat: " + e.getMessage());
        }
    }

    private void saveObtained() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(obtainedFile))) {
            for (Map.Entry<UUID, Set<MutationType>> entry : obtainedMutations.entrySet()) {
                StringBuilder sb = new StringBuilder(entry.getKey().toString()).append("=");
                StringJoiner sj = new StringJoiner(",");
                for (MutationType t : entry.getValue()) sj.add(t.getId());
                pw.println(sb.append(sj));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save obtained_mutations.dat: " + e.getMessage());
        }
    }
}
