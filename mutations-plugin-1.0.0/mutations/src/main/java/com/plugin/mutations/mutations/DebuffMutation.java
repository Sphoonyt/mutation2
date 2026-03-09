package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class DebuffMutation extends Mutation {

    public DebuffMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.DEBUFF; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§5☠ You have been granted the §5Debuff Mutation§5!");
        player.sendMessage("§7Passive 1: Debuffs/buffs inflicted are reversed at same level");
        player.sendMessage("§7Passive 2: Rotten flesh gives Absorption II instead");
        player.sendMessage("§7Use §f/ability 1 §7to activate your ability.");
    }

    @Override
    public void onRemove(Player player) {}

    // ---- Ability 1: Mass Debuff (45s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_DebuffAll", 45)) return;
        player.sendMessage("§5☠ Mass Debuff!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);
        world.spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 60, 2, 1, 2, 0.1);

        List<LivingEntity> nearby = AbilityUtils.getNearby(player.getLocation(), 10, player);
        for (LivingEntity e : nearby) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,   100, 0)); // Weakness I  5s
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   100, 3)); // Slowness IV 5s
            e.addPotionEffect(new PotionEffect(PotionEffectType.POISON,     100, 4)); // Poison V    5s
            world.spawnParticle(Particle.WITCH, e.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
        }
        player.sendMessage("§5Debuffed §f" + nearby.size() + " §5enemies!");
    }

    @Override
    public void activateAbility2(Player player) {
        player.sendMessage("§5No Ability 2 for Debuff Mutation.");
    }

    @Override
    public void activateAbility3(Player player) {
        player.sendMessage("§5No Ability 3 for Debuff Mutation.");
    }

    // ---- Passive 1: Reverse all debuffs/buffs inflicted ----
    // When enemies try to give this player a debuff, reverse it to a buff.
    // When this player damages an enemy, reverse any active buffs on the enemy to debuffs.
    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        // Schedule a check — after damage is applied, flip any new negative effects on the player
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                PotionEffectType reversed = getReversal(effect.getType());
                if (reversed != null && isNegative(effect.getType())) {
                    // Replace negative with its positive counterpart
                    player.removePotionEffect(effect.getType());
                    player.addPotionEffect(new PotionEffect(reversed, effect.getDuration(), effect.getAmplifier(), false, true));
                    player.sendMessage("§5☠ " + formatEffect(effect.getType()) + " reversed to " + formatEffect(reversed) + "!");
                }
            }
        }, 1L);
    }


    // ---- Passive 2: Rotten flesh = Absorption II (handled via PassiveListener hook) ----
    public void onConsumeRottenFlesh(Player player, PlayerItemConsumeEvent event) {
        event.setCancelled(true);
        // Match golden apple stats exactly:
        // +4 hunger, 9.6 saturation, Regeneration II 5s, Absorption I 2min, Absorption II 5s bonus
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
        player.setSaturation(Math.min(20f, player.getSaturation() + 9.6f));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));  // Regen II 5s
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,   2400, 1)); // Absorption II 2min
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.05);
        player.sendMessage("§5☠ Rotten flesh converted — golden apple effect + Absorption II!");
    }

    // ---- Reversal map ----
    private static PotionEffectType getReversal(PotionEffectType type) {
        if (type == PotionEffectType.WEAKNESS)      return PotionEffectType.STRENGTH;
        if (type == PotionEffectType.STRENGTH)      return PotionEffectType.WEAKNESS;
        if (type == PotionEffectType.SLOWNESS)      return PotionEffectType.SPEED;
        if (type == PotionEffectType.SPEED)         return PotionEffectType.SLOWNESS;
        if (type == PotionEffectType.POISON)        return PotionEffectType.REGENERATION;
        if (type == PotionEffectType.REGENERATION)  return PotionEffectType.POISON;
        if (type == PotionEffectType.BLINDNESS)     return PotionEffectType.NIGHT_VISION;
        if (type == PotionEffectType.NIGHT_VISION)  return PotionEffectType.BLINDNESS;
        if (type == PotionEffectType.MINING_FATIGUE) return PotionEffectType.HASTE;
        if (type == PotionEffectType.HASTE)         return PotionEffectType.MINING_FATIGUE;
        if (type == PotionEffectType.HUNGER)        return PotionEffectType.SATURATION;
        return null;
    }

    private static boolean isNegative(PotionEffectType type) {
        return type == PotionEffectType.WEAKNESS || type == PotionEffectType.SLOWNESS
                || type == PotionEffectType.POISON || type == PotionEffectType.BLINDNESS
                || type == PotionEffectType.MINING_FATIGUE || type == PotionEffectType.HUNGER
                || type == PotionEffectType.WITHER || type == PotionEffectType.NAUSEA;
    }

    private static boolean isPositive(PotionEffectType type) {
        return type == PotionEffectType.STRENGTH || type == PotionEffectType.SPEED
                || type == PotionEffectType.REGENERATION || type == PotionEffectType.NIGHT_VISION
                || type == PotionEffectType.HASTE || type == PotionEffectType.ABSORPTION
                || type == PotionEffectType.RESISTANCE || type == PotionEffectType.SATURATION;
    }

    private static String formatEffect(PotionEffectType type) {
        return type.toString().replace("_", " ").toLowerCase();
    }
}
