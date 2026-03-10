package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class WindMutation extends Mutation {

    // Track remaining air jumps per player
    private final Map<UUID, Integer> airJumpsLeft = new HashMap<>();
    private final Set<UUID> fallDamageImmune = new HashSet<>();
    private final Set<UUID> airJumpFallImmune = new HashSet<>();

    public static final int MAX_AIR_JUMPS = 3;

    public WindMutation(MutationsPlugin plugin) { super(plugin); }

    @Override public MutationType getType() { return MutationType.WIND; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§b🌪️ You have been granted the §bWind Mutation§b!");
        player.sendMessage("§7Passive: Air Step (3 mid-air jumps) | Arrows bounce off you");
        resetJumps(player);
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        airJumpsLeft.remove(uuid);
        fallDamageImmune.remove(uuid);
        // Ensure flight is disabled on removal
        if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
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
        AbilityUtils.spawnRing(loc.clone().add(0, 0.3, 0), 6, Particle.CLOUD, null, 32);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(loc, 6, player, plugin);
        for (LivingEntity entity : nearby) AbilityUtils.applyStun(entity, 20);

        runLater(() -> {
            world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.7f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation(), 5, 1, 1, 1, 0.1);
            AbilityUtils.spawnRing(player.getLocation().clone().add(0, 0.3, 0), 6, Particle.CLOUD, null, 32);
            List<LivingEntity> targets = AbilityUtils.getNearbyTrusted(player.getLocation(), 6, player, plugin);
            for (LivingEntity target : targets) {
                Vector dir = target.getLocation().toVector()
                        .subtract(player.getLocation().toVector()).normalize();
                dir.setY(0.5);
                target.setVelocity(dir.multiply(2.5));
                AbilityUtils.trueDamage(target, 1.5, player); // 1.5 hearts AoE
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

        Snowball sb = player.launchProjectile(Snowball.class);
        sb.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2.5));
        sb.setMetadata("wind_shuriken", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

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

        Vector dir = player.getEyeLocation().getDirection();
        Vector vel;
        if (dir.getY() > 0.3) {
            vel = dir.normalize().multiply(1.5);
            vel.setY(vel.getY() * 1.8);
        } else {
            vel = player.getVelocity().clone();
            vel.setY(1.35);
        }
        player.setVelocity(vel);
        player.setFallDistance(0f);

        UUID uuid = player.getUniqueId();
        fallDamageImmune.add(uuid);
        runLater(() -> fallDamageImmune.remove(uuid), 100L);
    }

    public boolean isFallDamageImmune(UUID uuid) { return fallDamageImmune.contains(uuid) || airJumpFallImmune.contains(uuid); }

    // ---- Air Step passive ----
    // Uses the allowFlight trick: when airborne with jumps left, enable flight so
    // pressing space fires PlayerToggleFlightEvent which we intercept in PassiveListener.
    @Override
    public void onTick(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        UUID uuid = player.getUniqueId();

        if (player.isOnGround()) {
            // Landed — reset jumps and disable flight ability
            airJumpsLeft.put(uuid, MAX_AIR_JUMPS);
            airJumpFallImmune.remove(uuid);
            if (player.getAllowFlight() && !player.isFlying()) {
                player.setAllowFlight(false);
            }
        } else {
            // Airborne — enable allowFlight if we still have jumps so space can be detected
            int left = airJumpsLeft.getOrDefault(uuid, 0);
            if (left > 0 && !player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        }
    }

    /**
     * Called by PassiveListener's PlayerToggleFlightEvent handler.
     * Returns true if we consumed a jump.
     */
    public boolean tryAirJump(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (player.isOnGround()) return false;

        UUID uuid = player.getUniqueId();
        int left = airJumpsLeft.getOrDefault(uuid, 0);
        if (left <= 0) return false;

        airJumpsLeft.put(uuid, left - 1);

        // 2 blocks up + 1 block forward in look direction
        Vector lookFlat = player.getEyeLocation().getDirection().clone();
        lookFlat.setY(0);
        if (lookFlat.lengthSquared() > 0) lookFlat.normalize().multiply(0.13);
        Vector vel = lookFlat;
        vel.setY(0.55); // ~2 blocks up
        player.setVelocity(vel);
        player.setFallDistance(0f);
        airJumpFallImmune.add(player.getUniqueId());
        runLater(() -> airJumpFallImmune.remove(player.getUniqueId()), 200L); // immune for 10s

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.8f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.3, 0.1, 0.3, 0.05);
        player.sendMessage("§b🌪️ Air Step! §7(" + (left - 1) + " jumps left)");

        // Keep allowFlight enabled only if jumps remain
        if (left - 1 <= 0) {
            player.setAllowFlight(false);
        }
        return true;
    }

    private void resetJumps(Player player) {
        airJumpsLeft.put(player.getUniqueId(), MAX_AIR_JUMPS);
    }

    public boolean shouldDeflectArrow(Player player, Arrow arrow) {
        return arrow.getPierceLevel() == 0;
    }

    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {}

    @Override
    public void applyPassiveEffects(Player target) {
        // Air Step: agility and jumping power
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 0, false, false), true);
    }


    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_Tornado", "A2_Shuriken", "A3_WindLeap"};
    }

    @Override
    public boolean isAbilityActive(int slot, java.util.UUID uuid) {
        // Wind Leap (A3) active = fall damage immune
        if (slot == 3) return fallDamageImmune.contains(uuid) || airJumpFallImmune.contains(uuid);
        return false;
    }

}