package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class NatureMutation extends Mutation {

    private final Map<UUID, Double> damageAccumulated = new HashMap<>();
    private final Set<UUID> violentNatureActive = new HashSet<>();
    private final Set<UUID> graspedPlayers = new HashSet<>(); // cannot eat or throw potions

    public NatureMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.NATURE; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§2🌿 You have been granted the §2Nature Mutation§2!");
        player.sendMessage("§2Passive: Nature's Blessing — Regen II for 3s after every 15 damage taken.");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        damageAccumulated.remove(uuid);
        violentNatureActive.remove(uuid);
        graspedPlayers.remove(uuid);
    }

    // ── Passive: Nature's Blessing — accumulate damage, regen on threshold ───
    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        UUID uuid = player.getUniqueId();
        double acc = damageAccumulated.getOrDefault(uuid, 0.0) + event.getFinalDamage();

        if (acc >= 15.0) {
            damageAccumulated.put(uuid, acc - 15.0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, false, false));
            player.sendMessage("§2🌿 Nature's Blessing activated!");
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    player.getLocation().add(0,1,0), 10, 0.4, 0.5, 0.4, 0.02);
        } else {
            damageAccumulated.put(uuid, acc);
        }
    }

    // ── A1: Nature's Grasp (22s CD) — stun 2 enemies, stop healing 5s ───────
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_NaturesGrasp", 22)) return;
        player.sendMessage("§2🌿 Nature's Grasp!");

        World world = player.getWorld();
        List<Player> targets = getNearestEnemies(player, 15, 2);

        if (targets.isEmpty()) {
            player.sendMessage("§c No targets found!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A1_NaturesGrasp");
            return;
        }

        for (Player target : targets) {
            UUID targetUUID = target.getUniqueId();

            // Stun
            AbilityUtils.applyStun(target, 30);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 254, false, false));

            // Suppress healing effects
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, false));
            target.removePotionEffect(PotionEffectType.REGENERATION);

            // Block eating and potion use
            graspedPlayers.add(targetUUID);

            // Keep cancelling regen for 5s
            final org.bukkit.scheduler.BukkitTask[] regenTask = {null};
            regenTask[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override public void run() {
                    if (!target.isOnline()) { regenTask[0].cancel(); return; }
                    target.removePotionEffect(PotionEffectType.REGENERATION);
                }
            }, 0L, 5L);

            runLater(() -> {
                regenTask[0].cancel();
                graspedPlayers.remove(targetUUID);
                if (target.isOnline()) target.sendMessage("§2🌿 Nature's Grasp released.");
            }, 100L);

            target.sendMessage("§2🌿 You've been grasped by " + player.getName() + "! Healing & eating suppressed for 5s!");
            world.spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0,1,0), 15, 0.4, 0.5, 0.4, 0.02);
            world.playSound(target.getLocation(), Sound.BLOCK_GRASS_BREAK, 1.2f, 0.6f);
        }

        world.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_BREAK, 1.5f, 0.5f);
    }

    // ── A2: Trunk Slam (90s CD) — 4 hearts + Slowness I for 4s ──────────────
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_TrunkSlam", 90)) return;
        player.sendMessage("§2🌳 Trunk Slam!");

        Player target = getNearestEnemy(player, 8);
        if (target == null) {
            player.sendMessage("§c No target within 8 blocks!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A2_TrunkSlam");
            return;
        }

        AbilityUtils.trueDamage(target, 4, player, "§2" + (target instanceof Player tp ? tp.getName() : target.getName()) + " §7was slammed into the earth by §2" + player.getName() + "§7's Trunk Slam");
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, false, false));
        target.sendMessage("§2🌳 You were slammed by a tree trunk from " + player.getName() + "!");

        World world = player.getWorld();
        world.spawnParticle(Particle.BLOCK, target.getLocation().add(0,1,0), 30,
                0.5, 0.5, 0.5, 0.1, Material.OAK_LOG.createBlockData());
        world.spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0,1,0), 12, 0.5, 0.5, 0.5, 0.05);
        world.playSound(target.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.5f, 0.5f);
        world.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
    }

    // ── A3: Violent Nature (75s CD, 10s) — 0.5♥/tick leaf swarm ─────────────
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_ViolentNature", 75)) return;

        Player target = getNearestEnemy(player, 20);
        if (target == null) {
            player.sendMessage("§c No target found within 20 blocks!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A3_ViolentNature");
            return;
        }

        UUID targetUUID = target.getUniqueId();
        UUID sourceUUID = player.getUniqueId();
        violentNatureActive.add(sourceUUID);

        player.sendMessage("§2🌿 Violent Nature on §f" + target.getName() + "§2! (10s)");
        target.sendMessage("§2🌿 Violent Nature is tearing through you!");

        World world = player.getWorld();

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !violentNatureActive.contains(sourceUUID)) { task.cancel(); return; }
            Player t = plugin.getServer().getPlayer(targetUUID);
            if (t == null || !t.isOnline()) { task.cancel(); violentNatureActive.remove(sourceUUID); return; }

            // 0.5 hearts true damage per tick (20 ticks/s → 10 hearts total over 10s)
            AbilityUtils.trueDamage(t, 0.5, player, "§2" + (t instanceof Player tp ? tp.getName() : t.getName()) + " §7was consumed by §2" + player.getName() + "§7's Violent Nature");

            // Leaf particles on target
            world.spawnParticle(Particle.CHERRY_LEAVES, t.getLocation().add(0,1,0),
                    6, 0.4, 0.6, 0.4, 0.05);

        }, 0, 20L); // every tick (1s intervals)

        runLater(() -> {
            violentNatureActive.remove(sourceUUID);
            if (player.isOnline()) player.sendMessage("§2Violent Nature ended.");
        }, 200L);
    }

    @Override
    public void applyPassiveEffects(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,   30, 0, false, false), true);
    }

    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_NaturesGrasp", "A2_TrunkSlam", "A3_ViolentNature"};
    }

    @Override
    public boolean isAbilityActive(int slot, UUID uuid) {
        if (slot == 3) return violentNatureActive.contains(uuid);
        return false;
    }

    public boolean isGrasped(UUID uuid) {
        return graspedPlayers.contains(uuid);
    }

    private Player getNearestEnemy(Player source, double maxDist) {
        Player nearest = null; double minDist = maxDist * maxDist;
        for (Player p : source.getWorld().getPlayers()) {
            if (p == source) continue;
            if (plugin.getTrustManager().isTrusted(source.getUniqueId(), p.getUniqueId())) continue;
            double d = p.getLocation().distanceSquared(source.getLocation());
            if (d < minDist) { minDist = d; nearest = p; }
        }
        return nearest;
    }

    private List<Player> getNearestEnemies(Player source, double maxDist, int limit) {
        List<Player> candidates = new ArrayList<>();
        for (Player p : source.getWorld().getPlayers()) {
            if (p == source) continue;
            if (plugin.getTrustManager().isTrusted(source.getUniqueId(), p.getUniqueId())) continue;
            if (p.getLocation().distanceSquared(source.getLocation()) <= maxDist * maxDist) candidates.add(p);
        }
        candidates.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(source.getLocation())));
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }
}
