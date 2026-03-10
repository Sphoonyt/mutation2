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
import org.bukkit.util.Vector;

import java.util.*;

public class BypassMutation extends Mutation {

    private final Set<UUID> rapierActive = new HashSet<>();
    private final Random random = new Random();

    public BypassMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.BYPASS; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§7🚫 You have been granted the §7Bypass Mutation§7!");
        player.sendMessage("§7Passive: Phase Defense (30% chance to ignore/reduce damage/knockback)");
    }

    @Override
    public void onRemove(Player player) {
        rapierActive.remove(player.getUniqueId());
    }

    // ---- Ability 1: Rapier State (30s CD, 7s duration) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_Rapier", 30)) return;

        UUID uuid = player.getUniqueId();
        rapierActive.add(uuid);
        player.sendMessage("§7🗡️ Rapier State active! (7s)");
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 140, 0));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 20, 0.3, 0.5, 0.3, 0.05);

        runLater(() -> {
            rapierActive.remove(uuid);
            if (player.isOnline()) player.sendMessage("§7Rapier State ended.");
        }, 140L);
    }

    // ---- Ability 2: Mutation Lock (45s CD) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_MutLock", 45)) return;

        // Lock nearest player with a mutation
        Player target = getNearestMutationPlayer(player, 15);
        if (target == null) {
            player.sendMessage("§c No mutation player found within 15 blocks!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A2_MutLock");
            return;
        }

        plugin.getMutationManager().lockMutation(target, 10);
        player.sendMessage("§7🚫 Locked §f" + target.getName() + "§7's mutation for 10s!");
        target.sendMessage("§c🚫 Your mutation abilities have been locked for 10s by " + player.getName() + "!");

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0,1,0), 30, 0.5, 1, 0.5, 0.05);
    }

    // ---- Ability 3: Phantom Dash (16s CD) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_PhantomDash", 16)) return;
        player.sendMessage("§7👻 Phantom Dash!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        // Invisible trail during dash
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 10, 0, false, false));

        Location start = player.getLocation().clone();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        dir.setY(Math.max(dir.getY(), 0.05));

        // Dash
        player.setVelocity(dir.multiply(1.4));

        // Track hit entities to avoid multi-hit
        Set<UUID> hitEntities = new HashSet<>();
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) { task.cancel(); return; }

            double traveled = player.getLocation().distance(start);
            world.spawnParticle(Particle.END_ROD, player.getLocation(), 3, 0.1, 0.1, 0.1, 0);

            List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 1.5, player, plugin);
            for (LivingEntity e : nearby) {
                if (hitEntities.contains(e.getUniqueId())) continue;
                hitEntities.add(e.getUniqueId());
                AbilityUtils.trueDamage(e, 5, player); // 5 hearts true damage
                world.spawnParticle(Particle.EXPLOSION_EMITTER, e.getLocation(), 3, 0.2, 0.2, 0.2, 0);
                world.playSound(e.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
            }

            if (traveled >= 7 || player.isOnGround()) {
                task.cancel();
            }
        }, 0, 1);
    }

    // ---- Passive: Phase Defense ----
    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        // 30% chance to completely negate the hit
        if (random.nextFloat() < 0.30) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.05);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
            player.sendMessage("§7🚫 Phase Defense triggered!");
        }
    }

    public boolean isRapierActive(UUID uuid) {
        return rapierActive.contains(uuid);
    }

    private Player getNearestMutationPlayer(Player source, double maxDist) {
        Player nearest = null;
        double minDist = maxDist * maxDist;

        for (Player p : source.getWorld().getPlayers()) {
            if (p == source) continue;
            if (!plugin.getMutationManager().hasMutation(p)) continue;
            double dist = p.getLocation().distanceSquared(source.getLocation());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    @Override
    public void applyPassiveEffects(Player target) {
        // Ghost-like speed and perception
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,       30, 1, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,30, 0, false, false), true);
    }


    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_Rapier", "A2_MutLock", "A3_PhantomDash"};
    }

    @Override
    public boolean isAbilityActive(int slot, java.util.UUID uuid) {
        if (slot == 1) return rapierActive.contains(uuid);
        return false;
    }

}