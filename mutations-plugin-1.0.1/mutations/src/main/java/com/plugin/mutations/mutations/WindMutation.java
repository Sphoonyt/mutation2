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
import org.bukkit.util.Vector;

import java.util.*;

public class WindMutation extends Mutation {

    private final Map<UUID, Integer> airJumpsUsed = new HashMap<>();
    private final Map<UUID, Double> prevYVelocity = new HashMap<>();
    private final Map<UUID, Boolean> prevOnGround = new HashMap<>();
    private final Map<UUID, Long> lastAirJumpTime = new HashMap<>();
    private final Set<UUID> fallDamageImmune = new HashSet<>();

    private static final int MAX_AIR_JUMPS = 3;
    private static final long AIR_JUMP_COOLDOWN_MS = 300; // reduced so it feels responsive

    public WindMutation(MutationsPlugin plugin) { super(plugin); }

    @Override public MutationType getType() { return MutationType.WIND; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§b🌪️ You have been granted the §bWind Mutation§b!");
        player.sendMessage("§7Passive: Air Step (3 mid-air jumps) | Arrows bounce off you");
        UUID uuid = player.getUniqueId();
        airJumpsUsed.put(uuid, 0);
        prevYVelocity.put(uuid, 0.0);
        prevOnGround.put(uuid, true);
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        airJumpsUsed.remove(uuid);
        prevYVelocity.remove(uuid);
        prevOnGround.remove(uuid);
        lastAirJumpTime.remove(uuid);
        fallDamageImmune.remove(uuid);
    }

    // ---- Ability 1: Tornado Eruption (18s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_Tornado", 18)) return;
        player.sendMessage("§b🌪️ Tornado Eruption!");

        Location loc = player.getLocation();
        World world = player.getWorld();
        world.playSound(loc, Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 60, 1.5, 1.5, 1.5, 0.1);
        world.spawnParticle(Particle.SWEEP_ATTACK, loc, 30, 1, 0.5, 1, 0.1);
        // Show stun radius ring
        AbilityUtils.spawnRing(loc.clone().add(0, 0.3, 0), 6, Particle.CLOUD, null, 32);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(loc, 6, player, plugin);
        for (LivingEntity entity : nearby) AbilityUtils.applyStun(entity, 20);

        // After 1s: strong explosion push
        runLater(() -> {
            world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.7f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation(), 5, 1, 1, 1, 0.1);
            AbilityUtils.spawnRing(player.getLocation().add(0, 0.3, 0), 6, Particle.CLOUD, null, 32);
            List<LivingEntity> targets = AbilityUtils.getNearbyTrusted(player.getLocation(), 6, player, plugin);
            for (LivingEntity target : targets) {
                Vector dir = target.getLocation().toVector()
                        .subtract(player.getLocation().toVector()).normalize();
                dir.setY(0.5);
                target.setVelocity(dir.multiply(2.5)); // strong knockback ~12+ blocks
                AbilityUtils.trueDamage(target, 2, player); // 2 hearts true damage
            }
        }, 20L);
    }

    // ---- Ability 2: Wind Shuriken (16s CD) — launches target 13 blocks up ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_Shuriken", 16)) return;
        player.sendMessage("§b💨 Wind Shuriken!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.5f);

        Snowball sb = player.launchProjectile(Snowball.class);
        sb.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2.5));
        sb.setMetadata("wind_shuriken", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (sb.isDead() || !sb.isValid()) { task.cancel(); return; }
            world.spawnParticle(Particle.CLOUD, sb.getLocation(), 3, 0.1, 0.1, 0.1, 0);
        }, 0, 1);
    }

    // ---- Ability 3: Wind Leap (8s CD) — no fall damage ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_WindLeap", 8)) return;
        player.sendMessage("§b🌬️ Wind Leap!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f);
        world.spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.5, 0.1, 0.5, 0.1);

        Vector dir = player.getEyeLocation().getDirection();
        Vector vel;
        if (dir.getY() > 0.3) {
            vel = dir.normalize().multiply(1.5);
            vel.setY(vel.getY() * 1.8);
        } else {
            vel = player.getVelocity().clone();
            vel.setY(1.35); // ~14 blocks up
        }
        player.setVelocity(vel);
        player.setFallDistance(0f);

        // Grant full fall damage immunity for 5s
        UUID uuid = player.getUniqueId();
        fallDamageImmune.add(uuid);
        runLater(() -> fallDamageImmune.remove(uuid), 100L);
    }

    public boolean isFallDamageImmune(UUID uuid) { return fallDamageImmune.contains(uuid); }

    // ---- Passive: Air Step ----
    // Uses space-bar detection via velocity change
    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        boolean onGround = player.isOnGround();
        boolean wasOnGround = prevOnGround.getOrDefault(uuid, true);
        double currY = player.getVelocity().getY();
        double prevY = prevYVelocity.getOrDefault(uuid, 0.0);

        // Landed: reset jumps
        if (onGround) {
            airJumpsUsed.put(uuid, 0);
        }

        // Detect jump input while airborne:
        // velocity Y spikes upward (player pressed space) while already in air
        if (!onGround && !wasOnGround && prevY < 0.1 && currY > 0.35) {
            int jumps = airJumpsUsed.getOrDefault(uuid, 0);
            long now = System.currentTimeMillis();
            long lastJump = lastAirJumpTime.getOrDefault(uuid, 0L);

            if (jumps < MAX_AIR_JUMPS && (now - lastJump) >= AIR_JUMP_COOLDOWN_MS) {
                airJumpsUsed.put(uuid, jumps + 1);
                lastAirJumpTime.put(uuid, now);

                Vector vel = player.getVelocity();
                vel.setY(0.65); // consistent jump height
                player.setVelocity(vel);
                player.setFallDistance(0f);

                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 2f);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
            }
        }

        prevOnGround.put(uuid, onGround);
        prevYVelocity.put(uuid, currY);
    }

    public boolean shouldDeflectArrow(Player player, Arrow arrow) {
        return arrow.getPierceLevel() == 0;
    }

    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {}
}
