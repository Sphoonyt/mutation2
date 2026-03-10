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

public class TrueShotMutation extends Mutation {

    // Passive tracking
    private final Map<UUID, Long> lastTrueArrowTime = new HashMap<>();
    private final Set<UUID> markedTargets = new HashSet<>();
    private final Map<UUID, UUID> guidedMarkTarget = new HashMap<>(); // shooter -> target
    private final Map<UUID, Long> crouchStartTime = new HashMap<>();

    public TrueShotMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.TRUE_SHOT; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§a🏹 You have been granted the §aTrue Shot Mutation§a!");
        player.sendMessage("§7Passive: Perfect Aim - 10% faster arrows, no drop first 20 blocks");
        player.sendMessage("§7Crouch 1s → +20% next shot damage");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        lastTrueArrowTime.remove(uuid);
        guidedMarkTarget.remove(uuid);
        crouchStartTime.remove(uuid);
    }

    // ---- Ability 1: Penetrating Volley (16s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_PenVolley", 16)) return;
        player.sendMessage("§a🏹 Penetrating Volley!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.3f);

        Set<LivingEntity> totalHit = new HashSet<>();

        for (int i = 0; i < 3; i++) {
            runLater(() -> {
                if (!player.isOnline()) return;
                Arrow arrow = player.launchProjectile(Arrow.class);
                arrow.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(3.9));
                arrow.setPierceLevel(2);
                arrow.setDamage(6); // 3 damage per arrow
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setMetadata("volley_arrow", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

                world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.3f);
            }, i * 20L); // 20 ticks = 1 second between arrows so each can hit separately
        }
    }

    // ---- Ability 2: Guided Light Arrow (18s CD) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_GuidedLight", 18)) return;
        player.sendMessage("§a🏹 Guided Light Arrow!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 2f);

        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2.5));
        arrow.setDamage(8); // 4 damage
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setGlowing(true);
        arrow.setMetadata("guided_arrow", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // Homing behavior: nudge toward nearest entity
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (arrow.isDead() || !arrow.isValid()) { task.cancel(); return; }

            world.spawnParticle(Particle.END_ROD, arrow.getLocation(), 2, 0.05, 0.05, 0.05, 0);

            // Find nearest enemy
            LivingEntity nearest = null;
            double minDist = 15 * 15;
            for (Entity e : arrow.getNearbyEntities(15, 15, 15)) {
                if (e instanceof LivingEntity le && le != player) {
                    double dist = e.getLocation().distanceSquared(arrow.getLocation());
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = le;
                    }
                }
            }

            if (nearest != null) {
                // Aim at the entity's body center aggressively
                Vector toTarget = nearest.getLocation().add(0, nearest.getHeight() / 2, 0)
                        .toVector().subtract(arrow.getLocation().toVector());
                double dist = toTarget.length();
                Vector dir = toTarget.normalize();
                // Stronger correction when farther away, locks on close up
                double pull = Math.min(0.6, 0.15 + (dist * 0.04));
                Vector vel = arrow.getVelocity().multiply(0.8).add(dir.multiply(pull));
                vel = vel.normalize().multiply(2.5);
                arrow.setVelocity(vel);
            }
        }, 0, 2);
    }

    // ---- Ability 3: True Shot Apex (25s CD) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_TrueShotApex", 25)) return;
        player.sendMessage("§a🏹 True Shot Apex!");

        UUID uuid = player.getUniqueId();
        lastTrueArrowTime.put(uuid, System.currentTimeMillis());

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);
        world.spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 30, 0.3, 0.5, 0.3, 0.1);

        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(5));
        arrow.setPierceLevel(127); // Infinite piercing
        arrow.setDamage(12); // 6 true damage (handled as armor ignore)
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setGlowing(true);
        arrow.setMetadata("true_shot_apex", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        arrow.setFireTicks(0);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (arrow.isDead() || !arrow.isValid()) { task.cancel(); return; }
            world.spawnParticle(Particle.END_ROD, arrow.getLocation(), 3, 0.05, 0.05, 0.05, 0.02);
        }, 0, 1);
    }

    // ---- Passive: Perfect Aim Instinct ----
    // Handles arrow modifications - called by PassiveListener on EntityShootBowEvent

    public void onBowShoot(Player player, Arrow arrow) {
        UUID uuid = player.getUniqueId();

        // 10% faster arrows
        arrow.setVelocity(arrow.getVelocity().multiply(1.1));

        // Cancel gravity for first 20 blocks (simulated by delaying)
        // We'll nudge Y up for 15 ticks
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (arrow.isDead() || !arrow.isValid()) { task.cancel(); return; }
            double dist = arrow.getLocation().distance(player.getLocation());
            if (dist > 20) { task.cancel(); return; }
            Vector vel = arrow.getVelocity();
            vel.setY(vel.getY() + 0.02); // Counter gravity slightly
            arrow.setVelocity(vel);
        }, 0, 1);

        // Crouch damage bonus
        long crouchStart = crouchStartTime.getOrDefault(uuid, 0L);
        if (crouchStart > 0 && System.currentTimeMillis() - crouchStart >= 1000) {
            arrow.setDamage(arrow.getDamage() * 1.2);
            player.sendMessage("§a🏹 Crouch bonus activated! +20% damage");
            crouchStartTime.put(uuid, 0L);
        }

        // Guided mark bonus: if target is marked, +20% damage
        // Tracked via metadata on arrow

        // True Arrow passive: every 12s, arrow ignores armor + +2 true damage
        long lastTrue = lastTrueArrowTime.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - lastTrue >= 12000) {
            arrow.setDamage(arrow.getDamage() + 4); // +2 true damage (4 hp)
            arrow.setMetadata("true_arrow", new FixedMetadataValue(plugin, "true"));
            lastTrueArrowTime.put(uuid, System.currentTimeMillis());
            player.sendMessage("§a🏹 True Arrow ready!");
        }
    }

    public void onPlayerStartSneak(Player player) {
        crouchStartTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void onPlayerStopSneak(Player player) {
        crouchStartTime.put(player.getUniqueId(), 0L);
    }

    /** Mark a target as hit by guided arrow for 4s */
    public void markTarget(LivingEntity target, Player shooter) {
        UUID targetUuid = target.getUniqueId();
        guidedMarkTarget.put(shooter.getUniqueId(), targetUuid);
        markedTargets.add(targetUuid);

        target.setGlowing(true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0));
        shooter.sendMessage("§a🎯 Target marked! +20% ranged damage for 30 blocks");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            markedTargets.remove(targetUuid);
            if (target.isValid()) target.setGlowing(false);
        }, 80L);
    }

    public boolean isMarkedTarget(UUID targetUuid, UUID shooterUuid) {
        return markedTargets.contains(targetUuid)
                && targetUuid.equals(guidedMarkTarget.get(shooterUuid));
    }

    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        // Not melee focused - passive handled via bow events
    }

    @Override
    public void applyPassiveEffects(Player target) {
        // Archer's eye: jump boost for vantage and glowing perception
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,    30, 0, false, false), true);
    }

}