package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class WitchMutation extends Mutation {

    public WitchMutation(MutationsPlugin plugin) { super(plugin); }

    @Override public MutationType getType() { return MutationType.WITCH; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§5🧙 You have been granted the §5Witch Mutation§5!");
        player.sendMessage("§7Passive: All potion effects gain +50% duration");
        player.sendMessage("§7A1: Double all your effect levels for 10s | A2: Strip effects from all nearby players");
    }

    @Override
    public void onRemove(Player player) {}

    // ---- Ability 1: Coven's Fury (80s CD) — double all effect levels for 10s ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_CovenFury", 80)) return;
        player.sendMessage("§5🧙 Coven's Fury! All your effects doubled for 10s!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.8f);
        world.spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.1);
        AbilityUtils.spawnSphere(player.getLocation(), 2, Particle.WITCH, null, 40);

        // Snapshot current effects, add doubled versions alongside originals
        List<PotionEffect> originals = new ArrayList<>(player.getActivePotionEffects());
        List<PotionEffect> doubled = new ArrayList<>();

        for (PotionEffect effect : originals) {
            // Double the amplifier (level)
            int newAmp = Math.min(effect.getAmplifier() * 2 + 1, 9); // cap at level 10
            doubled.add(new PotionEffect(effect.getType(), 200, newAmp, false, true)); // 10s
        }

        // Apply doubled effects — they override originals for 10s
        for (PotionEffect effect : doubled) {
            player.addPotionEffect(effect, true);
        }

        // After 10s restore originals (but only if still online and not already removed)
        runLater(() -> {
            if (!player.isOnline()) return;
            player.sendMessage("§5🧙 Coven's Fury faded.");
            // Reapply originals with remaining duration
            for (PotionEffect orig : originals) {
                if (player.hasPotionEffect(orig.getType())) {
                    // Restore original level, keeping remaining time
                    PotionEffect current = player.getPotionEffect(orig.getType());
                    if (current != null && current.getAmplifier() > orig.getAmplifier()) {
                        // Still at boosted level — restore original
                        player.addPotionEffect(new PotionEffect(
                            orig.getType(), Math.max(20, current.getDuration()), orig.getAmplifier(), false, true), true);
                    }
                }
            }
        }, 200L);
    }

    // ---- Ability 2: Mass Dispel (5m CD) — strip effects from everyone nearby ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_MassDispel", 300)) return;
        player.sendMessage("§5🧙 Mass Dispel!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 0.5f);
        AbilityUtils.spawnRing(player.getLocation().add(0, 0.5, 0), 12, Particle.WITCH, null, 48);
        AbilityUtils.spawnSphere(player.getLocation(), 6, Particle.WITCH, null, 50);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 12, player, plugin);
        int count = 0;
        for (LivingEntity e : nearby) {
            Collection<PotionEffect> effects = new ArrayList<>(e.getActivePotionEffects());
            for (PotionEffect effect : effects) {
                e.removePotionEffect(effect.getType());
            }
            world.spawnParticle(Particle.WITCH, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            count++;
        }
        player.sendMessage("§5🧙 Stripped effects from §f" + count + " §5entities!");
    }

    @Override
    public void activateAbility3(Player player) {
        player.sendMessage("§5🧙 Witch has no third ability.");
    }

    // ---- Passive: Extended Brews — all effects applied to the witch gain +50% duration ----
    // Handled in PassiveListener via EntityPotionEffectEvent

    @Override
    public void applyPassiveEffects(Player target) {
        // Extended Brews aura: regeneration and luck
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.LUCK,          30, 0, false, false), true);
    }

}