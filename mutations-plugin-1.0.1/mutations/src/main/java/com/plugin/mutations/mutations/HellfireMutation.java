package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class HellfireMutation extends Mutation {

    private final Map<UUID, Integer> whipStage = new HashMap<>();

    public HellfireMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.HELLFIRE; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§c🔥 You have been granted the §cHellfire Mutation§c!");
        player.sendMessage("§7Passive: Infernal Focus - Permanent fire resistance");

        // Give permanent fire resistance
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public void onRemove(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        whipStage.remove(player.getUniqueId());
    }

    // ---- Ability 1: Hellfire Rush (16s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_HellfireRush", 32)) return;
        player.sendMessage("§c🔥 Hellfire Rush!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);

        Vector baseDir = player.getEyeLocation().getDirection().normalize();
        baseDir.setY(0.15);

        // Zigzag dash: 3 short bursts in alternating diagonal directions
        for (int i = 0; i < 3; i++) {
            final int step = i;
            runLater(() -> {
                if (!player.isOnline()) return;
                Vector dir = baseDir.clone();
                // Alternate left/right zig
                Vector perp = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                if (step % 2 == 0) perp.multiply(-0.3); else perp.multiply(0.3);
                dir.add(perp).normalize().multiply(1.0);
                dir.setY(0.2);
                player.setVelocity(dir);
                player.setFireTicks(20);

                // Leave fire trail
                List<Block> fireBlocks = new ArrayList<>();
                Location trailLoc = player.getLocation();
                for (int b = 0; b < 3; b++) {
                    Block block = trailLoc.clone().subtract(dir.clone().normalize().multiply(b)).getBlock();
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.SOUL_FIRE);
                        fireBlocks.add(block);
                    }
                }

                // Damage nearby
                List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(player.getLocation(), 2, player, plugin);
                for (LivingEntity e : hit) {
                    AbilityUtils.trueDamage(e, 3, player); // 3 hearts
                    e.setFireTicks(60); // 3s burn
                }

                world.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation(), 15, 0.5, 0.3, 0.5, 0.1);
                AbilityUtils.spawnRing(player.getLocation().add(0, 0.5, 0), 2,
                        Particle.SOUL_FIRE_FLAME, null, 16);

                // Clear fire after 4s
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    for (Block fb : fireBlocks) {
                        if (fb.getType() == Material.SOUL_FIRE) fb.setType(Material.AIR);
                    }
                }, 80L);
            }, step * 8L);
        }
    }

    // ---- Ability 2: Flame Whip (15s CD) ----
    @Override
    public void activateAbility2(Player player) {
        // Multi-stage - first activation starts it, subsequent calls advance it
        UUID uuid = player.getUniqueId();
        int stage = whipStage.getOrDefault(uuid, 0);

        if (stage == 0) {
            if (!checkCooldown(player, "A2_FlameWhip", 30)) return;
            whipStage.put(uuid, 1);
            player.sendMessage("§c🔥 Flame Whip! [1/3] - Hit again to chain!");
        }

        if (stage < 3) {
            executeWhipHit(player, stage);
            whipStage.put(uuid, stage + 1);

            // Reset combo if not continued within 3s
            runLater(() -> {
                if (whipStage.getOrDefault(uuid, 0) == stage + 1) {
                    whipStage.put(uuid, 0);
                }
            }, 60L);
        }

        if (whipStage.getOrDefault(uuid, 0) >= 3) {
            whipStage.put(uuid, 0);
        }
    }

    private void executeWhipHit(Player player, int stage) {
        World world = player.getWorld();
        Location loc = player.getEyeLocation();
        Vector dir = loc.getDirection().normalize();

        // Hit enemies in front in a wide arc
        List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(player.getLocation().add(dir.clone().multiply(3)), 3, player, plugin);

        if (stage < 2) {
            // First 2 hits: 1.5 hearts each
            for (LivingEntity e : hit) {
                AbilityUtils.trueDamage(e, 1.5, player); // 1.5 hearts
                e.setFireTicks(40);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
            }
            world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.2f);
            player.sendMessage("§c🔥 Whip hit " + (stage + 1) + "! " + (2 - stage) + " more to final slam!");
        } else {
            // Final hit: 2.5 hearts + fire line
            for (LivingEntity e : hit) {
                AbilityUtils.trueDamage(e, 2.5, player); // 2.5 hearts
                e.setFireTicks(60);
            }

            // Fire line on ground
            for (int i = 1; i <= 8; i++) {
                Location fireLoc = player.getLocation().add(dir.clone().multiply(i));
                Block b = fireLoc.getBlock();
                Block above = fireLoc.clone().add(0, 1, 0).getBlock();
                if (b.getType().isSolid() && above.getType() == Material.AIR) {
                    above.setType(Material.SOUL_FIRE);
                    final Block fb = above;
                    runLater(() -> { if (fb.getType() == Material.SOUL_FIRE) fb.setType(Material.AIR); }, 80L);
                }
            }

            world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(dir.multiply(4)), 30, 2, 0.5, 2, 0.15);
            player.sendMessage("§c🔥 Final Flame Whip Slam!");
        }
    }

    // ---- Ability 3: Hell's Pull (20s CD) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_HellPull", 40)) return;
        player.sendMessage("§c🔥 Hell's Pull!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        AbilityUtils.spawnRing(player.getLocation().clone().add(0, 0.5, 0), 6, Particle.SOUL_FIRE_FLAME, null, 30);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 6, player, plugin);
        for (LivingEntity target : nearby) {
            // For each target: animate a hook particle that shoots from player to target
            // then curves back toward the player, pulling the target with it.
            Location playerLoc = player.getLocation().clone().add(0, 1, 0);
            Location targetLoc = target.getLocation().clone().add(0, 1, 0);

            Vector straight = targetLoc.toVector().subtract(playerLoc.toVector());
            double dist = straight.length();
            Vector toTarget = straight.clone().normalize();

            // Phase 1 (0-8 ticks): hook flies straight to target
            // Phase 2 (8-16 ticks): hook curves back toward player, dragging particles
            final LivingEntity finalTarget = target;
            final int totalTicks = 16;
            final int[] tick = {0};

            plugin.getServer().getScheduler().runTaskTimer(plugin, hookTask -> {
                if (tick[0] >= totalTicks || !finalTarget.isValid()) {
                    hookTask.cancel();
                    return;
                }

                double progress = (double) tick[0] / totalTicks;
                Location hookLoc;

                if (tick[0] < 8) {
                    // Straight phase: travel toward target
                    double t = tick[0] / 8.0;
                    hookLoc = playerLoc.clone().add(toTarget.clone().multiply(t * dist));
                } else {
                    // Curve back phase: arc from near target back to player with Y lift
                    double t = (tick[0] - 8) / 8.0; // 0..1
                    Vector fromTarget = playerLoc.toVector().subtract(targetLoc.toVector()).normalize();
                    hookLoc = targetLoc.clone().add(fromTarget.clone().multiply(t * dist));
                    // Add a vertical arc (sin curve)
                    hookLoc.add(0, Math.sin(t * Math.PI) * 1.5, 0);
                }

                world.spawnParticle(Particle.SOUL_FIRE_FLAME, hookLoc, 4, 0.05, 0.05, 0.05, 0.005);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, hookLoc, 2, 0.03, 0.03, 0.03, 0);

                // On final tick: yank the target and deal damage
                if (tick[0] == totalTicks - 1) {
                    Vector pull = playerLoc.toVector().subtract(finalTarget.getLocation().toVector()).normalize();
                    pull.setY(0.4);
                    finalTarget.setVelocity(pull.multiply(1.0));
                    AbilityUtils.trueDamage(finalTarget, 1.5, player); // 1.5 hearts
                    finalTarget.setFireTicks(80);
                    world.playSound(finalTarget.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 0.8f);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, finalTarget.getLocation().add(0,1,0), 20, 0.4, 0.4, 0.4, 0.08);
                }

                tick[0]++;
            }, 0L, 1L);
        }

        player.sendMessage("§c Hooked " + nearby.size() + " enemies!");
    }

    /** Returns true if the player is currently in an active whip combo (stage 1 or 2) */
    public boolean isWhipActive(java.util.UUID uuid) {
        return whipStage.getOrDefault(uuid, 0) > 0;
    }

    @Override
    public void onTick(Player player) {
        // Keep fire resistance refreshed
        if (!player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
        }
    }

    @Override
    public void applyPassiveEffects(Player target) {
        // Infernal Focus: fire immunity and infernal strength
        target.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,         30, 0, false, false), true);
    }


    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_HellfireRush", "A2_FlameWhip", "A3_HellPull"};
    }

    @Override
    public boolean isAbilityActive(int slot, java.util.UUID uuid) {
        // No long-duration active states in Hellfire
        return false;
    }

}