package com.plugin.mutations.utils;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class AbilityUtils {

    public static final String MUTATION_ITEM_KEY = "mutation_type";

    // ---- Mutation item helpers ----

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

    public static MutationType getMutationFromItem(ItemStack item, JavaPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, MUTATION_ITEM_KEY);
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        return MutationType.fromId(id);
    }

    private static Material getMutationMaterial(MutationType type) {
        return switch (type) {
            case WIND            -> Material.FEATHER;
            case BLOOD_SOLDIER   -> Material.MAGMA_CREAM;
            case FROZEN          -> Material.BLUE_ICE;
            case BYPASS          -> Material.ENDER_EYE;
            case ROCK            -> Material.COBBLESTONE;
            case HELLFIRE        -> Material.BLAZE_ROD;
            case DRAGONBORNE_POISON -> Material.DRAGON_BREATH;
            case DRAGONBORNE_FIRE   -> Material.FIRE_CHARGE;
            case DRAGONBORNE_ARMOR  -> Material.IRON_INGOT;
            case DRAGONBORNE_LIGHT           -> Material.GLOWSTONE;
            case TRUE_SHOT       -> Material.ARROW;
            case LOVE            -> Material.PINK_DYE;
            case DEBUFF          -> Material.FERMENTED_SPIDER_EYE;
            case WITCH           -> Material.WITCH_SPAWN_EGG;
            case SHADOW          -> Material.INK_SAC;
            case LIGHTNING       -> Material.LIGHTNING_ROD;
            case NATURE          -> Material.OAK_LEAVES;
            case INVENTORY       -> Material.CHEST;
        };
    }

    // ---- Combat utilities ----

    /** Apply a stun (blindness + slowness + mining fatigue) */
    public static void applyStun(LivingEntity entity, int ticks) {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,      ticks, 5,  false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,       ticks, 10, false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 10, false, false));
    }

    // ---- Kill credit + custom death messages ----

    /** Pending ability death messages: victimUUID → message */
    private static final Map<UUID, String> pendingDeathMessages = new java.util.concurrent.ConcurrentHashMap<>();

    public static String consumeDeathMessage(UUID victimUUID) {
        return pendingDeathMessages.remove(victimUUID);
    }

    /**
     * TRUE DAMAGE with kill credit + custom death message.
     * @param deathMessage  shown if this hit kills — use null to suppress custom message
     */
    public static void trueDamage(LivingEntity entity, double hearts, Player source, String deathMessage) {
        // Respect server-wide grace period
        var grace = org.bukkit.Bukkit.getPluginManager().getPlugin("Mutations");
        if (grace instanceof com.plugin.mutations.MutationsPlugin mp
                && mp.getGraceCommand().isGraceActive()) {
            return;
        }
        double dmg = hearts * 2.0;
        entity.setNoDamageTicks(0);

        double absorption = entity.getAbsorptionAmount();
        if (absorption > 0) {
            double absorbed = Math.min(absorption, dmg);
            entity.setAbsorptionAmount(absorption - absorbed);
            dmg -= absorbed;
        }

        if (dmg <= 0) {
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
            entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
            return;
        }

        double newHp = entity.getHealth() - dmg;

        if (newHp <= 0.0) {
            if (entity instanceof Player player && hasTotem(player)) {
                entity.setHealth(1.0);
                entity.damage(1.0, source);
            } else {
                // Use a tiny real damage call to register kill credit, then set HP to 0
                entity.setNoDamageTicks(0);
                entity.damage(0.001, source); // registers source as attacker for getKiller()
                if (deathMessage != null && entity instanceof Player) {
                    pendingDeathMessages.put(entity.getUniqueId(), deathMessage);
                }
                entity.setHealth(0);
            }
        } else {
            entity.setHealth(newHp);
        }
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
    }

    /**
     * TRUE DAMAGE — bypasses all armor, resistance, and effects.
     * Respects Totems of Undying: if the killing blow would kill a player who
     * holds a totem, we drop them to 1 HP and let vanilla's damage event fire
     * so the totem activates normally.
     * @param hearts how many hearts to remove (1 heart = 2 HP)
     */
    public static void trueDamage(LivingEntity entity, double hearts, Player source) {
        trueDamage(entity, hearts, source, null);
    }

    /** Returns true if the player has a Totem of Undying in main hand or off hand */
    private static boolean hasTotem(Player player) {
        org.bukkit.inventory.ItemStack main = player.getInventory().getItemInMainHand();
        org.bukkit.inventory.ItemStack off  = player.getInventory().getItemInOffHand();
        return (main != null && main.getType() == org.bukkit.Material.TOTEM_OF_UNDYING)
            || (off  != null && off.getType()  == org.bukkit.Material.TOTEM_OF_UNDYING);
    }

    /**
     * Hard-reset max health back to the default 20 HP (10 hearts) baseline.
     * Use this after timed abilities that temporarily raised max health expire.
     */
    public static void resetMaxHearts(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(20.0); // 10 hearts = 20 HP
        if (player.getHealth() > 20.0) player.setHealth(20.0);
    }

    /**
     * Add real hearts by raising MAX HEALTH attribute, then also heal the player.
     * Call {@link #undoMaxHeartChange(Player, double)} after the timer to restore.
     * @param hearts hearts to add (1 heart = 2 HP)
     */
    public static void addMaxHearts(Player player, double hearts) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double add = hearts * 2.0;
        attr.setBaseValue(attr.getBaseValue() + add);
        // Also give the player those hearts as current health
        player.setHealth(Math.min(attr.getBaseValue(), player.getHealth() + add));
    }

    /**
     * Remove real hearts by lowering MAX HEALTH attribute.
     * Call {@link #undoMaxHeartChange(Player, double)} after the timer to restore.
     * @param hearts hearts to remove (1 heart = 2 HP)
     */
    public static void removeMaxHearts(Player player, double hearts) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double remove = hearts * 2.0;
        double newMax = Math.max(2.0, attr.getBaseValue() - remove); // floor at 1 heart
        attr.setBaseValue(newMax);
        // Clamp current health to new max
        if (player.getHealth() > newMax) player.setHealth(newMax);
    }

    /**
     * Undo a previous addMaxHearts / removeMaxHearts call.
     * @param delta positive = we previously added this many hearts; negative = removed
     */
    public static void undoMaxHeartChange(Player player, double heartsAdded) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        double delta = heartsAdded * 2.0;
        double restored = attr.getBaseValue() - delta;
        restored = Math.max(2.0, restored); // never below 1 heart
        attr.setBaseValue(restored);
        if (player.getHealth() > restored) player.setHealth(restored);
    }

    // ---- Movement utilities ----

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

    // ---- Entity queries ----

    public static List<LivingEntity> getNearby(Location center, double radius, Player exclude) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le && e != exclude) result.add(le);
        }
        return result;
    }

    /** Trust-aware getNearby — skips players that the source trusts */
    public static List<LivingEntity> getNearbyTrusted(Location center, double radius, Player exclude, MutationsPlugin plugin) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e == exclude) continue;
            if (plugin != null && exclude != null && e instanceof Player target) {
                if (plugin.getTrustManager().isTrusted(exclude.getUniqueId(), target.getUniqueId())) continue;
            }
            result.add(le);
        }
        return result;
    }

    // ---- Beam ----

    /** Beam that deals TRUE DAMAGE (armor-independent). damage = hearts */
    public static Set<LivingEntity> beamDamage(Player source, int length, double hitRadius,
                                                Particle particle, Color dustColor, double heartsPerHit,
                                                String deathMessage) {
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
                    String msg = deathMessage != null
                            ? deathMessage.replace("%victim%", le instanceof Player p ? p.getName() : le.getName())
                            : null;
                    trueDamage(le, heartsPerHit, source, msg);
                    hit.add(le);
                }
            }
        }
        return hit;
    }

    public static Set<LivingEntity> beamDamage(Player source, int length, double hitRadius,
                                                Particle particle, Color dustColor, double heartsPerHit) {
        return beamDamage(source, length, hitRadius, particle, dustColor, heartsPerHit, null);
    }

    // ---- Knockback helpers ----

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

    // ---- Messages ----

    public static void sendCooldownMessage(Player player, String abilityName, long seconds) {
        player.sendMessage("§c[Mutations] " + abilityName + " is on cooldown! §7(" + seconds + "s remaining)");
    }

    public static void sendLockedMessage(Player player) {
        player.sendMessage("§c[Mutations] Your mutation abilities are locked!");
    }

    // ---- Particle shapes ----

    public static void spawnRing(Location center, double radius, Particle particle, Color color, int points) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            Location loc = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            if (color != null) {
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.2f));
            } else {
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            }
        }
    }

    public static void spawnSphere(Location center, double radius, Particle particle, Color color, int points) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double theta = Math.acos(1 - 2.0 * i / points);
            double phi   = Math.PI * (1 + Math.sqrt(5)) * i;
            double x = Math.sin(theta) * Math.cos(phi) * radius;
            double y = Math.cos(theta) * radius;
            double z = Math.sin(theta) * Math.sin(phi) * radius;
            Location loc = center.clone().add(x, y + 1, z);
            if (color != null) {
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0f));
            } else {
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            }
        }
    }
}
