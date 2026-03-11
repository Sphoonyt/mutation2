package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
        // Visible debuff radius ring
        AbilityUtils.spawnRing(player.getLocation().add(0, 0.3, 0), 10,
                Particle.WITCH, null, 40);
        AbilityUtils.spawnSphere(player.getLocation(), 5, Particle.WITCH, null, 40);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 10, player, plugin);
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
                    player.sendMessage("§5☠ Your effects have been reversed!");
                }
            }
        }, 1L);
    }


    // ---- Passive 2: Rotten flesh = Absorption II (handled via PassiveListener hook) ----
    public void onConsumeRottenFlesh(Player player) { onConsumeRottenFlesh(player, false); }

    public void onConsumeRottenFlesh(Player player, boolean fromOffHand) {
        // Remove 1 rotten flesh from the correct hand
        org.bukkit.inventory.ItemStack held = fromOffHand
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (held != null && held.getType() == org.bukkit.Material.ROTTEN_FLESH) {
            if (held.getAmount() > 1) {
                held.setAmount(held.getAmount() - 1);
            } else {
                if (fromOffHand) player.getInventory().setItemInOffHand(null);
                else             player.getInventory().setItemInMainHand(null);
            }
        }

        // Match a golden apple: full food, full saturation, Regen II 5s, Absorption II 2min
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true));  // Regen II 5s
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,   2400, 1, false, true)); // Absorption II 2min

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 8, 0.4, 0.4, 0.4, 0.05);
        player.sendMessage("§5☠ Rotten flesh consumed — Regen II + Absorption II for 2 minutes!");
    }

    // ---- Reversal map ----
    public static PotionEffectType getReversal(PotionEffectType type) {
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

    public static boolean isNegative(PotionEffectType type) {
        return type == PotionEffectType.WEAKNESS || type == PotionEffectType.SLOWNESS
                || type == PotionEffectType.POISON || type == PotionEffectType.BLINDNESS
                || type == PotionEffectType.MINING_FATIGUE || type == PotionEffectType.HUNGER
                || type == PotionEffectType.WITHER || type == PotionEffectType.NAUSEA;
    }

    public static boolean isPositive(PotionEffectType type) {
        return type == PotionEffectType.STRENGTH || type == PotionEffectType.SPEED
                || type == PotionEffectType.REGENERATION || type == PotionEffectType.NIGHT_VISION
                || type == PotionEffectType.HASTE || type == PotionEffectType.ABSORPTION
                || type == PotionEffectType.RESISTANCE || type == PotionEffectType.SATURATION;
    }

    private static String formatEffect(PotionEffectType type) {
        return type.toString().replace("_", " ").toLowerCase();
    }

    @Override
    public void applyPassiveEffects(Player target) {
        // Toxin mastery: resistance to debuffs, absorption layer
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 30, 0, false, false), true);
    }


    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_DebuffAll", "", ""};
    }

    @Override
    public boolean isAbilityActive(int slot, java.util.UUID uuid) {
        return false;
    }

}