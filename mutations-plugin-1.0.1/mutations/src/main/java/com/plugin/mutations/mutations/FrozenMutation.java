package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class FrozenMutation extends Mutation {

    private final Map<UUID, Boolean> hadSpeedFromIce = new HashMap<>();
    private final Map<UUID, List<Block>> domainBlocks = new HashMap<>();

    /** Global set of blocks placed by Frozen abilities — cannot be broken by players */
    public static final Set<Location> PROTECTED_BLOCKS = new HashSet<>();

    private static final Set<Material> ICE_TYPES = EnumSet.of(
        Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE
    );

    public FrozenMutation(MutationsPlugin plugin) { super(plugin); }

    @Override public MutationType getType() { return MutationType.FROZEN; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§f❄️ You have been granted the §fFrozen Mutation§f!");
        player.sendMessage("§7Passive: Ice Runner - Speed II on any ice");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        hadSpeedFromIce.remove(uuid);
        List<Block> blocks = domainBlocks.remove(uuid);
        if (blocks != null) restoreBlocks(blocks);
    }

    // ---- Ability 1: Frozen Recovery (30s CD) ----
    // Heals self 3 hearts over 3s, gives Hunger 255 for 3s to self + all players within 10 blocks
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_FrozenRec", 30)) return;

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);

        // Gather allies within 10 blocks (including self)
        List<Player> targets = new ArrayList<>();
        targets.add(player);
        for (org.bukkit.entity.Entity e : world.getNearbyEntities(player.getLocation(), 10, 10, 10)) {
            if (e instanceof Player nearby && nearby != player) targets.add(nearby);
        }

        player.sendMessage("§f❄️ Frozen Recovery! Feeding " + targets.size() + " player(s) within 10 blocks.");

        // Apply hunger 255 (Saturation 254 for 60 ticks) + resistance to all targets
        for (Player target : targets) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 60, 254, false, false));
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1, false, false));

            // Per-target particle burst
            world.spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
            world.spawnParticle(Particle.HEART,     target.getLocation().add(0, 2, 0), 6,  0.3, 0.3, 0.3, 0);

            if (target != player) target.sendMessage("§f❄️ " + player.getName() + "'s Frozen Recovery fed you!");
        }

        // Big AoE ring visual
        world.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 120, 0.8, 1.2, 0.8, 0.15);
        AbilityUtils.spawnRing(player.getLocation(), 10, Particle.SNOWFLAKE, null, 48);
        AbilityUtils.spawnRing(player.getLocation(), 5,  Particle.END_ROD,   null, 24);
        world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.03);

        // Heal caster 3 hearts over 3 seconds
        final int[] healCount = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || healCount[0] >= 3) {
                task.cancel();
                if (player.isOnline()) player.sendMessage("§f❄️ Frozen Recovery faded.");
                return;
            }
            player.setHealth(Math.min(
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                player.getHealth() + 2.0));
            world.spawnParticle(Particle.HEART,     player.getLocation().add(0, 2, 0), 5,  0.3, 0.3, 0.3, 0);
            world.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 25, 0.5, 0.9, 0.5, 0.08);
            world.spawnParticle(Particle.END_ROD,   player.getLocation().add(0, 1, 0), 8,  0.3, 0.8, 0.3, 0.02);
            healCount[0]++;
        }, 20L, 20L);
    }

    // ---- Ability 2: Ice Spike Line (14s CD) ----
    // Erupts a line of 3-block-tall ice spikes from the ground in front of the player.
    // Each spike column grows upward over 3 ticks, damages nearby entities, then melts after 3s.
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_IceSpike", 14)) return;
        player.sendMessage("§f❄️ Ice Spike Line!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        world.playSound(player.getLocation(), Sound.BLOCK_SNOW_BREAK,  1f, 0.5f);

        Location origin = player.getLocation();
        Vector dir = origin.getDirection().clone();
        dir.setY(0);
        dir.normalize();

        final int NUM_SPIKES = 8;   // spikes in the line
        final int SPIKE_HEIGHT = 3; // blocks tall each spike grows
        final long MELT_TICKS  = 80L; // how long spikes stay (4s)

        for (int i = 1; i <= NUM_SPIKES; i++) {
            final int step = i;
            // Stagger each spike by 3 ticks so they erupt in sequence
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Find ground at this position
                Location col = origin.clone().add(dir.clone().multiply(step));
                col = findGround(col);
                if (col == null) return;

                final Location groundLoc = col;
                List<Block> spike = new ArrayList<>();

                // Grow spike upward over SPIKE_HEIGHT ticks
                for (int h = 1; h <= SPIKE_HEIGHT; h++) {
                    final int height = h;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        Block b = groundLoc.clone().add(0, height, 0).getBlock();
                        if (b.getType() == Material.AIR || b.getType() == Material.WATER) {
                            b.setType(height == SPIKE_HEIGHT ? Material.PACKED_ICE : Material.BLUE_ICE);
                            spike.add(b);
                            PROTECTED_BLOCKS.add(b.getLocation());
                        }
                        // Burst particles as it grows
                        world.spawnParticle(Particle.SNOWFLAKE, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.25, 0.1, 0.25, 0.04);
                        world.spawnParticle(Particle.END_ROD,   b.getLocation().add(0.5, 0.5, 0.5),  5, 0.15, 0.1, 0.15, 0.02);
                    }, (long)(h - 1));
                }

                // Damage + slow entities at spike column (fires once spike is full)
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    Location hitCenter = groundLoc.clone().add(0, 1.5, 0);
                    List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(hitCenter, 1.0, player, plugin);
                    for (LivingEntity e : hit) {
                        AbilityUtils.trueDamage(e, 3, player);
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        world.spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
                        world.spawnParticle(Particle.END_ROD,   e.getLocation().add(0, 1, 0), 12, 0.2, 0.5, 0.2, 0.04);
                        world.spawnParticle(Particle.CRIT,      e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
                        world.playSound(e.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
                    }
                    // Ground crack effect
                    world.spawnParticle(Particle.SNOWFLAKE, groundLoc.clone().add(0.5, 0.2, 0.5), 20, 0.5, 0, 0.5, 0.06);
                    world.playSound(groundLoc, Sound.BLOCK_SNOW_PLACE, 0.8f, 1.8f);
                }, (long)(SPIKE_HEIGHT));

                // Melt the spike after MELT_TICKS
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    for (Block b : spike) {
                        PROTECTED_BLOCKS.remove(b.getLocation());
                        if (b.getType() == Material.BLUE_ICE || b.getType() == Material.PACKED_ICE) {
                            b.setType(Material.AIR);
                            world.spawnParticle(Particle.CLOUD, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }, MELT_TICKS);

            }, (long)(i * 3));
        }
    }

    /** Walk downward from loc to find the first solid ground block; returns the air block just above it. */
    private Location findGround(Location loc) {
        Location check = loc.clone();
        // Scan up to 5 blocks down, 3 blocks up to handle terrain variation
        for (int dy = 2; dy >= -5; dy--) {
            Block b = check.clone().add(0, dy, 0).getBlock();
            Block above = check.clone().add(0, dy + 1, 0).getBlock();
            if (b.getType().isSolid() && (above.getType() == Material.AIR || above.getType() == Material.WATER)) {
                return above.getLocation();
            }
        }
        return null; // over void / deep water
    }

    // ---- Ability 3: Glacial Domain (90s CD) ----
    // Ice dome; ground inside becomes ice; enemies get Slowness II each tick
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_GlacialDomain", 90)) return;
        player.sendMessage("§f❄️ Glacial Domain!");

        UUID uuid = player.getUniqueId();
        World world = player.getWorld();
        Location center = player.getLocation().clone();

        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.3f);
        world.playSound(center, Sound.BLOCK_SNOW_BREAK,  1.2f, 0.4f);

        // Build dome + freeze ground within radius 8
        int radius = 8;
        List<Block> placed = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x*x + y*y + z*z);
                    // Dome shell
                    if (dist >= radius - 0.9 && dist <= radius + 0.1 && y >= -1) {
                        Block b = center.clone().add(x, y, z).getBlock();
                        if (b.getType() == Material.AIR) {
                            b.setType(Material.BLUE_ICE);
                            placed.add(b);
                            PROTECTED_BLOCKS.add(b.getLocation());
                        }
                    }
                    // Freeze ground (y=0 level) inside the dome
                    if (y == 0 && dist <= radius - 1) {
                        Block ground = center.clone().add(x, -1, z).getBlock();
                        if (ground.getType().isSolid() && !ICE_TYPES.contains(ground.getType())) {
                            Block surface = center.clone().add(x, 0, z).getBlock();
                            if (surface.getType() == Material.AIR) {
                                surface.setType(Material.BLUE_ICE);
                                placed.add(surface);
                                PROTECTED_BLOCKS.add(surface.getLocation());
                            }
                        }
                    }
                }
            }
        }
        domainBlocks.put(uuid, placed);

        // Big initial burst
        world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1, 0), 300, 8, 2, 8, 0.25);
        world.spawnParticle(Particle.END_ROD,   center.clone().add(0, 1, 0), 80,  8, 2, 8, 0.1);
        AbilityUtils.spawnSphere(center.clone(), 8, Particle.SNOWFLAKE, null, 100);
        AbilityUtils.spawnRing(center.clone(), 8, Particle.END_ROD, null, 40);

        // Per-second tick: slowness on enemies + visual ring
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !domainBlocks.containsKey(uuid)) { task.cancel(); return; }
            AbilityUtils.spawnRing(center.clone().add(0, 0.3, 0), 8, Particle.SNOWFLAKE, null, 28);
            AbilityUtils.spawnRing(center.clone().add(0, 0.6, 0), 6, Particle.END_ROD,   null, 18);

            List<LivingEntity> inDomain = AbilityUtils.getNearbyTrusted(center, 9, player, plugin);
            for (LivingEntity e : inDomain) {
                double dx = e.getLocation().getX() - center.getX();
                double dz = e.getLocation().getZ() - center.getZ();
                if (dx*dx + dz*dz <= 9*9) {
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
                    world.spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0,1,0), 8, 0.3, 0.5, 0.3, 0.03);
                }
            }
        }, 0, 20L);

        // End after 10s
        runLater(() -> {
            List<Block> blocks = domainBlocks.remove(uuid);
            if (blocks != null) restoreBlocks(blocks);
            player.sendMessage("§f❄️ Glacial Domain ended.");
            world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.5f);
            world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0,1,0), 150, 6, 2, 6, 0.2);
        }, 200L);
    }

    // ---- Passive: Ice Runner — Speed II on any ice type ----
    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        Block standing = player.getLocation().subtract(0, 0.1, 0).getBlock();
        boolean onIce = ICE_TYPES.contains(standing.getType());
        boolean hadSpeed = hadSpeedFromIce.getOrDefault(uuid, false);

        if (onIce) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            hadSpeedFromIce.put(uuid, true);
        } else if (hadSpeed) {
            player.removePotionEffect(PotionEffectType.SPEED);
            hadSpeedFromIce.put(uuid, false);
        }
    }

    private void restoreBlocks(List<Block> blocks) {
        for (Block b : blocks) {
            PROTECTED_BLOCKS.remove(b.getLocation());
            if (ICE_TYPES.contains(b.getType())) b.setType(Material.AIR);
        }
    }

    @Override
    public void applyPassiveEffects(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,   30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false), true);
    }

    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_FrozenRec", "A2_IceSpike", "A3_GlacialDomain"};
    }

    @Override
    public boolean isAbilityActive(int slot, java.util.UUID uuid) {
        if (slot == 3) return domainBlocks.containsKey(uuid);
        return false;
    }
}
