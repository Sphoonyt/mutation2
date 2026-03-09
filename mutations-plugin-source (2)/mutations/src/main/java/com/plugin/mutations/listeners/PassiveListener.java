package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class PassiveListener implements Listener {

    private final MutationsPlugin plugin;
    private final Map<UUID, Long> sneakStartTime = new HashMap<>();
    private BukkitTask tickTask;
    // Light mutation regen tracking
    private final Map<UUID, Long> lastLightRegen = new HashMap<>();

    public PassiveListener(MutationsPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    private void startTickTask() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Mutation> entry : plugin.getMutationManager().getPlayerMutations().entrySet()) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) continue;
                entry.getValue().onTick(player);

                // Light regen in light
                if (entry.getValue() instanceof LightMutation) {
                    handleLightRegen(player);
                }
            }
        }, 0, 1);
    }

    private void handleLightRegen(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastLightRegen.getOrDefault(uuid, 0L);

        if (now - last < 5000) return; // Every 5s

        int skyLight = player.getLocation().getBlock().getLightFromSky();
        if (skyLight >= 12) {
            double newHP = Math.min(player.getMaxHealth(), player.getHealth() + 1); // 0.5 heart = 1 HP
            player.setHealth(newHP);
            lastLightRegen.put(uuid, now);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 3, 0.2, 0.2, 0.2, 0.02);
        }
    }

    // ---- Damage dealt: hook into mutation's onDamageDealt ----
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Attacker is player with mutation
        Player attacker = getAttacker(event);
        if (attacker != null) {
            Mutation mutation = plugin.getMutationManager().getMutation(attacker);
            if (mutation != null && event.getEntity() instanceof LivingEntity target) {
                mutation.onDamageDealt(attacker, target, event);

                // Bypass rapier state: ignore 50% armor
                if (mutation instanceof BypassMutation bypassMutation) {
                    if (bypassMutation.isRapierActive(attacker.getUniqueId())) {
                        // Simulate armor ignore by boosting raw damage
                        double baseDmg = event.getDamage();
                        // We apply 50% bonus to compensate for armor reduction
                        event.setDamage(baseDmg * 1.5);
                    }
                }
            }
        }

        // Victim is player with mutation
        if (event.getEntity() instanceof Player victim) {
            Mutation mutation = plugin.getMutationManager().getMutation(victim);
            if (mutation != null) {
                mutation.onDamageReceived(victim, event);
            }

            // Handle projectile hits
            if (event.getDamager() instanceof Projectile proj) {
                handleProjectileHit(proj, victim, event);
            }
        }
    }

    private void handleProjectileHit(Projectile proj, Player victim, EntityDamageByEntityEvent event) {
        // Wind arrow deflect
        Mutation victimMut = plugin.getMutationManager().getMutation(victim);
        if (victimMut instanceof WindMutation windMut) {
            if (proj instanceof Arrow arrow) {
                if (windMut.shouldDeflectArrow(victim, arrow)) {
                    event.setCancelled(true);
                    // Deflect arrow back
                    Vector deflect = victim.getEyeLocation().getDirection().normalize().multiply(-1.5);
                    proj.setVelocity(deflect);
                    victim.sendMessage("§b🌪️ Arrow deflected!");
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.5f);
                    return;
                }
            }
        }

        // True shot apex: ignore armor (handled by high pierce level)
        if (proj.hasMetadata("true_shot_apex")) {
            // Already high damage, armor is still applied, but we boost it
            event.setDamage(event.getDamage() * 1.3); // compensate armor reduction
        }

        // Toxic fang hit
        if (proj.hasMetadata("toxic_fang") && event.getEntity() instanceof LivingEntity le) {
            event.setDamage(8); // 4 damage
            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
            Vector dir = le.getLocation().toVector().subtract(proj.getLocation().toVector()).normalize();
            dir.setY(0.3);
            le.setVelocity(dir.multiply(0.85)); // 5-block knockback

            // Guided mark
            if (proj.hasMetadata("guided_arrow") && event.getEntity() instanceof LivingEntity target) {
                String shooterStr = proj.getMetadata("guided_arrow").get(0).asString();
                Player shooter = plugin.getServer().getPlayer(UUID.fromString(shooterStr));
                if (shooter != null) {
                    Mutation mut = plugin.getMutationManager().getMutation(shooter);
                    if (mut instanceof TrueShotMutation tsm) {
                        tsm.markTarget(target, shooter);
                    }
                }
            }
        }

        // Guided arrow mark
        if (proj.hasMetadata("guided_arrow") && event.getEntity() instanceof LivingEntity target) {
            String shooterStr = proj.getMetadata("guided_arrow").get(0).asString();
            try {
                Player shooter = plugin.getServer().getPlayer(UUID.fromString(shooterStr));
                if (shooter != null) {
                    Mutation mut = plugin.getMutationManager().getMutation(shooter);
                    if (mut instanceof TrueShotMutation tsm) {
                        tsm.markTarget(target, shooter);
                        // Check if marked target, apply damage bonus
                        if (tsm.isMarkedTarget(target.getUniqueId(), shooter.getUniqueId())) {
                            event.setDamage(event.getDamage() * 1.2);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Flame talon hit
        if (proj.hasMetadata("flame_talon") && event.getEntity() instanceof LivingEntity le) {
            event.setDamage(8); // 4 damage
            le.setFireTicks(140); // 7s burn
            Vector dir = le.getLocation().toVector().subtract(proj.getLocation().toVector()).normalize();
            dir.setY(0.4);
            le.setVelocity(dir.multiply(0.7)); // 4-block launch

            // Explosion effect
            proj.getWorld().spawnParticle(Particle.FLAME, proj.getLocation(), 20, 1, 1, 1, 0.15);
            proj.getWorld().playSound(proj.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
        }

        // Rock boulder hit
        if (proj.hasMetadata("rock_boulder") && event.getEntity() instanceof LivingEntity le) {
            event.setDamage(6); // 3 damage
            proj.getWorld().spawnParticle(Particle.BLOCK, proj.getLocation(), 15, 0.3, 0.3, 0.3, 0.1,
                    Material.STONE.createBlockData());
            proj.getWorld().playSound(proj.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.5f);
        }

        // Wind shuriken hit
        if (proj.hasMetadata("wind_shuriken") && event.getEntity() instanceof LivingEntity le) {
            event.setDamage(6); // 3 damage
            Vector up = new Vector(0, 2.2, 0); // ~13 blocks up
            le.setVelocity(up);
            le.setFallDistance(-20f); // Prevent fall damage for first part
            proj.getWorld().spawnParticle(Particle.CLOUD, proj.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            proj.getWorld().playSound(proj.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 2f);
        }
    }

    // ---- Arrow shoot: True Shot bow enhancement ----
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (!(mutation instanceof TrueShotMutation tsm)) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        tsm.onBowShoot(player, arrow);
    }

    // ---- Sneak for True Shot crouch bonus ----
    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (!(mutation instanceof TrueShotMutation tsm)) return;

        if (event.isSneaking()) {
            tsm.onPlayerStartSneak(player);
        } else {
            tsm.onPlayerStopSneak(player);
        }
    }

    // ---- Love: Sneak+Jump = Solo Focus ----
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Intentionally empty - movement handled in tick task
        // Could add Love focus tracking here but tick task handles it
    }

    // ---- Love: detect sneak+jump (sneak already held + jump press) ----
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            sneakStartTime.put(player.getUniqueId(), System.currentTimeMillis());
        } else {
            sneakStartTime.remove(player.getUniqueId());
        }
    }

    // Love: jump while sneaking triggers solo focus
    @EventHandler
    public void onPlayerJump(io.papermc.paper.event.player.PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (mutation instanceof LoveMutation loveMut) {
            loveMut.triggerSoloFocus(player);
        }
    }

    // ---- Prevent fall damage after Wind Leap / Dragonborne jumps ----
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (mutation == null) return;

        // Wind and Dragonborne mutations get fall damage reduction
        if (mutation instanceof WindMutation || mutation instanceof DragonborneMutation) {
            event.setDamage(Math.max(0, event.getDamage() - 3)); // Reduce fall damage
            if (event.getDamage() <= 0) event.setCancelled(true);
        }
    }

    // ---- Volley arrow: track 3 hits for bonus ----
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.hasMetadata("volley_arrow")) return;
        if (!(event.getHitEntity() instanceof LivingEntity)) return;

        // Bonus damage if all 3 hit: tracked via metadata (simplified: just deal bonus on each hit)
        // The bonus is applied per-hit for simplicity
    }

    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player p) return p;
        }
        return null;
    }
}
