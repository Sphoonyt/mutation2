package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class FrozenMutation extends Mutation {

    // Ice Runner tracking
    private final Map<UUID, Boolean> hadSpeedFromIce = new HashMap<>();
    // Glacial domain tracking
    private final Map<UUID, List<Block>> domainBlocks = new HashMap<>();

    public FrozenMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.FROZEN; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§f❄️ You have been granted the §fFrozen Mutation§f!");
        player.sendMessage("§7Passive: Ice Runner - Speed II while on ice");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        hadSpeedFromIce.remove(uuid);
        // Clean up any domain blocks
        List<Block> blocks = domainBlocks.remove(uuid);
        if (blocks != null) restoreBlocks(blocks);
    }

    // ---- Ability 1: Frozen Recovery (30s CD) ----
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_FrozenRec", 30)) return;
        player.sendMessage("§f❄️ Frozen Recovery!");

        World world = player.getWorld();
        UUID uuid = player.getUniqueId();
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);

        // ---- Build ice cage ----
        // Cage is 3x4x3 (x: -1..+1, y: 0..+3, z: -1..+1) relative to the block at player feet.
        // Layer 0 = floor (solid slab of ice), layers 1-2 = hollow walls (player body space),
        // layer 3 = ceiling (solid slab).
        // The center column (x=0, z=0, y=1 and y=2) is left open — that's where the player stands.
        Location feet = player.getLocation().getBlock().getLocation(); // integer block coords
        List<Block> cageBlocks = new ArrayList<>();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean isFloor   = (y == 0);
                    boolean isCeiling = (y == 3);
                    boolean isWall    = (x == -1 || x == 1 || z == -1 || z == 1);

                    if (!isFloor && !isCeiling && !isWall) continue; // interior air

                    // Leave the center column open for the player (body at y=1, y=2)
                    if (x == 0 && z == 0 && !isFloor && !isCeiling) continue;

                    Block b = feet.clone().add(x, y, z).getBlock();
                    // Only place in air (don't overwrite terrain)
                    if (b.getType() == Material.AIR) {
                        b.setType(Material.BLUE_ICE);
                        cageBlocks.add(b);
                    }
                }
            }
        }

        // Teleport player to stand on the floor tile (y+1 above feet block)
        Location inside = feet.clone().add(0.5, 1.0, 0.5);
        inside.setYaw(player.getLocation().getYaw());
        inside.setPitch(player.getLocation().getPitch());
        player.teleport(inside);

        world.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 80, 0.6, 1, 0.6, 0.1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   80, 10, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1,  false, false));

        // Heal 3 hearts over 3 seconds, then remove cage
        final int[] healCount = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || healCount[0] >= 3) {
                task.cancel();
                for (Block b : cageBlocks) {
                    if (b.getType() == Material.BLUE_ICE) b.setType(Material.AIR);
                }
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                player.sendMessage("§f❄️ Released from ice! Slowed for 3s.");
                world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.5f);
                world.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.15);
                return;
            }
            player.setHealth(Math.min(
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                player.getHealth() + 2.0));
            world.spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 3, 0.2, 0.2, 0.2, 0);
            world.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 15, 0.4, 0.8, 0.4, 0.05);
            healCount[0]++;
        }, 20L, 20L);
    }

    // ---- Ability 2: Ice Spike Line (14s CD) ----
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_IceSpike", 14)) return;
        player.sendMessage("§f❄️ Ice Spike Line!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);

        Location loc = player.getLocation();
        Vector dir = loc.getDirection();
        dir.setY(0);
        dir.normalize();

        List<Block> spikeBlocks = new ArrayList<>();

        for (int i = 1; i <= 8; i++) {
            Location target = loc.clone().add(dir.clone().multiply(i));
            Block groundBlock = target.getBlock();
            Block aboveBlock = target.clone().add(0, 1, 0).getBlock();

            // Damage entities in the spike path
            List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(target, 1, player, plugin);
            for (LivingEntity e : hit) {
                e.damage(6, player); // 3 damage (3 hearts)
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                world.spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.05);
            }

            // Place ice spike visually
            if (groundBlock.getType().isSolid() && aboveBlock.getType() == Material.AIR) {
                aboveBlock.setType(Material.PACKED_ICE);
                spikeBlocks.add(aboveBlock);
            } else if (aboveBlock.getType() == Material.AIR) {
                aboveBlock.setType(Material.PACKED_ICE);
                spikeBlocks.add(aboveBlock);
            }

            world.spawnParticle(Particle.SNOWFLAKE, target.add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.05);
        }

        // Remove spikes after 3s
        runLater(() -> {
            for (Block b : spikeBlocks) {
                if (b.getType() == Material.PACKED_ICE) {
                    b.setType(Material.AIR);
                }
            }
        }, 60L);
    }

    // ---- Ability 3: Glacial Domain (90s CD) ----
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_GlacialDomain", 90)) return;
        player.sendMessage("§f❄️ Glacial Domain!");

        UUID uuid = player.getUniqueId();
        World world = player.getWorld();
        Location center = player.getLocation();

        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.3f);

        // Build ice dome: hollow sphere of radius 8 (shell only)
        int radius = 8;
        List<Block> placed = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x*x + y*y + z*z);
                    // Shell: between radius-1 and radius (1 block thick dome)
                    if (dist >= radius - 0.9 && dist <= radius + 0.1 && y >= -1) {
                        Block b = center.clone().add(x, y, z).getBlock();
                        if (b.getType() == Material.AIR) {
                            b.setType(Material.BLUE_ICE);
                            placed.add(b);
                        }
                    }
                }
            }
        }
        domainBlocks.put(uuid, placed);

        world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0,1,0), 200, 6, 1, 6, 0.2);
        // Visible sphere outline for the dome
        AbilityUtils.spawnSphere(center.clone(), 8, Particle.SNOWFLAKE, null, 80);

        // Apply Slowness II to enemies in domain each second
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) { task.cancel(); return; }
            // Show ground ring boundary every tick
            AbilityUtils.spawnRing(center.clone().add(0, 0.2, 0), 8, Particle.SNOWFLAKE, null, 24);
            List<LivingEntity> inDomain = AbilityUtils.getNearbyTrusted(player.getLocation(), 12, player, plugin);
            for (LivingEntity e : inDomain) {
                double dx = e.getLocation().getX() - center.getX();
                double dz = e.getLocation().getZ() - center.getZ();
                if (dx*dx + dz*dz <= 12*12) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                    world.spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0,1,0), 3, 0.3, 0.5, 0.3, 0.02);
                }
            }
        }, 0, 20L);

        // End domain after 10s
        runLater(() -> {
            List<Block> blocks = domainBlocks.remove(uuid);
            if (blocks != null) restoreBlocks(blocks);
            player.sendMessage("§f❄️ Glacial Domain ended.");
            world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.5f);
        }, 200L);
    }

    // ---- Passive: Ice Runner ----
    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        Block standing = player.getLocation().subtract(0, 0.1, 0).getBlock();
        boolean onIce = standing.getType() == Material.ICE
                || standing.getType() == Material.PACKED_ICE
                || standing.getType() == Material.BLUE_ICE
                || standing.getType() == Material.FROSTED_ICE;

        boolean hadSpeed = hadSpeedFromIce.getOrDefault(uuid, false);

        if (onIce && !hadSpeed) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            hadSpeedFromIce.put(uuid, true);
        } else if (!onIce && hadSpeed) {
            player.removePotionEffect(PotionEffectType.SPEED);
            hadSpeedFromIce.put(uuid, false);
        } else if (onIce) {
            // Keep refreshing
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        }
    }

    private void restoreBlocks(List<Block> blocks) {
        for (Block b : blocks) {
            if (b.getType() == Material.BLUE_ICE || b.getType() == Material.PACKED_ICE
                    || b.getType() == Material.SNOW) {
                b.setType(Material.AIR);
            }
        }
    }
}
