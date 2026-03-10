package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class RockMutation extends Mutation {

    // Stone Recovery tracking
    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private final Map<UUID, Integer> stillTicks = new HashMap<>();

    public RockMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.ROCK; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§6🪨 You have been granted the §6Rock Mutation§6!");
        player.sendMessage("§7Passive: Stone Recovery - Repair armor 1%/s while still");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        lastPosition.remove(uuid);
        stillTicks.remove(uuid);
    }

    // ---- Ability 1: Stoneburst Slam (16s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_StoneSlamm", 16)) return;
        player.sendMessage("§6🪨 Stoneburst Slam!");

        World world = player.getWorld();
        Location loc = player.getLocation();

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
        world.spawnParticle(Particle.BLOCK, loc, 80, 1.5, 0.3, 1.5, 0.15,
                Material.STONE.createBlockData());
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 0.5, 0.1, 0.5, 0.05);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(loc, 5, player, plugin);
        for (LivingEntity e : nearby) {
            e.damage(8, player); // 4 hearts
            Vector dir = e.getLocation().toVector().subtract(loc.toVector()).normalize();
            dir.setY(0.3);
            e.setVelocity(dir.multiply(0.35)); // ~2 block knockback
        }
    }

    // ---- Ability 2: Skin Hardening (40s CD, 6s) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_SkinHarden", 40)) return;
        player.sendMessage("§6🪨 Skin Hardening!");

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1)); // Resistance II
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 0));   // Slowness I

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.5f);
        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0,1,0), 40, 0.5, 1, 0.5, 0.2,
                Material.STONE.createBlockData());

        runLater(() -> {
            if (player.isOnline()) player.sendMessage("§6Stone hardening faded.");
        }, 120L);
    }

    // ---- Ability 3: Boulder Barrage (20s CD) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_BoulderBarrage", 20)) return;
        player.sendMessage("§6🪨 Boulder Barrage!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.7f);

        // Launch 3 boulders with 0.5s (10 tick) delay between each
        for (int i = 0; i < 3; i++) {
            final int index = i;
            runLater(() -> {
                if (!player.isOnline()) return;
                Vector dir = player.getEyeLocation().getDirection().normalize();
                if (index == 1) dir.add(new Vector(0.15, 0.05, 0));
                if (index == 2) dir.add(new Vector(-0.15, 0.05, 0));
                dir.normalize();

                Snowball boulder = player.launchProjectile(Snowball.class);
                boulder.setVelocity(dir.multiply(1.8));
                boulder.setMetadata("rock_boulder", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

                world.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 0.7f, 0.5f);
            }, i * 10L); // 10 ticks = 0.5 seconds between boulders
        }
    }

    // ---- Passive: Stone Recovery ----
    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();
        Location prev = lastPosition.get(uuid);

        // Check if player moved
        boolean isStill = prev != null
                && Math.abs(loc.getX() - prev.getX()) < 0.05
                && Math.abs(loc.getZ() - prev.getZ()) < 0.05;

        if (isStill) {
            int ticks = stillTicks.getOrDefault(uuid, 0) + 1;
            stillTicks.put(uuid, ticks);

            // After 3s still (60 ticks), start repairing armor every second (20 ticks)
            if (ticks >= 60 && ticks % 20 == 0) {
                repairArmor(player, 1);
                player.getWorld().spawnParticle(Particle.BLOCK,
                        player.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.05,
                        Material.STONE.createBlockData());
            }
        } else {
            stillTicks.put(uuid, 0);
        }

        lastPosition.put(uuid, loc.clone());
    }

    private void repairArmor(Player player, int percentPerSlot) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (!item.getType().toString().contains("_HELMET")
                    && !item.getType().toString().contains("_CHESTPLATE")
                    && !item.getType().toString().contains("_LEGGINGS")
                    && !item.getType().toString().contains("_BOOTS")) continue;

            if (!(item.getItemMeta() instanceof Damageable damageable)) continue;
            int maxDur = item.getType().getMaxDurability();
            int curDur = damageable.getDamage();
            int repair = Math.max(1, (int)(maxDur * (percentPerSlot / 100.0)));
            damageable.setDamage(Math.max(0, curDur - repair));
            item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) damageable);
        }
        player.getInventory().setArmorContents(armor);
    }

    @Override
    public void applyPassiveEffects(Player target) {
        // Stone Skin: high resistance
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   30, 0, false, false), true);
    }

}