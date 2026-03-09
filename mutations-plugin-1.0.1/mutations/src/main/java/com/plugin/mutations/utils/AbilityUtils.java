package com.plugin.mutations.utils;

import com.plugin.mutations.MutationType;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.plugin.mutations.MutationsPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class AbilityUtils {

    public static final String MUTATION_ITEM_KEY = "mutation_type";

    /** Build the physical mutation item for a given type */
    public static ItemStack createMutationItem(MutationType type, JavaPlugin plugin) {
        Material mat = getMutationMaterial(type);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName() + " §r§8[Mutation Orb]");
        List<String> lore = new ArrayList<>();
        lore.add("§7Right-click to §aabsorb §7this mutation.");
        lore.add("§7Use §f/mutation withdraw §7to return it.");
        lore.add("");
        lore.add("§8Type: §f" + type.getId());
        meta.setLore(lore);
        NamespacedKey key = new NamespacedKey(plugin, MUTATION_ITEM_KEY);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.getId());
        item.setItemMeta(meta);
        return item;
    }

    /** Get the mutation type from a tagged item, or null if not a mutation item */
    public static MutationType getMutationFromItem(ItemStack item, JavaPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, MUTATION_ITEM_KEY);
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        return MutationType.fromId(id);
    }

    /** Choose a material to represent each mutation */
    private static Material getMutationMaterial(MutationType type) {
        return switch (type) {
            case WIND -> Material.FEATHER;
            case BLOOD_SOLDIER -> Material.MAGMA_CREAM;
            case FROZEN -> Material.BLUE_ICE;
            case BYPASS -> Material.ENDER_EYE;
            case ROCK -> Material.COBBLESTONE;
            case HELLFIRE -> Material.BLAZE_ROD;
            case DRAGONBORNE_POISON -> Material.DRAGON_BREATH;
            case DRAGONBORNE_FIRE -> Material.FIRE_CHARGE;
            case DRAGONBORNE_ARMOR -> Material.IRON_INGOT;
            case LIGHT -> Material.GLOWSTONE;
            case TRUE_SHOT -> Material.ARROW;
            case LOVE -> Material.PINK_DYE;
            case DEBUFF -> Material.FERMENTED_SPIDER_EYE;
        };
    }

    /** Apply a stun to a living entity */
    public static void applyStun(LivingEntity entity, int ticks) {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 5, false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10, false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 10, false, false));
    }

    public static void launch(Entity entity, Vector direction, double power) {
        entity.setVelocity(direction.normalize().multiply(power));
    }

    public static void launchUp(Entity entity, double blocks) {
        Vector vel = entity.getVelocity();
        vel.setY(blocks * 0.17 + 0.2);
        entity.setVelocity(vel);
    }

    public static void dash(Player player, double blocks) {
        Vector dir = player.getEyeLocation().getDirection().normalize();
        dir.setY(Math.max(dir.getY(), 0.1));
        player.setVelocity(dir.multiply(blocks * 0.17));
    }

    public static List<LivingEntity> getNearby(Location center, double radius, Player exclude) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le && e != exclude) result.add(le);
        }
        return result;
    }

    public static Set<LivingEntity> beamDamage(Player source, int length, double hitRadius,
                                                Particle particle, Color dustColor, double damage) {
        Set<LivingEntity> hit = new HashSet<>();
        Location loc = source.getEyeLocation();
        Vector dir = loc.getDirection().normalize();
        for (int i = 0; i < length; i++) {
            loc.add(dir);
            if (!loc.getBlock().isPassable()) break;
            if (dustColor != null) {
                loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.1, 0.1, 0.1, 0,
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

    public static void knockbackFrom(Location center, List<LivingEntity> entities, double blocks) {
        for (LivingEntity e : entities) {
            Vector dir = e.getLocation().toVector().subtract(center.toVector()).normalize();
            dir.setY(0.3);
            e.setVelocity(dir.multiply(blocks * 0.17));
        }
    }

    public static void pullToward(Location center, List<LivingEntity> entities) {
        for (LivingEntity e : entities) {
            Vector dir = center.toVector().subtract(e.getLocation().toVector()).normalize();
            dir.setY(0.2);
            e.setVelocity(dir.multiply(0.6));
        }
    }

    public static void sendCooldownMessage(Player player, String abilityName, long seconds) {
        player.sendMessage("§c[Mutations] " + abilityName + " is on cooldown! §7(" + seconds + "s remaining)");
    }

    public static void sendLockedMessage(Player player) {
        player.sendMessage("§c[Mutations] Your mutation abilities are locked!");
    }

    /** True damage — bypasses armor, directly reduces health */
    public static void trueDamage(LivingEntity entity, double hearts, Player source) {
        double dmg = hearts * 2.0; // 1 heart = 2 HP
        double newHp = Math.max(0.0, entity.getHealth() - dmg);
        entity.setNoDamageTicks(0);
        entity.setHealth(newHp);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
    }

    /** Add real hearts (not absorption) — clamps to max health */
    public static void addHearts(Player player, double hearts) {
        double add = hearts * 2.0;
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + add));
    }

    /** Remove real hearts — clamps to 0.5 HP minimum so player doesn't die unexpectedly */
    public static void removeHearts(Player player, double hearts) {
        double remove = hearts * 2.0;
        player.setHealth(Math.max(1.0, player.getHealth() - remove));
    }


    /**
     * Get nearby living entities, excluding the source player AND any players they trust.
     * Pass plugin to enable trust filtering. Pass null to skip trust filtering.
     */
    public static List<LivingEntity> getNearbyTrusted(Location center, double radius, Player exclude, MutationsPlugin plugin) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e == exclude) continue;
            // Skip trusted players
            if (plugin != null && exclude != null && e instanceof Player target) {
                if (plugin.getTrustManager().isTrusted(exclude.getUniqueId(), target.getUniqueId())) continue;
            }
            result.add(le);
        }
        return result;
    }

    /**
     * Spawn a ring of particles at a given location.
     * @param center center location
     * @param radius ring radius in blocks
     * @param particle particle type
     * @param color dust color (null for non-dust particles)
     * @param points number of points around the ring
     */
    public static void spawnRing(Location center, double radius, Particle particle, Color color, int points) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, 0, z);
            if (color != null) {
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(color, 1.2f));
            } else {
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Spawn a sphere shell of particles.
     */
    public static void spawnSphere(Location center, double radius, Particle particle, Color color, int points) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double theta = Math.acos(1 - 2.0 * i / points);
            double phi = Math.PI * (1 + Math.sqrt(5)) * i;
            double x = Math.sin(theta) * Math.cos(phi) * radius;
            double y = Math.cos(theta) * radius;
            double z = Math.sin(theta) * Math.sin(phi) * radius;
            Location loc = center.clone().add(x, y + 1, z);
            if (color != null) {
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(color, 1.0f));
            } else {
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            }
        }
    }

}
