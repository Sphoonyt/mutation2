package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BloodSoldierMutation extends Mutation {

    // Passive hit tracking
    private final Map<UUID, Integer> consecutiveHits = new HashMap<>();
    private final Map<UUID, Integer> harvestHits = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    private final Set<UUID> katanaActive = new HashSet<>();
    private final Set<UUID> nauseaReady = new HashSet<>(); // after 4 hits, waiting for activation
    private final Map<UUID, Long> consecutiveResetTime = new HashMap<>();

    private static final long CONSECUTIVE_HIT_WINDOW_MS = 3000; // hits must be within 3s

    public BloodSoldierMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.BLOOD_SOLDIER; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§c🩸 You have been granted the §cBlood-Soldier Mutation§c!");
        player.sendMessage("§7Passive: Blood Harvest (every 15 hits steal 2 hearts for 8s)");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        consecutiveHits.remove(uuid);
        harvestHits.remove(uuid);
        lastHitTime.remove(uuid);
        katanaActive.remove(uuid);
        nauseaReady.remove(uuid);
    }

    // ---- Ability 1: Nauseating Blood (17s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_NauseBlood", 17)) return;

        UUID uuid = player.getUniqueId();
        int hits = consecutiveHits.getOrDefault(uuid, 0);

        if (hits < 4) {
            player.sendMessage("§c🩸 You need 4 consecutive hits to activate! (" + hits + "/4)");
            plugin.getCooldownManager().clearAbility(uuid, "A1_NauseBlood");
            return;
        }

        player.sendMessage("§c🩸 Nauseating Blood!");
        consecutiveHits.put(uuid, 0);

        // Apply slowness to nearby enemies
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1f, 0.5f);
        world.spawnParticle(Particle.FALLING_LAVA, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.05);
        AbilityUtils.spawnRing(player.getLocation().add(0, 0.3, 0), 6, Particle.FALLING_LAVA, null, 28);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 6, player, plugin);
        for (LivingEntity e : nearby) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
            // Visual
            world.spawnParticle(Particle.FALLING_LAVA, e.getLocation().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.01);
        }
        player.sendMessage("§c Applied Slowness to " + nearby.size() + " enemies!");
    }

    // ---- Ability 2: Soldier's Sacrifice (60s CD) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_Sacrifice", 60)) return;

        if (player.getHealth() <= 4) {
            player.sendMessage("§c🩸 Not enough health to sacrifice!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A2_Sacrifice");
            return;
        }

        player.sendMessage("§c🩸 Soldier's Sacrifice! Max health set to 8 hearts for Strength II!");
        // Get current max health to know how many hearts to remove
        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double originalMax = attr.getBaseValue();
        double targetMax = 16.0; // 8 hearts
        // Only reduce if above 8 hearts; clamp to 8 hearts max
        double delta = originalMax - targetMax; // how many HP we're removing from max
        double heartsRemoved = delta / 2.0;

        if (heartsRemoved > 0) {
            AbilityUtils.removeMaxHearts(player, heartsRemoved);
        }
        // Set current health to exactly 8 hearts too
        player.setHealth(Math.min(player.getHealth(), 16.0));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 0.5f);
        player.getWorld().spawnParticle(Particle.FALLING_LAVA, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.05);

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 1)); // 8s Strength II

        // Restore original max health after 8s
        runLater(() -> {
            if (player.isOnline()) {
                if (heartsRemoved > 0) AbilityUtils.undoMaxHeartChange(player, -heartsRemoved);
                player.sendMessage("§c🩸 Your max health has been restored!");
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            }
        }, 160L);
    }

    // ---- Ability 3: Blood Katana (25s CD, 8s duration) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_Katana", 25)) return;

        if (player.getHealth() <= 3) {
            player.sendMessage("§c🩸 Not enough health to create Blood Katana!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A3_Katana");
            return;
        }

        // Cost: lower max health by 1.5 hearts, restored when katana expires
        AbilityUtils.removeMaxHearts(player, 1.5);

        UUID uuid = player.getUniqueId();
        katanaActive.add(uuid);

        // Give blood katana (enchanted sword)
        ItemStack katana = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = katana.getItemMeta();
        meta.setDisplayName("§c🩸 Blood Katana");
        List<String> lore = new ArrayList<>();
        lore.add("§7Hits steal 0.5 saturation");
        lore.add("§7+1 bonus damage");
        lore.add("§cBlood Mutation Active");
        meta.setLore(lore);
        katana.setItemMeta(meta);
        player.getInventory().setItemInMainHand(katana);

        player.sendMessage("§c🩸 Blood Katana summoned! (8s)");
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 0.7f);
        player.getWorld().spawnParticle(Particle.FALLING_LAVA, player.getLocation().add(0,1,0), 20, 0.3, 0.5, 0.3, 0.05);

        // Remove after 8s
        runLater(() -> {
            if (player.isOnline()) {
                katanaActive.remove(uuid);
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.IRON_SWORD
                        && held.hasItemMeta()
                        && "§c🩸 Blood Katana".equals(held.getItemMeta().getDisplayName())) {
                    player.getInventory().setItemInMainHand(null);
                }
                AbilityUtils.undoMaxHeartChange(player, -1.5); // restore 1.5 hearts to max
                player.sendMessage("§c🩸 Blood Katana dissipated. Max health restored.");
            }
        }, 160L);
    }

    // ---- Passive: Blood Harvest & Katana effects ----
    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Reset consecutive hits if too long between hits
        long lastHit = lastHitTime.getOrDefault(uuid, 0L);
        if (now - lastHit > CONSECUTIVE_HIT_WINDOW_MS) {
            consecutiveHits.put(uuid, 0);
        }
        lastHitTime.put(uuid, now);

        // Consecutive hit counter for Nauseating Blood
        int consec = consecutiveHits.getOrDefault(uuid, 0) + 1;
        consecutiveHits.put(uuid, consec);
        if (consec == 4) {
            player.sendMessage("§c🩸 Nauseating Blood ready! Activate A1!");
        }

        // Blood Harvest counter
        int harvest = harvestHits.getOrDefault(uuid, 0) + 1;
        harvestHits.put(uuid, harvest);
        if (harvest >= 15) {
            harvestHits.put(uuid, 0);
            triggerBloodHarvest(player, target);
        }

        // Blood Katana effects
        if (katanaActive.contains(uuid)) {
            // +1 bonus damage
            event.setDamage(event.getDamage() + 2);
            // Steal 0.5 saturation from target (if player)
            if (target instanceof Player targetPlayer) {
                float sat = targetPlayer.getSaturation();
                targetPlayer.setSaturation(Math.max(0, sat - 0.5f));
                player.setSaturation(Math.min(20, player.getSaturation() + 0.5f));
            }
            player.getWorld().spawnParticle(Particle.FALLING_LAVA,
                    target.getLocation().add(0,1,0), 5, 0.2, 0.3, 0.2, 0.02);
        }
    }

    private void triggerBloodHarvest(Player player, LivingEntity target) {
        player.sendMessage("§c🩸 Blood Harvest activated! Stole 2 hearts!");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.5f);
        AbilityUtils.spawnRing(player.getLocation().add(0, 1, 0), 1.5, Particle.FALLING_LAVA, null, 16);

        // True steal: remove 2 real hearts from target, add 2 real hearts to self
        AbilityUtils.trueDamage(target, 2, player);
        AbilityUtils.addMaxHearts(player, 2); // steal 2 hearts into max health pool

        player.getWorld().spawnParticle(Particle.FALLING_LAVA, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.02);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.05);
    }
}
