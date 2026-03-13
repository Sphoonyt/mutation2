package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import com.plugin.mutations.utils.EquipmentPacketUtil;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class ShadowMutation extends Mutation {

    private final Set<UUID> dissipateActive      = new HashSet<>();
    private final Set<UUID> hideSeekActive       = new HashSet<>();
    private final Set<UUID> hideSeekUsedBackstab = new HashSet<>();

    public ShadowMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override public MutationType getType() { return MutationType.SHADOW; }

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
        restoreArmor(player);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ── Passive: potion invis + dark dust particles ───────────────────────────
    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, 40, 0, false, false), true);

        // While true invis is active, keep re-sending empty equipment every tick
        // so any items equipped/changed mid-ability are also hidden
        if (dissipateActive.contains(uuid) || hideSeekActive.contains(uuid)) {
            EquipmentPacketUtil.sendEmptyEquipment(player);
        }

        // Dark dust particles
        player.getWorld().spawnParticle(Particle.DUST,
                player.getLocation().add(0, 1, 0),
                3, 0.4, 0.6, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(20, 0, 35), 1.2f));
    }

    // ── True invis: invis potion + fake empty equipment via ProtocolLib ───────
    private void hideFromAll(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        EquipmentPacketUtil.sendEmptyEquipment(player);
    }

    private void showToAll(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        EquipmentPacketUtil.restoreEquipment(player);
    }

    private void restoreArmor(Player player) {
        EquipmentPacketUtil.restoreEquipment(player);
    }

    // ── A1: Dissipate (60s CD, 10s true invis + AoE blowback/blindness) ──────
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_Dissipate", 60)) return;

        UUID uuid = player.getUniqueId();
        dissipateActive.add(uuid);
        player.sendMessage("§8💨 Dissipate!");

        World world = player.getWorld();
        Location center = player.getLocation().clone();

        // AoE knockback + blindness
        for (LivingEntity e : AbilityUtils.getNearbyTrusted(center, 8, player, plugin)) {
            Vector dir = e.getLocation().toVector().subtract(center.toVector()).normalize();
            dir.setY(0.4);
            e.setVelocity(dir.multiply(1.6));
            e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
        }

        for (int r = 0; r < 80; r++) {
            double angle = Math.random() * Math.PI * 2;
            double dist  = Math.random() * 8;
            world.spawnParticle(Particle.SQUID_INK,
                    center.clone().add(Math.cos(angle)*dist, Math.random()*2, Math.sin(angle)*dist),
                    1, 0, 0, 0, 0);
        }
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);
        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 0.8f, 1.5f);

        hideFromAll(player);

        runLater(() -> {
            dissipateActive.remove(uuid);
            if (player.isOnline()) {
                showToAll(player);
                player.sendMessage("§8Dissipate faded.");
            }
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

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 254, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false));

        final org.bukkit.scheduler.BukkitTask[] holder = {null};
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (!target.isOnline()) { holder[0].cancel(); return; }
                target.setVelocity(new Vector(0, 0, 0));
            }
        }, 0L, 1L);

        runLater(() -> {
            holder[0].cancel();
            if (target.isOnline()) target.removePotionEffect(PotionEffectType.SLOWNESS);
        }, 30L);
    }

    // ── A3: Hide and Seek (150s CD, 10s true invis) ───────────────────────────
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_HideAndSeek", 150)) return;

        UUID uuid = player.getUniqueId();
        hideSeekActive.add(uuid);
        hideSeekUsedBackstab.remove(uuid);
        player.sendMessage("§8🌑 Hide and Seek! (10s)");

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        player.getWorld().spawnParticle(Particle.SQUID_INK,
                player.getLocation().add(0,1,0), 30, 0.3, 0.5, 0.3, 0.05);

        hideFromAll(player);

        // Regen 0.5 hearts per 10s
        final org.bukkit.scheduler.BukkitTask[] regenHolder = {null};
        regenHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || !hideSeekActive.contains(uuid)) {
                    regenHolder[0].cancel(); return;
                }
                double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(max, player.getHealth() + 1.0));
            }
        }, 200L, 200L);

        runLater(() -> {
            hideSeekActive.remove(uuid);
            hideSeekUsedBackstab.remove(uuid);
            if (regenHolder[0] != null) regenHolder[0].cancel();
            if (player.isOnline()) {
                showToAll(player);
                player.sendMessage("§8Hide and Seek ended.");
            }
        }, 200L);
    }

    // ── Damage hooks ─────────────────────────────────────────────────────────
    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        UUID uuid = player.getUniqueId();
        if (hideSeekActive.contains(uuid) && !hideSeekUsedBackstab.contains(uuid)) {
            AbilityUtils.trueDamage(target, event.getFinalDamage() / 2.0, player);
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
            hideSeekActive.remove(uuid);
            hideSeekUsedBackstab.remove(uuid);
            showToAll(player);
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
