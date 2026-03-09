package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.*;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
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

        if (now - last < 1000) return; // Every 1s = half heart per second

        int skyLight = player.getLocation().getBlock().getLightFromSky();
        if (skyLight >= 12) {
            double newHP = Math.min(player.getMaxHealth(), player.getHealth() + 1); // 1 HP = 0.5 heart
            player.setHealth(newHP);
            lastLightRegen.put(uuid, now);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 2, 0.2, 0.2, 0.2, 0.01);
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

                // Rapier state: true damage (armor-independent)
                if (mutation instanceof BypassMutation bypassMutation) {
                    if (bypassMutation.isRapierActive(attacker.getUniqueId())
                            && event.getEntity() instanceof LivingEntity rTarget) {
                        // Apply the base weapon damage as true damage
                        double baseDmg = event.getDamage();
                        event.setCancelled(true);
                        AbilityUtils.trueDamage(rTarget, baseDmg / 2.0, attacker);
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

        // True shot apex: full true damage (armor ignored)
        if (proj.hasMetadata("true_shot_apex") && event.getEntity() instanceof LivingEntity le) {
            AbilityUtils.trueDamage(le, 6, null); // 6 hearts true damage
            event.setCancelled(true);
        }

        // Toxic fang hit
        if (proj.hasMetadata("toxic_fang") && event.getEntity() instanceof LivingEntity le) {
            AbilityUtils.trueDamage(le, 4, null); // 4 hearts true damage
            event.setCancelled(true);
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
                        // Guided arrow always true damage
                        AbilityUtils.trueDamage(target, 4, shooter); // 4 hearts true
                        event.setCancelled(true);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Flame talon hit - explodes on impact
        if (proj.hasMetadata("flame_talon") && event.getEntity() instanceof LivingEntity le) {
            Location hitLoc = proj.getLocation();
            World hitWorld = proj.getWorld();

            // True damage + fire
            AbilityUtils.trueDamage(le, 4, null); // 4 hearts true damage
            le.setFireTicks(140); // 7s burn
            Vector dir = le.getLocation().toVector().subtract(hitLoc.toVector()).normalize();
            dir.setY(0.4);
            le.setVelocity(dir.multiply(0.7));

            // Explosion: AoE fire + damage in 3 block radius
            hitWorld.spawnParticle(Particle.EXPLOSION_EMITTER, hitLoc, 3, 0.5, 0.5, 0.5, 0.1);
            hitWorld.spawnParticle(Particle.FLAME, hitLoc, 40, 1.5, 1.5, 1.5, 0.15);
            hitWorld.playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);

            for (Entity e2 : hitWorld.getNearbyEntities(hitLoc, 3, 3, 3)) {
                if (e2 instanceof LivingEntity le2 && le2 != le) {
                    AbilityUtils.trueDamage(le2, 2, null);
                    le2.setFireTicks(80);
                }
            }

            event.setCancelled(true); // we handled damage manually
        }

        // Rock boulder hit - 3 hearts true damage
        if (proj.hasMetadata("rock_boulder") && event.getEntity() instanceof LivingEntity le) {
            AbilityUtils.trueDamage(le, 3, null);
            proj.getWorld().spawnParticle(Particle.BLOCK, proj.getLocation(), 15, 0.3, 0.3, 0.3, 0.1,
                    Material.STONE.createBlockData());
            proj.getWorld().playSound(proj.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.5f);
            event.setCancelled(true);
        }

        // Wind shuriken hit - 3 hearts true damage + launch 13 blocks up
        if (proj.hasMetadata("wind_shuriken") && event.getEntity() instanceof LivingEntity le) {
            AbilityUtils.trueDamage(le, 3, null);
            Vector up = new Vector(0, 2.0, 0); // ~13 blocks up
            le.setVelocity(up);
            le.setFallDistance(-100f); // cancel fall damage accumulation
            proj.getWorld().spawnParticle(Particle.CLOUD, proj.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            proj.getWorld().playSound(proj.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 2f);
            event.setCancelled(true); // we handled damage manually
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


    // ---- Air jump: intercept space-press while airborne via flight toggle ----
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        // Only intercept if they're trying to START flying (pressing space)
        if (!event.isFlying()) return;
        // Ignore creative/spectator
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        Mutation mutation = plugin.getMutationManager().getMutation(player);
        boolean consumed = false;

        if (mutation instanceof WindMutation windMut) {
            consumed = windMut.tryAirJump(player);
        } else if (mutation instanceof DragonborneMutation dragonMut) {
            consumed = dragonMut.tryAirJump(player);
        }

        if (consumed) {
            // Cancel the flight toggle so they don't actually start flying
            event.setCancelled(true);
            // Keep allowFlight true until they run out of jumps (managed inside tryAirJump)
        }
    }

    // Debounce set to prevent double-firing rotten flesh on hungry players
    private final Set<UUID> rottenFleshHandled = new HashSet<>();

    // Love: detect sneak+jump via move event (PlayerJumpEvent removed in 1.21.x)
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();

    @EventHandler
    public void onPlayerMoveJump(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        UUID uuid = player.getUniqueId();
        boolean onGround = player.isOnGround();
        boolean prev = wasOnGround.getOrDefault(uuid, true);

        if (prev && !onGround && player.getVelocity().getY() > 0.1) {
            Mutation mutation = plugin.getMutationManager().getMutation(player);
            if (mutation instanceof LoveMutation loveMut) {
                loveMut.triggerSoloFocus(player);
            }
        }

        wasOnGround.put(uuid, onGround);
    }

    // ---- Prevent fall damage after Wind Leap / Dragonborne jumps ----
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (mutation == null) return;

        // Wind: full fall damage immunity after Wind Leap
        if (mutation instanceof WindMutation windMut) {
            if (windMut.isFallDamageImmune(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }
        // Dragonborne: reduce fall damage
        if (mutation instanceof DragonborneMutation) {
            event.setDamage(Math.max(0, event.getDamage() - 3));
            if (event.getDamage() <= 0) event.setCancelled(true);
        }
    }

    // ---- Volley arrow: true damage on hit ----
    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.hasMetadata("volley_arrow")) return;
        if (!(event.getHitEntity() instanceof LivingEntity le)) return;
        // 3 hearts true damage per volley arrow
        AbilityUtils.trueDamage(le, 3, null);
        arrow.remove();
    }


    // ---- Mutation Orb: right-click to absorb ----
    @EventHandler(priority = EventPriority.HIGH)
    public void onOrbRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        var itemManager = plugin.getMutationManager().getItemManager();
        MutationType type = itemManager.getMutationType(held);
        if (type == null) return;

        event.setCancelled(true);

        if (plugin.getMutationManager().hasMutation(player)) {
            player.sendMessage("§cYou already have a mutation! Use §f/mutation withdraw §cfirst.");
            return;
        }

        // Absorb: remove 1 orb from hand and assign mutation
        held.setAmount(held.getAmount() - 1);
        plugin.getMutationManager().setMutation(player, type);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        player.sendMessage("§aYou absorbed the " + type.getDisplayName() + "§a!");
    }



    // ---- Debuff passive 1: reverse negative effects applied to player ----
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPotionEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == org.bukkit.event.entity.EntityPotionEffectEvent.Cause.PLUGIN) return;
        if (event.getAction() != org.bukkit.event.entity.EntityPotionEffectEvent.Action.ADDED) return;

        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (!(mutation instanceof DebuffMutation)) return;
        if (event.getNewEffect() == null) return;

        org.bukkit.potion.PotionEffect effect = event.getNewEffect();
        org.bukkit.potion.PotionEffectType reversed = DebuffMutation.getReversal(effect.getType());
        if (reversed != null && DebuffMutation.isNegative(effect.getType())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            reversed, effect.getDuration(), effect.getAmplifier(), false, true));
                    player.sendMessage("§5☠ Your effects have been reversed!");
                }
            }, 1L);
        }
    }

    // ---- Debuff Passive 2: Rotten flesh → golden apple effect ----
    // Two-handler approach:
    // 1. PlayerInteractEvent fires on every right-click regardless of hunger level.
    //    We apply our effects here and mark the debounce set.
    // 2. PlayerItemConsumeEvent fires when vanilla eating actually begins (hunger < 20).
    //    We cancel it so the player doesn't get vanilla rotten flesh nausea.

    @EventHandler(priority = EventPriority.HIGH)
    public void onRottenFleshRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != Material.ROTTEN_FLESH) return;

        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (!(mutation instanceof DebuffMutation debuffMut)) return;

        // Prevent double-apply (fires twice: HAND + OFF_HAND slots)
        UUID uuid = player.getUniqueId();
        if (rottenFleshHandled.contains(uuid)) return;
        rottenFleshHandled.add(uuid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> rottenFleshHandled.remove(uuid), 2L);

        // Cancel so vanilla eating animation doesn't start
        event.setCancelled(true);
        debuffMut.onConsumeRottenFlesh(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRottenFleshVanillaEat(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.ROTTEN_FLESH) return;
        Player player = event.getPlayer();
        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (!(mutation instanceof DebuffMutation)) return;
        // Cancel vanilla eating to prevent nausea and wrong stat changes
        event.setCancelled(true);
    }

    // ========== MUTATION DEATH TRANSFER ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player victim = event.getEntity();
        MutationType type = plugin.getMutationManager().getMutation(victim) != null
                ? plugin.getMutationManager().getMutation(victim).getType()
                : null;
        if (type == null) return;

        // Remove mutation from victim silently (suppress cooldown clears noise)
        plugin.getMutationManager().removeMutation(victim);

        // Create the orb item
        ItemStack orb = plugin.getMutationManager().getItemManager().createMutationItem(type);

        Player killer = victim.getKiller();

        if (killer != null && killer.isOnline()) {
            // Killed by a player
            if (!plugin.getMutationManager().hasMutation(killer)) {
                // Killer has no mutation — absorb it directly
                plugin.getMutationManager().setMutation(killer, type);
                killer.sendMessage("§6⚔ You absorbed §f" + victim.getName() + "§6's "
                        + type.getDisplayName() + " §6mutation!");
                victim.sendMessage("§c☠ Your " + type.getDisplayName()
                        + " §cwas taken by §f" + killer.getName() + "§c!");
                killer.getWorld().playSound(killer.getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            } else {
                // Killer already has a mutation — give orb item
                giveOrbOrDrop(killer, orb, victim.getLocation());
                killer.sendMessage("§6⚔ You took §f" + victim.getName() + "§6's "
                        + type.getDisplayName() + " §6mutation orb! §7(inventory full = dropped)");
                victim.sendMessage("§c☠ Your " + type.getDisplayName()
                        + " §cwas taken by §f" + killer.getName() + "§c!");
            }
        } else {
            // Natural death — drop orb at death location
            victim.getWorld().dropItemNaturally(victim.getLocation(), orb);
            victim.sendMessage("§c☠ Your " + type.getDisplayName()
                    + " §cmutation orb dropped at your death location!");
        }
    }

    /** Try to add orb to player inventory; drop at location if full */
    private void giveOrbOrDrop(Player player, ItemStack orb, org.bukkit.Location dropLoc) {
        var leftover = player.getInventory().addItem(orb);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(dropLoc, leftover.get(0));
        }
    }

    // ========== MUTATION ORB INDESTRUCTIBILITY ==========

    /** Prevent mutation orbs (as dropped item entities) from being destroyed by anything. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOrbItemDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Item itemEntity)) return;
        if (plugin.getMutationManager().getItemManager().isMutationItem(itemEntity.getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Prevent void-deletion of mutation orbs.
     *  Items are removed by the server when their tick age hits the despawn limit or fall below void.
     *  We catch EntityDamageEvent with VOID cause above, but also reschedule teleport if needed. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onOrbSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        org.bukkit.entity.Item itemEntity = event.getEntity();
        if (!plugin.getMutationManager().getItemManager().isMutationItem(itemEntity.getItemStack())) return;
        // Make the orb never despawn
        itemEntity.setPickupDelay(20);
        // Glowing so it's always visible
        itemEntity.setGlowing(true);
        // Tick-check to rescue from void
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!itemEntity.isValid()) { task.cancel(); return; }
            // If fallen below y = -64 (void), teleport back up to surface
            if (itemEntity.getLocation().getY() < -60) {
                org.bukkit.Location safe = itemEntity.getLocation().clone();
                safe.setY(itemEntity.getWorld().getHighestBlockYAt(safe) + 1);
                itemEntity.teleport(safe);
            }
        }, 10L, 10L);
    }

    /** Prevent mutation orbs from being burned in furnaces or destroyed by item-destroying events. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOrbExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.getDrops().removeIf(is -> plugin.getMutationManager().getItemManager().isMutationItem(is));
    }

    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player p) return p;
        }
        return null;
    }
}
