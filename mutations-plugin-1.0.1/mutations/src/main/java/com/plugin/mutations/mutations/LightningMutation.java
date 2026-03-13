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
import org.bukkit.util.Vector;

import java.util.*;

public class LightningMutation extends Mutation {

    private final Random random = new Random();
    private final Set<UUID> arenaActive = new HashSet<>();
    private final Set<UUID> boltActive  = new HashSet<>();
    // Maps player UUID -> the phantom vehicle entity UUID
    private final Map<UUID, UUID> boltVehicle = new HashMap<>();

    public LightningMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override public MutationType getType() { return MutationType.LIGHTNING; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§e⚡ You have been granted the §eLightning Mutation§e!");
        player.sendMessage("§ePassive: Zeus's Disciple — 5% chance to strike lightning on hit.");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        arenaActive.remove(uuid);
        stopBolt(player);
    }

    // ── Passive: Zeus's Disciple — 5% lightning on hit ───────────────────────
    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (random.nextFloat() < 0.05f) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            player.sendMessage("§e⚡ Zeus's Disciple triggered!");
        }
    }

    // ── A1: Lightning Bolt Ride (180s CD, 10s) ────────────────────────────────
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_LightningRide", 180)) return;

        UUID uuid = player.getUniqueId();
        boltActive.add(uuid);
        player.sendMessage("§e⚡ Lightning Bolt Ride! (10s — take a hit to stop)");

        World world = player.getWorld();
        world.strikeLightningEffect(player.getLocation());
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);

        // Give the player flight so they can move freely in any direction
        boolean hadFlight = player.getAllowFlight();
        player.setAllowFlight(true);
        player.setFlying(true);

        final int[] tickCount = {0};
        final BukkitTask[] taskHolder = {null};
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || !boltActive.contains(uuid)) {
                    endBolt(player, hadFlight);
                    taskHolder[0].cancel();
                    return;
                }

                tickCount[0]++;
                if (tickCount[0] > 200) { // 10s
                    boltActive.remove(uuid);
                    endBolt(player, hadFlight);
                    taskHolder[0].cancel();
                    if (player.isOnline()) player.sendMessage("§e⚡ Lightning Bolt faded.");
                    return;
                }

                // Drive player in their look direction at 6 blocks/second (0.3/tick)
                Vector dir = player.getEyeLocation().getDirection().normalize().multiply(0.3);
                player.setVelocity(dir);
                player.setFlySpeed(0.0f); // block normal fly input, we control movement

                // Visual FX
                Location loc = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 8, 0.4, 0.6, 0.4, 0.3);
                world.spawnParticle(Particle.FIREWORK, loc, 3, 0.2, 0.3, 0.2, 0.1);

                // Real lightning strike at player location every 10 ticks (0.5s)
                if (tickCount[0] % 10 == 0) {
                    world.strikeLightning(player.getLocation());
                    world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.5f);
                }
            }
        }, 0L, 1L);
    }

    private void endBolt(Player player, boolean hadFlight) {
        player.setFlySpeed(0.1f); // restore default
        player.setFlying(false);
        if (!hadFlight) player.setAllowFlight(false);
        player.setFallDistance(0f);
    }

    // ── A2: Lightning Siphon (75s CD) — heals 5 hearts ───────────────────────
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_Siphon", 75)) return;
        player.sendMessage("§e⚡ Lightning Siphon!");

        World world = player.getWorld();
        world.strikeLightningEffect(player.getLocation());
        double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHp, player.getHealth() + 10.0));
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.5f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0,1,0),
                60, 0.4, 0.8, 0.4, 0.2);
    }

    // ── A3: Lightning Arena (90s CD, 10s) ────────────────────────────────────
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_Arena", 90)) return;

        UUID uuid = player.getUniqueId();
        arenaActive.add(uuid);
        player.sendMessage("§e⚡ Lightning Arena! (10s)");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !arenaActive.contains(uuid)) { task.cancel(); return; }
            Location current = player.getLocation().clone();
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 / 8) * i;
                Location strike = current.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
                world.strikeLightningEffect(strike);
                for (LivingEntity e : AbilityUtils.getNearbyTrusted(strike, 1.5, player, plugin)) {
                    AbilityUtils.trueDamage(e, 1.5, player);
                }
            }
            world.spawnParticle(Particle.ELECTRIC_SPARK, current.add(0,1,0), 30, 3, 0.5, 3, 0.3);
        }, 0, 20L);

        runLater(() -> {
            arenaActive.remove(uuid);
            if (player.isOnline()) {
                player.sendMessage("§e⚡ Lightning Arena ended.");
                player.getWorld().playSound(player.getLocation(),
                        Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.5f);
            }
        }, 200L);
    }

    // ── Damage hook: getting hit stops the bolt ride ──────────────────────────
    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        if (boltActive.remove(player.getUniqueId())) {
            endBolt(player, false);
            player.sendMessage("§c⚡ Lightning Bolt broken by hit!");
            player.getWorld().strikeLightningEffect(player.getLocation());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void stopBolt(Player player) {
        if (boltActive.remove(player.getUniqueId())) {
            endBolt(player, false);
        }
        boltVehicle.remove(player.getUniqueId());
    }

    private Entity getVehicle(UUID playerUUID, World world) {
        UUID vehicleUUID = boltVehicle.get(playerUUID);
        if (vehicleUUID == null) return null;
        return world.getEntities().stream()
                .filter(e -> e.getUniqueId().equals(vehicleUUID))
                .findFirst().orElse(null);
    }

    public boolean isBoltActive(UUID uuid) { return boltActive.contains(uuid); }

    @Override
    public void applyPassiveEffects(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 0, false, false), true);
    }

    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_LightningRide", "A2_Siphon", "A3_Arena"};
    }

    @Override
    public boolean isAbilityActive(int slot, UUID uuid) {
        if (slot == 1) return boltActive.contains(uuid);
        if (slot == 3) return arenaActive.contains(uuid);
        return false;
    }
}
