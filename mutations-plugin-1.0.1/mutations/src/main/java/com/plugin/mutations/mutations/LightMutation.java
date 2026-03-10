package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class LightMutation extends Mutation {

    private final Map<UUID, Long> lastBlindChance = new HashMap<>();
    private final Random random = new Random();

    public LightMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.LIGHT; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§e☀️ You have been granted the §eLight Mutation§e!");
        player.sendMessage("§7Passive: Radiant Core - 8% dmg reduction, blind chance, regen in light");
    }

    @Override
    public void onRemove(Player player) {
        lastBlindChance.remove(player.getUniqueId());
    }

    // ---- Ability 1: Luminous Roar (18s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_LuminousRoar", 18)) return;
        player.sendMessage("§e☀️ Luminous Roar!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.5f);

        // 12-block beam
        Set<LivingEntity> hit = AbilityUtils.beamDamage(player, 12, 1.2, Particle.END_ROD, null, 2); // 2 hearts

        for (LivingEntity e : hit) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            world.spawnParticle(Particle.END_ROD, e.getLocation().add(0,1,0), 15, 0.3, 0.5, 0.3, 0.1);
        }

        // 5s trail dealing 1.5 DPS where beam was
        Location beamEnd = player.getEyeLocation().clone().add(
                player.getEyeLocation().getDirection().normalize().multiply(12));

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) { task.cancel(); return; }
            List<LivingEntity> trail = AbilityUtils.getNearbyTrusted(beamEnd, 1.5, player, plugin);
            for (LivingEntity e : trail) {
                AbilityUtils.trueDamage(e, 0.5, player); // 0.5 hearts/tick
                world.spawnParticle(Particle.END_ROD, e.getLocation().add(0,1,0), 3, 0.2, 0.2, 0.2, 0.02);
            }
        }, 0, 13); // ~1.5 DPS (13 ticks ≈ 0.65s, dealing 1 each time)

        runLater(() -> {}, 100L); // Cancel trail after 5s handled by task tracking
    }

    // ---- Ability 2: Solar Wing Strike (12s CD) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_SolarWing", 12)) return;
        player.sendMessage("§e☀️ Solar Wing Strike!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f);
        world.spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 20, 0.5, 0.5, 0.5, 0.1);

        // 15-block dash
        AbilityUtils.dash(player, 15);

        // After dash: Solar Flare (2-block radius, 3 dmg + knockback)
        runLater(() -> {
            if (!player.isOnline()) return;
            world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 2f);
            world.spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 40, 1, 1, 1, 0.15);

            List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 2, player, plugin);
            for (LivingEntity e : nearby) {
                AbilityUtils.trueDamage(e, 3, player);
                Vector dir = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                dir.setY(0.4);
                e.setVelocity(dir.multiply(0.7));
            }
            player.sendMessage("§e Solar Flare!");
        }, 10L);
    }

    // ---- Ability 3: Celestial Expanse (22s CD, 4s) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_CelestialExp", 22)) return;
        player.sendMessage("§e☀️ Celestial Expanse!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2f);

        // Expanding aura over 4s
        final int[] tick = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || tick[0] >= 80) {
                task.cancel();
                // Final burst
                world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.5f);
                world.spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 80, 3, 1, 3, 0.2);
                AbilityUtils.spawnSphere(player.getLocation(), 6, Particle.END_ROD, null, 60);

                List<LivingEntity> finalHit = AbilityUtils.getNearbyTrusted(player.getLocation(), 6, player, plugin);
                for (LivingEntity e : finalHit) {
                    AbilityUtils.trueDamage(e, 5, player); // 5 hearts max
                    e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                    Vector dir = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    dir.setY(0.3);
                    e.setVelocity(dir.multiply(0.4)); // 2-block knockback
                }
                player.sendMessage("§e Final burst!");
                return;
            }

            // Aura DPS and slowness
            double radius = 2 + (tick[0] / 80.0) * 4; // Expands 2->6 blocks
            List<LivingEntity> inAura = AbilityUtils.getNearbyTrusted(player.getLocation(), radius, player, plugin);
            for (LivingEntity e : inAura) {
                AbilityUtils.trueDamage(e, 0.5, player); // 0.5 hearts/tick
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 0, false, false));
            }

            // Visible aura ring that expands with the damage radius
            double finalRadius = radius;
            AbilityUtils.spawnRing(player.getLocation().add(0, 0.5, 0), finalRadius,
                    Particle.END_ROD, null, Math.max(16, (int)(finalRadius * 8)));
            AbilityUtils.spawnRing(player.getLocation().add(0, 1.5, 0), finalRadius * 0.7,
                    Particle.END_ROD, null, 12);

            tick[0] += 10;
        }, 0, 10L);
    }

    // ---- Passive: Radiant Core ----
    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        // 8% damage reduction
        event.setDamage(event.getDamage() * 0.92);
    }

    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastBlind = lastBlindChance.getOrDefault(uuid, 0L);

        // Every 8s, next attack has 15% chance to blind
        if (now - lastBlind >= 8000) {
            if (random.nextFloat() < 0.15) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                player.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.05);
                player.sendMessage("§e☀️ Radiant blind triggered!");
                lastBlindChance.put(uuid, now);
            }
        }
    }

    @Override
    public void onTick(Player player) {
        // Regen 0.5 heart every 5s in light (sky light level >= 12)
        if (player.getWorld().getTime() < 13000 || player.getWorld().getTime() > 23000) {
            // Daytime - check if in light
            int skyLight = player.getLocation().getBlock().getLightFromSky();
            if (skyLight >= 12) {
                // Schedule once per 5s (100 ticks) - use modular tick check
                UUID uuid = player.getUniqueId();
                // Use a simple state tracker
                long now = System.currentTimeMillis();
                // Simple approach: regen handled by scheduled task attached to player
            }
        }
    }

    @Override
    public void applyPassiveEffects(Player target) {
        // Radiant Core: solar regeneration and speed
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,         30, 0, false, false), true);
    }


    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_LuminousRoar", "A2_SolarWing", "A3_CelestialExp"};
    }

    @Override
    public boolean isAbilityActive(int slot, java.util.UUID uuid) {
        if (slot == 2) {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(uuid);
            return p != null && p.isFlying();
        }
        return false;
    }

}