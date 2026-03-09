package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class WindMutation extends Mutation {

    // Air step tracking
    private final Map<UUID, Integer> airJumpsUsed = new HashMap<>();
    private final Map<UUID, Double> prevYVelocity = new HashMap<>();
    private final Map<UUID, Boolean> prevOnGround = new HashMap<>();
    private final Map<UUID, Long> lastAirJumpTime = new HashMap<>();

    private static final int MAX_AIR_JUMPS = 3;
    private static final double AIR_JUMP_COOLDOWN_MS = 1500;
    private BukkitTask tickTask;

    public WindMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.WIND; }

    @Override
    public void onAssign(Player player) {
        ItemStack a1 = AbilityUtils.createAbilityItem(Material.FEATHER,
                "§b[A1] Tornado Eruption", "AOE wind burst | 18s CD");
        ItemStack a2 = AbilityUtils.createAbilityItem(Material.SNOWBALL,
                "§b[A2] Wind Shuriken", "Throw wind charge | 16s CD");
        ItemStack a3 = AbilityUtils.createAbilityItem(Material.ELYTRA,
                "§b[A3] Wind Leap", "Launch up/forward | 8s CD");
        AbilityUtils.giveAbilityItems(player, a1, a2, a3);
        player.sendMessage("§b🌪️ You have been granted the §bWind Mutation§b!");
        player.sendMessage("§7Passive: Air Step (3 mid-air jumps) | Arrows bounce off you");

        UUID uuid = player.getUniqueId();
        airJumpsUsed.put(uuid, 0);
        prevYVelocity.put(uuid, 0.0);
        prevOnGround.put(uuid, true);
    }

    @Override
    public void onRemove(Player player) {
        AbilityUtils.removeAbilityItems(player);
        UUID uuid = player.getUniqueId();
        airJumpsUsed.remove(uuid);
        prevYVelocity.remove(uuid);
        prevOnGround.remove(uuid);
        lastAirJumpTime.remove(uuid);
    }

    // ---- Ability 1: Tornado Eruption (18s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_Tornado", 18)) return;
        player.sendMessage("§b🌪️ Tornado Eruption!");

        Location loc = player.getLocation();
        World world = player.getWorld();
        world.playSound(loc, Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);

        // Visual burst
        world.spawnParticle(Particle.CLOUD, loc.add(0, 1, 0), 60, 1.5, 1.5, 1.5, 0.1);
        world.spawnParticle(Particle.SWEEP_ATTACK, loc, 30, 1, 0.5, 1, 0.1);

        List<LivingEntity> nearby = AbilityUtils.getNearby(loc, 5, player);

        // 1s stun
        for (LivingEntity entity : nearby) {
            AbilityUtils.applyStun(entity, 20);
        }

        // After 1s: explosion push
        runLater(() -> {
            world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.7f);
            world.spawnParticle(Particle.EXPLOSION, player.getLocation(), 5, 1, 1, 1, 0.1);

            List<LivingEntity> targets = AbilityUtils.getNearby(player.getLocation(), 5, player);
            for (LivingEntity target : targets) {
                Vector dir = target.getLocation().toVector()
                        .subtract(player.getLocation().toVector()).normalize();
                dir.setY(0.4);
                target.setVelocity(dir.multiply(0.7)); // ~4 blocks
                target.damage(4, player); // 2 hearts
            }
        }, 20L);
    }

    // ---- Ability 2: Wind Shuriken (16s CD) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_Shuriken", 16)) return;
        player.sendMessage("§b💨 Wind Shuriken!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.5f);

        // Shoot a snowball with metadata
        Snowball sb = player.launchProjectile(Snowball.class);
        sb.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2.5));
        sb.setMetadata("wind_shuriken", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        sb.setMetadata("wind_shuriken_owner", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // Particle trail for shuriken
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (sb.isDead() || !sb.isValid()) { task.cancel(); return; }
            world.spawnParticle(Particle.CLOUD, sb.getLocation(), 3, 0.1, 0.1, 0.1, 0);
        }, 0, 1);
    }

    // ---- Ability 3: Wind Leap (8s CD) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_WindLeap", 8)) return;
        player.sendMessage("§b🌬️ Wind Leap!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f);
        world.spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.5, 0.1, 0.5, 0.1);

        // Launch 7 blocks up or forward depending on look angle
        Vector dir = player.getEyeLocation().getDirection();
        if (dir.getY() > 0.3) {
            // Looking up: go forward + up
            Vector vel = dir.normalize().multiply(1.2);
            vel.setY(vel.getY() * 1.5);
            player.setVelocity(vel);
        } else {
            // Looking flat/down: go up
            Vector vel = player.getVelocity();
            vel.setY(1.1); // ~7 blocks up
            player.setVelocity(vel);
        }

        player.setFallDistance(0f);
    }

    // ---- Passive: Air Step ----
    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        boolean onGround = player.isOnGround();
        boolean wasOnGround = prevOnGround.getOrDefault(uuid, true);
        double prevY = prevYVelocity.getOrDefault(uuid, 0.0);
        double currY = player.getVelocity().getY();

        // Just landed: reset air jump count
        if (!wasOnGround && onGround) {
            airJumpsUsed.put(uuid, 0);
        }

        // Detect mid-air jump: was in air, still in air, velocity went from ≤ 0 to > 0.2
        if (!wasOnGround && !onGround && prevY <= 0.05 && currY > 0.2) {
            int jumps = airJumpsUsed.getOrDefault(uuid, 0);
            long now = System.currentTimeMillis();
            long lastJump = lastAirJumpTime.getOrDefault(uuid, 0L);

            if (jumps < MAX_AIR_JUMPS && (now - lastJump) >= AIR_JUMP_COOLDOWN_MS) {
                airJumpsUsed.put(uuid, jumps + 1);
                lastAirJumpTime.put(uuid, now);

                // Boost the jump
                Vector vel = player.getVelocity();
                vel.setY(0.55);
                player.setVelocity(vel);
                player.setFallDistance(0f);

                World world = player.getWorld();
                world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 2f);
                world.spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
            }
        }

        prevOnGround.put(uuid, onGround);
        prevYVelocity.put(uuid, currY);
    }

    /** Called from PassiveListener when an arrow hits a wind mutation player */
    public boolean shouldDeflectArrow(Player player, Arrow arrow) {
        // Deflect if arrow does not have piercing
        int piercing = arrow.getPierceLevel();
        return piercing == 0;
    }

    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        // Arrow deflect handled in PassiveListener
    }
}
