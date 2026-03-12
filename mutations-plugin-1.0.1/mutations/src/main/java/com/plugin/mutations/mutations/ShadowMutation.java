package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class ShadowMutation extends Mutation {

    private final Set<UUID> dissipateActive  = new HashSet<>();
    private final Set<UUID> hideSeekActive   = new HashSet<>();
    private final Set<UUID> hideSeekUsedBackstab = new HashSet<>();
    // track targets locked by Shadow Grab
    private final Map<UUID, UUID> grabbedTargets = new HashMap<>(); // source -> target

    public ShadowMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.SHADOW; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§8🖤 You have been granted the §8Shadow Mutation§8!");
        player.sendMessage("§8Passive: Shadow Lurker — Permanent invisibility.");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        dissipateActive.remove(uuid);
        hideSeekActive.remove(uuid);
        hideSeekUsedBackstab.remove(uuid);
        grabbedTargets.remove(uuid);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ── Passive: Shadow Lurker — perm invis ─────────────────────────────────
    @Override
    public void onTick(Player player) {
        // Maintain permanent invisibility unless Dissipate/HideAndSeek is handling it
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, 40, 0, false, false), true);
    }

    // ── A1: Dissipate (60s CD, 10s true invis + AoE blowback/blindness) ─────
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_Dissipate", 60)) return;

        UUID uuid = player.getUniqueId();
        dissipateActive.add(uuid);
        player.sendMessage("§8💨 Dissipate!");

        World world = player.getWorld();
        Location center = player.getLocation().clone();

        // True invisibility — suppress particles for duration
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 1, false, false));

        // AoE blowback + blindness
        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(center, 8, player, plugin);
        for (LivingEntity e : nearby) {
            Vector dir = e.getLocation().toVector().subtract(center.toVector()).normalize();
            dir.setY(0.4);
            e.setVelocity(dir.multiply(1.6));
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
        }

        // Suppress particles in AoE — spawn black smoke to mask visual
        for (int r = 0; r < 80; r++) {
            double angle = Math.random() * Math.PI * 2;
            double dist  = Math.random() * 8;
            Location pLoc = center.clone().add(
                    Math.cos(angle) * dist, Math.random() * 2, Math.sin(angle) * dist);
            world.spawnParticle(Particle.SQUID_INK, pLoc, 1, 0, 0, 0, 0);
        }
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);
        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT,    0.8f, 1.5f);

        runLater(() -> {
            dissipateActive.remove(uuid);
            if (player.isOnline()) player.sendMessage("§8Dissipate faded.");
        }, 200L);
    }

    // ── A2: Shadow Grab (22s CD) ─────────────────────────────────────────────
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_ShadowGrab", 22)) return;

        Player target = getNearestEnemy(player, 12);
        if (target == null) {
            player.sendMessage("§c No player found within 12 blocks!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A2_ShadowGrab");
            return;
        }

        player.sendMessage("§8🕷️ Shadow Grab on §f" + target.getName() + "§8!");
        target.sendMessage("§8🕷️ You've been grabbed by §f" + player.getName() + "§8!");

        World world = player.getWorld();
        world.playSound(target.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 0.6f);
        world.spawnParticle(Particle.SQUID_INK, target.getLocation().add(0,1,0), 20, 0.4, 0.5, 0.4, 0.02);

        // Freeze target for 1.5s using velocity zero + slowness 255
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 254, false, false));
        target.setVelocity(new Vector(0, 0, 0));

        // Tick-freeze: keep zeroing velocity for 1.5s
        UUID targetUUID = target.getUniqueId();
        final org.bukkit.scheduler.BukkitTask[] freezeTaskHolder = {null};
        freezeTaskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (!target.isOnline()) { freezeTaskHolder[0].cancel(); return; }
                target.setVelocity(new Vector(0, 0, 0));
            }
        }, 0L, 1L);
        runLater(() -> {
            if (freezeTaskHolder[0] != null) freezeTaskHolder[0].cancel();
            if (target.isOnline()) {
                target.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }, 30L);

        // Weakness for 4s
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false));
    }

    // ── A3: Hide and Seek (150s CD, 10s) ─────────────────────────────────────
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_HideAndSeek", 150)) return;

        UUID uuid = player.getUniqueId();
        hideSeekActive.add(uuid);
        hideSeekUsedBackstab.remove(uuid);
        player.sendMessage("§8🌑 Hide and Seek! (10s — true invisible, regen, double backstab)");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        world.spawnParticle(Particle.SQUID_INK, player.getLocation().add(0,1,0), 30, 0.3, 0.5, 0.3, 0.05);

        // True invis — no particles visible
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 220, 1, false, false));

        // Regen half a heart per 10s = 1 HP per 200 ticks
        plugin.getServer().getScheduler().runTaskTimer(plugin, regenTask -> {
            if (!player.isOnline() || !hideSeekActive.contains(uuid)) { regenTask.cancel(); return; }
            if (player.getHealth() < player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) {
                player.setHealth(Math.min(
                    player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                    player.getHealth() + 1.0));
            }
        }, 0, 200L);

        runLater(() -> {
            hideSeekActive.remove(uuid);
            hideSeekUsedBackstab.remove(uuid);
            if (player.isOnline()) player.sendMessage("§8Hide and Seek ended.");
        }, 200L);
    }

    // ── Damage hook: double damage while H&S active (once), cancel H&S on hit ─
    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        UUID uuid = player.getUniqueId();
        if (hideSeekActive.contains(uuid) && !hideSeekUsedBackstab.contains(uuid)) {
            // Backstab: double the damage (true damage on top)
            double baseDmg = event.getFinalDamage();
            AbilityUtils.trueDamage(target, baseDmg / 2.0, player); // add equal extra
            hideSeekUsedBackstab.add(uuid);
            player.sendMessage("§8⚔ Backstab!");
            player.getWorld().spawnParticle(Particle.SQUID_INK,
                    target.getLocation().add(0,1,0), 15, 0.3, 0.5, 0.3, 0.05);
        }
    }

    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        UUID uuid = player.getUniqueId();
        if (hideSeekActive.contains(uuid)) {
            // Getting hit cancels H&S
            hideSeekActive.remove(uuid);
            hideSeekUsedBackstab.remove(uuid);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.sendMessage("§c Hide and Seek broken!");
            player.getWorld().spawnParticle(Particle.SQUID_INK,
                    player.getLocation().add(0,1,0), 20, 0.4, 0.6, 0.4, 0.05);
        }
    }

    @Override
    public void applyPassiveEffects(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        30, 0, false, false), true);
    }

    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_Dissipate", "A2_ShadowGrab", "A3_HideAndSeek"};
    }

    @Override
    public boolean isAbilityActive(int slot, UUID uuid) {
        if (slot == 1) return dissipateActive.contains(uuid);
        if (slot == 3) return hideSeekActive.contains(uuid);
        return false;
    }

    private Player getNearestEnemy(Player source, double maxDist) {
        Player nearest = null;
        double minDist = maxDist * maxDist;
        for (Player p : source.getWorld().getPlayers()) {
            if (p == source) continue;
            if (plugin.getTrustManager().isTrusted(source.getUniqueId(), p.getUniqueId())) continue;
            double d = p.getLocation().distanceSquared(source.getLocation());
            if (d < minDist) { minDist = d; nearest = p; }
        }
        return nearest;
    }
}
