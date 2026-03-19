package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class LoveMutation extends Mutation {

    // Devotion tracking
    private final Map<UUID, Long> devotionExpiry = new HashMap<>();
    private final Map<UUID, Long> lastLookAt = new HashMap<>();
    private final Set<UUID> devotionActive = new HashSet<>();
    private final Set<UUID> overdrive = new HashSet<>();

    // Charm tracking
    private final Set<UUID> charmed = new HashSet<>();

    public LoveMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.LOVE; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§d💖 You have been granted the §dLove Mutation§d!");
        player.sendMessage("§7Passive: Devotion Boost - Focus ally 1s for Devotion | Sneak+Jump = solo focus");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        devotionExpiry.remove(uuid);
        lastLookAt.remove(uuid);
        devotionActive.remove(uuid);
        overdrive.remove(uuid);
        AbilityUtils.resetMaxHearts(player);
    }

    // ---- Ability 1: Heart Pulse Burst (18s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_HeartPulse", 18)) return;
        player.sendMessage("§d💖 Heart Pulse Burst!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 0.5f);
        world.spawnParticle(Particle.HEART, player.getLocation().add(0,1,0), 20, 2, 1, 2, 0.1);
        AbilityUtils.spawnRing(player.getLocation().add(0, 0.3, 0), 4,
                Particle.DUST, org.bukkit.Color.fromRGB(255, 100, 200), 24);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 4, player, plugin);
        for (LivingEntity e : nearby) {
            AbilityUtils.trueDamage(e, 3, player, "§d" + (e instanceof Player ep ? ep.getName() : e.getName()) + " §7had their heart burst by §d" + player.getName() + "§7's Heart Pulse");
            // Charm: -15% speed + damage equivalent (weakness + slowness)
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0));
            charmed.add(e.getUniqueId());
            world.spawnParticle(Particle.HEART, e.getLocation().add(0,1,0), 5, 0.3, 0.5, 0.3, 0.05);

            // Remove charm after 2s
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> charmed.remove(e.getUniqueId()), 40L);
        }
    }

    // ---- Ability 2: Adoration Beam (16s CD) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_AdorationBeam", 16)) return;

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1f, 0.5f);

        boolean devActive = isDevotionActive(player);
        double hearts = devActive ? 5.0 : 3.0;

        if (devActive) {
            player.sendMessage("§d💖 Adoration Beam! §5[Devotion] §d5 hearts!");
        } else {
            player.sendMessage("§d💖 Adoration Beam! 3 hearts");
        }

        Set<LivingEntity> hit = AbilityUtils.beamDamage(player, 10, 1.2,
                Particle.DUST, Color.fromRGB(255, 100, 200), hearts,
                "§d%victim% §7was pierced by §d" + player.getName() + "§7's Adoration Beam");

        for (LivingEntity e : hit) {
            world.spawnParticle(Particle.HEART,   e.getLocation().add(0,1,0), 12, 0.4, 0.6, 0.4, 0.1);
            world.spawnParticle(Particle.DUST, e.getLocation().add(0,1,0), 15, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(Color.fromRGB(255,150,200), 1.2f));
        }
    }

    // ---- Ability 3: Love Overdrive (45s CD, 10s) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_LoveOverdrive", 45)) return;
        player.sendMessage("§d💖 Love Overdrive!");

        UUID uuid = player.getUniqueId();
        overdrive.add(uuid);

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        world.spawnParticle(Particle.HEART, player.getLocation().add(0,1,0), 40, 2, 2, 2, 0.1);
        AbilityUtils.spawnSphere(player.getLocation(), 3, Particle.DUST,
                org.bukkit.Color.fromRGB(255, 50, 150), 32);

        // Activation pulse
        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 3, player, plugin);
        for (LivingEntity e : nearby) {
            AbilityUtils.trueDamage(e, 1, player, "§d" + (e instanceof Player ep ? ep.getName() : e.getName()) + " §7was overwhelmed by §d" + player.getName() + "§7's Devotion");
        }

        // Speed II + raise max health by 2 hearts temporarily
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1)); // Speed II
        AbilityUtils.addMaxHearts(player, 2); // +2 real hearts via max health attribute
        player.sendMessage("§d +30% Speed, +20% Damage, +2 Real Hearts for 10s!");

        runLater(() -> {
            overdrive.remove(uuid);
            if (player.isOnline()) {
                AbilityUtils.resetMaxHearts(player); // restore to 10 hearts baseline
                player.sendMessage("§d Love Overdrive ended.");
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }, 200L);
    }

    // ---- Passive: Devotion Boost ----
    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        World world = player.getWorld();

        // Check if looking at an ally for 1s continuously
        Player focused = getLookedAtAlly(player);
        if (focused != null) {
            long now = System.currentTimeMillis();
            long start = lastLookAt.getOrDefault(uuid, now);
            if (start == now) lastLookAt.put(uuid, now);

            if (now - start >= 1000 && !devotionActive.contains(uuid)) {
                // Activate Devotion!
                devotionActive.add(uuid);
                devotionExpiry.put(uuid, now + 15000);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 310, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 310, 0, false, false));
                player.sendMessage("§d💖 Devotion activated! (15s) Looking at " + focused.getName());
                world.spawnParticle(Particle.HEART, player.getLocation().add(0,2,0), 10, 0.5, 0.5, 0.5, 0.05);
            }
        } else {
            lastLookAt.put(uuid, System.currentTimeMillis());
        }

        // Expire devotion
        if (devotionActive.contains(uuid)) {
            long expiry = devotionExpiry.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() >= expiry) {
                devotionActive.remove(uuid);
                player.removePotionEffect(PotionEffectType.SPEED);
                player.removePotionEffect(PotionEffectType.STRENGTH);
                player.sendMessage("§d Devotion faded.");
            }
        }
    }

    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        // During Devotion: heal 0.5 hearts per hit
        if (isDevotionActive(player)) {
            double newHP = Math.min(player.getMaxHealth(), player.getHealth() + 1);
            player.setHealth(newHP);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0,2,0), 2, 0.2, 0.2, 0.2, 0.02);
        }

        // Overdrive bonus (+20% damage)
        if (overdrive.contains(player.getUniqueId())) {
            event.setDamage(event.getDamage() * 1.2);
        }
    }

    /** Sneak+Jump triggers Solo Focus (called from PassiveListener) */
    public void triggerSoloFocus(Player player) {
        UUID uuid = player.getUniqueId();
        devotionActive.add(uuid);
        devotionExpiry.put(uuid, System.currentTimeMillis() + 15000);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 310, 0, false, false));
        // +4% = Speed 0 essentially, just visual
        player.sendMessage("§d💖 Solo Focus! +4% Speed/Damage for 15s");
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0,2,0), 15, 0.5, 1, 0.5, 0.05);
    }

    public boolean isDevotionActive(Player player) {
        UUID uuid = player.getUniqueId();
        if (!devotionActive.contains(uuid)) return false;
        if (System.currentTimeMillis() >= devotionExpiry.getOrDefault(uuid, 0L)) {
            devotionActive.remove(uuid);
            return false;
        }
        return true;
    }

    private Player getLookedAtAlly(Player player) {
        // Ray-cast to see if looking at another player
        for (Entity e : player.getNearbyEntities(8, 8, 8)) {
            if (!(e instanceof Player target)) continue;
            if (target == player) continue;
            // Check if player is roughly looking at target
            Vector toTarget = target.getLocation().add(0,1,0).toVector()
                    .subtract(player.getEyeLocation().toVector()).normalize();
            Vector lookDir = player.getEyeLocation().getDirection();
            if (lookDir.dot(toTarget) > 0.95) {
                return target;
            }
        }
        return null;
    }

    @Override
    public void applyPassiveEffects(Player target) {
        // Devotion aura: healing and protective shield
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,   30, 0, false, false), true);
    }


    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_HeartPulse", "A2_AdorationBeam", "A3_LoveOverdrive"};
    }

    @Override
    public boolean isAbilityActive(int slot, java.util.UUID uuid) {
        if (slot == 3) return overdrive.contains(uuid);
        if (slot == 1) return devotionActive.contains(uuid);
        return false;
    }

}