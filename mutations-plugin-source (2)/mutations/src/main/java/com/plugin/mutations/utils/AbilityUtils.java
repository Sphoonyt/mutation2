package com.plugin.mutations.utils;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class AbilityUtils {

    public static final String ABILITY_TAG = "mutation_ability";

    /** Create an ability item for the hotbar */
    public static ItemStack createAbilityItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        for (String l : lore) loreList.add("§7" + l);
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    /** Give ability items to a player in slots 2, 3, 4 (keeping 0-1 free) */
    public static void giveAbilityItems(Player player, ItemStack a1, ItemStack a2, ItemStack a3) {
        player.getInventory().setItem(2, a1);
        player.getInventory().setItem(3, a2);
        player.getInventory().setItem(4, a3);
    }

    /** Remove ability items from the ability slots */
    public static void removeAbilityItems(Player player) {
        player.getInventory().setItem(2, null);
        player.getInventory().setItem(3, null);
        player.getInventory().setItem(4, null);
    }

    /** Check if held item is an ability item by display name prefix */
    public static int getAbilitySlot(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return -1;
        String name = meta.getDisplayName();
        if (name.contains("[A1]")) return 1;
        if (name.contains("[A2]")) return 2;
        if (name.contains("[A3]")) return 3;
        return -1;
    }

    /** Apply a stun to a living entity (blindness + slowness + mining fatigue) */
    public static void applyStun(LivingEntity entity, int ticks) {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 5, false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10, false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 10, false, false));
    }

    /** Launch entity in a direction with given power */
    public static void launch(Entity entity, Vector direction, double power) {
        entity.setVelocity(direction.normalize().multiply(power));
    }

    /** Launch entity upward by Y blocks equivalent */
    public static void launchUp(Entity entity, double blocks) {
        Vector vel = entity.getVelocity();
        vel.setY(blocks * 0.17 + 0.2);
        entity.setVelocity(vel);
    }

    /** Dash player forward N blocks */
    public static void dash(Player player, double blocks) {
        Vector dir = player.getEyeLocation().getDirection().normalize();
        dir.setY(Math.max(dir.getY(), 0.1));
        player.setVelocity(dir.multiply(blocks * 0.17));
    }

    /** Get nearby living entities excluding source */
    public static List<LivingEntity> getNearby(Location center, double radius, Player exclude) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le && e != exclude) {
                result.add(le);
            }
        }
        return result;
    }

    /** Draw a particle beam and return entities hit */
    public static Set<LivingEntity> beamDamage(Player source, int length, double hitRadius,
                                                Particle particle, Color dustColor, double damage) {
        Set<LivingEntity> hit = new HashSet<>();
        Location loc = source.getEyeLocation();
        Vector dir = loc.getDirection().normalize();

        for (int i = 0; i < length; i++) {
            loc.add(dir);
            if (!loc.getBlock().isPassable()) break;

            if (dustColor != null) {
                loc.getWorld().spawnParticle(Particle.DUST,
                        loc, 5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(dustColor, 1.5f));
            } else {
                loc.getWorld().spawnParticle(particle, loc, 3, 0.1, 0.1, 0.1, 0);
            }

            for (Entity e : loc.getWorld().getNearbyEntities(loc, hitRadius, hitRadius, hitRadius)) {
                if (e instanceof LivingEntity le && e != source && !hit.contains(le)) {
                    le.damage(damage, source);
                    hit.add(le);
                }
            }
        }
        return hit;
    }

    /** Play ability activation effect */
    public static void abilityEffect(Player player, Sound sound, Particle particle) {
        player.getWorld().playSound(player.getLocation(), sound, 1f, 1f);
        player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
    }

    /** Knockback entities away from center */
    public static void knockbackFrom(Location center, List<LivingEntity> entities, double blocks) {
        for (LivingEntity e : entities) {
            Vector dir = e.getLocation().toVector().subtract(center.toVector()).normalize();
            dir.setY(0.3);
            e.setVelocity(dir.multiply(blocks * 0.17));
        }
    }

    /** Pull entities toward center */
    public static void pullToward(Location center, List<LivingEntity> entities) {
        for (LivingEntity e : entities) {
            Vector dir = center.toVector().subtract(e.getLocation().toVector()).normalize();
            dir.setY(0.2);
            e.setVelocity(dir.multiply(0.6));
        }
    }

    /** Sends cooldown message to player */
    public static void sendCooldownMessage(Player player, String abilityName, long seconds) {
        player.sendMessage("§c[Mutations] " + abilityName + " is on cooldown! §7(" + seconds + "s remaining)");
    }

    /** Sends locked message */
    public static void sendLockedMessage(Player player) {
        player.sendMessage("§c[Mutations] Your mutation abilities are locked!");
    }
}
