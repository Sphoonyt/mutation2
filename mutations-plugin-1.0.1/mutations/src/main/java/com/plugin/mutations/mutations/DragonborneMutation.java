package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class DragonborneMutation extends Mutation {

    public enum Style { POISON, FIRE, ARMOR }

    private final Style style;

    // Air jump tracking (for poison and fire styles)
    private final Map<UUID, Integer> airJumpsUsed = new HashMap<>();
    private final Map<UUID, Double> prevYVelocity = new HashMap<>();
    private final Map<UUID, Boolean> prevOnGround = new HashMap<>();
    private final Map<UUID, Long> lastAirJumpTime = new HashMap<>();

    // Armor style
    private final Set<UUID> ircladActive = new HashSet<>();
    private final Map<UUID, List<ArmorStand>> constructStands = new HashMap<>();

    private static final long AIR_JUMP_CD_MS = 1500;
    private static final int MAX_AIR_JUMPS = 2;

    public DragonborneMutation(MutationsPlugin plugin, Style style) {
        super(plugin);
        this.style = style;
    }

    @Override
    public MutationType getType() {
        return switch (style) {
            case POISON -> MutationType.DRAGONBORNE_POISON;
            case FIRE -> MutationType.DRAGONBORNE_FIRE;
            case ARMOR -> MutationType.DRAGONBORNE_ARMOR;
        };
    }

    @Override
    public void onAssign(Player player) {
        switch (style) {
            case POISON -> assignPoison(player);
            case FIRE -> assignFire(player);
            case ARMOR -> assignArmor(player);
        }
        UUID uuid = player.getUniqueId();
        airJumpsUsed.put(uuid, 0);
        prevYVelocity.put(uuid, 0.0);
        prevOnGround.put(uuid, true);
    }

    private void assignPoison(Player player) {
        player.sendMessage("§2🐉 Dragonborne (Poison Style) granted!");
        player.sendMessage("§7Passive: Venomborne Stride - 2 air jumps with poison puffs");
    }

    private void assignFire(Player player) {
        player.sendMessage("§c🐉 Dragonborne (Fire Style) granted!");
        player.sendMessage("§7Passive: Emberflight - 2 air jumps with fire damage below");
    }

    private void assignArmor(Player player) {
        player.sendMessage("§8🐉 Dragonborne (Armor Style) granted!");
        player.sendMessage("§7Passive: Dragon Scale Resilience - 8% dmg reduction, retaliate weakness");
    }

    @Override
    public void onRemove(Player player) {
        UUID uuid = player.getUniqueId();
        airJumpsUsed.remove(uuid);
        prevYVelocity.remove(uuid);
        prevOnGround.remove(uuid);
        lastAirJumpTime.remove(uuid);
        ircladActive.remove(uuid);
        cleanupConstruct(uuid);
    }

    @Override
    public void activateAbility1(Player player) {
        switch (style) {
            case POISON -> venomBurst(player);
            case FIRE -> blazingEruption(player);
            case ARMOR -> ironcladSurge(player);
        }
    }

    @Override
    public void activateAbility2(Player player) {
        switch (style) {
            case POISON -> toxicFangStrike(player);
            case FIRE -> flameTalonShot(player);
            case ARMOR -> steelFistBarrage(player);
        }
    }

    @Override
    public void activateAbility3(Player player) {
        switch (style) {
            case POISON -> serpentGlide(player);
            case FIRE -> infernoLeap(player);
            case ARMOR -> armorConstruct(player);
        }
    }

    // ========== POISON STYLE ==========

    private void venomBurst(Player player) {
        if (!checkCooldown(player, "A1_VenomBurst", 30)) return;
        player.sendMessage("§2☠️ Venom Burst!");
        World world = player.getWorld();
        Location loc = player.getLocation();

        world.playSound(loc, Sound.ENTITY_SLIME_ATTACK, 1f, 0.5f);
        world.spawnParticle(Particle.ITEM_SLIME, loc.add(0, 1, 0), 60, 2, 1, 2, 0.1);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(loc, 4, player, plugin);
        for (LivingEntity e : nearby) {
            e.damage(6, player);
            e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0));
        }

        // Final venom burst after initial hits
        runLater(() -> {
            if (!player.isOnline()) return;
            List<LivingEntity> finalHit = AbilityUtils.getNearbyTrusted(player.getLocation(), 4, player, plugin);
            for (LivingEntity e : finalHit) {
                e.damage(10, player); // 5 hearts
                world.spawnParticle(Particle.ITEM_SLIME, e.getLocation().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.05);
            }
            world.playSound(player.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.3f);
            player.sendMessage("§2Final Venom Burst!");
        }, 60L);
    }

    private void toxicFangStrike(Player player) {
        if (!checkCooldown(player, "A2_ToxicFang", 14)) return;
        player.sendMessage("§2☠️ Toxic Fang Strike!");
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 1.5f);

        Snowball proj = player.launchProjectile(Snowball.class);
        proj.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2));
        proj.setMetadata("toxic_fang", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (proj.isDead() || !proj.isValid()) { task.cancel(); return; }
            world.spawnParticle(Particle.ITEM_SLIME, proj.getLocation(), 3, 0.1, 0.1, 0.1, 0);
        }, 0, 1);
    }

    private void serpentGlide(Player player) {
        if (!checkCooldown(player, "A3_SerpentGlide", 12)) return;
        player.sendMessage("§2🐍 Serpent Glide!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 1.5f);

        Vector dir = player.getEyeLocation().getDirection().normalize();
        dir.setY(Math.max(dir.getY(), 0.1));
        player.setVelocity(dir.multiply(1.3));

        // Venom trail - runs for max 4s (80 ticks), stops when landing
        final long glideStart = System.currentTimeMillis();
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) { task.cancel(); return; }
            if (System.currentTimeMillis() - glideStart > 4000) { task.cancel(); return; }
            if (player.isOnGround()) { task.cancel(); return; }

            Location trailLoc = player.getLocation();
            world.spawnParticle(Particle.ITEM_SLIME, trailLoc, 5, 0.2, 0.1, 0.2, 0.02);

            List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(trailLoc, 1.5, player, plugin);
            for (LivingEntity e : hit) {
                e.damage(1, player);
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, false));
            }
        }, 0, 3);
    }

    // ========== FIRE STYLE ==========

    private void blazingEruption(Player player) {
        if (!checkCooldown(player, "A1_BlazingErupt", 18)) return;
        player.sendMessage("§c🔥 Blazing Eruption!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        world.spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 80, 2, 1, 2, 0.15);

        List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 6, player, plugin);
        for (LivingEntity e : nearby) {
            e.damage(8, player); // 4 hearts
            e.setFireTicks(80);  // 4s burn
        }
    }

    private void flameTalonShot(Player player) {
        if (!checkCooldown(player, "A2_FlameTalon", 16)) return;
        player.sendMessage("§c🔥 Flame Talon Shot!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.2f);

        Snowball proj = player.launchProjectile(Snowball.class);
        proj.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2.5));
        proj.setMetadata("flame_talon", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        proj.setMetadata("flame_talon_explode", new FixedMetadataValue(plugin, true));

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (proj.isDead() || !proj.isValid()) { task.cancel(); return; }
            world.spawnParticle(Particle.FLAME, proj.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
        }, 0, 1);
    }

    private void infernoLeap(Player player) {
        if (!checkCooldown(player, "A3_InfernoLeap", 12)) return;
        player.sendMessage("§c🔥 Inferno Leap!");

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);

        Vector dir = player.getEyeLocation().getDirection().normalize();
        dir.setY(Math.max(dir.getY() + 0.4, 0.5));
        player.setVelocity(dir.multiply(1.1));

        // Fire on ground where player leaps from
        Location startLoc = player.getLocation().clone();
        spawnFireGround(player, startLoc, 3, 80);
    }

    private void spawnFireGround(Player player, Location center, int radius, long durationTicks) {
        List<org.bukkit.block.Block> fireBlocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x*x + z*z > radius*radius) continue;
                org.bukkit.block.Block b = center.clone().add(x, 0, z).getBlock();
                if (b.getType() == Material.AIR) {
                    b.setType(Material.FIRE);
                    fireBlocks.add(b);
                }
            }
        }
        // DPS: damage entities in fire area
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(center, radius, player, plugin);
            for (LivingEntity e : hit) e.damage(1, player);
        }, 0, 20L);

        runLater(() -> {
            for (org.bukkit.block.Block b : fireBlocks) {
                if (b.getType() == Material.FIRE) b.setType(Material.AIR);
            }
        }, durationTicks);
    }

    // ========== ARMOR STYLE ==========

    private void ironcladSurge(Player player) {
        if (!checkCooldown(player, "A1_Ironclad", 35)) return;
        UUID uuid = player.getUniqueId();
        ircladActive.add(uuid);
        player.sendMessage("§8🛡️ Ironclad Surge! (5s)");

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0));

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.7f);
        world.spawnParticle(Particle.CRIT, player.getLocation().add(0,1,0), 30, 0.5, 1, 0.5, 0.2);

        // On end: fragment burst
        runLater(() -> {
            if (!player.isOnline()) return;
            ircladActive.remove(uuid);
            player.sendMessage("§8🛡️ Fragment Burst!");
            world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            world.spawnParticle(Particle.CRIT, player.getLocation().add(0,1,0), 60, 2, 1, 2, 0.3);

            List<LivingEntity> nearby = AbilityUtils.getNearbyTrusted(player.getLocation(), 4, player, plugin);
            for (LivingEntity e : nearby) {
                e.damage(10, player); // 5 hearts (2.5 hearts = 5 hp)
                AbilityUtils.applyStun(e, 20);
            }
        }, 100L);
    }

    private void steelFistBarrage(Player player) {
        if (!checkCooldown(player, "A2_SteelFist", 16)) return;
        player.sendMessage("§8🛡️ Steel Fist Barrage!");

        World world = player.getWorld();

        for (int i = 0; i < 3; i++) {
            final int punch = i;
            runLater(() -> {
                if (!player.isOnline()) return;
                List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(
                        player.getLocation().add(player.getLocation().getDirection().multiply(2)), 2.5, player, plugin);

                for (LivingEntity e : hit) {
                    if (punch < 2) {
                        e.damage(5, player); // 2.5 dmg each
                        world.spawnParticle(Particle.CRIT, e.getLocation().add(0,1,0), 8, 0.3, 0.5, 0.3, 0.1);
                    } else {
                        // Final punch: 3.5 dmg + knockback + armor toughness boost
                        e.damage(7, player);
                        Vector dir = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                        dir.setY(0.3);
                        e.setVelocity(dir.multiply(0.9)); // 5-block knockback
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, e.getLocation(), 3, 0.3, 0.3, 0.3, 0.1);
                    }
                }
                world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1 + punch * 0.3f);
            }, i * 8L);
        }
    }

    private void armorConstruct(Player player) {
        if (!checkCooldown(player, "A3_ArmorConstruct", 30)) return;
        player.sendMessage("§8🛡️ Armor Construct summoned! (5s)");

        UUID uuid = player.getUniqueId();
        World world = player.getWorld();
        cleanupConstruct(uuid);

        List<ArmorStand> stands = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(player.getLocation().add(0, 1, 0), EntityType.ARMOR_STAND);
            stand.setHelmet(new ItemStack(Material.IRON_HELMET));
            stand.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            stand.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            stand.setBoots(new ItemStack(Material.IRON_BOOTS));
            stand.setGravity(false);
            stand.setVisible(true);
            stand.setInvulnerable(true);      // can't be broken
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setPersistent(false);       // removed on server stop
            stands.add(stand);
        }
        constructStands.put(uuid, stands);

        // Revolve around player
        final double[] angle = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || stands.stream().anyMatch(Entity::isDead)) {
                task.cancel();
                cleanupConstruct(uuid);
                return;
            }

            for (int i = 0; i < stands.size(); i++) {
                double a = angle[0] + (i * (2 * Math.PI / 3));
                Location standLoc = player.getLocation().clone().add(
                        Math.cos(a) * 2, 0.5, Math.sin(a) * 2
                );
                stands.get(i).teleport(standLoc);
            }
            angle[0] += 0.3;

            // Block attacks for nearby allies
            List<LivingEntity> allies = AbilityUtils.getNearbyTrusted(player.getLocation(), 3, player, plugin);
            // Damage absorbed is handled in passive listener

        }, 0, 1);

        // Remove after 5s
        runLater(() -> {
            if (!player.isOnline()) return;
            // Destruction burst
            world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            world.spawnParticle(Particle.CRIT, player.getLocation().add(0,1,0), 40, 2, 1, 2, 0.2);
            List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(player.getLocation(), 3, player, plugin);
            for (LivingEntity e : hit) e.damage(6, player); // 3 AOE

            cleanupConstruct(uuid);
            player.sendMessage("§8Armor Construct collapsed!");
        }, 100L);
    }

    private void cleanupConstruct(UUID uuid) {
        List<ArmorStand> stands = constructStands.remove(uuid);
        if (stands != null) stands.forEach(Entity::remove);
    }

    // ========== PASSIVES ==========

    @Override
    public void onTick(Player player) {
        // Air jump tracking for Poison and Fire styles
        if (style == Style.ARMOR) return;

        UUID uuid = player.getUniqueId();
        boolean onGround = player.isOnGround();
        boolean wasOnGround = prevOnGround.getOrDefault(uuid, true);
        double prevY = prevYVelocity.getOrDefault(uuid, 0.0);
        double currY = player.getVelocity().getY();

        if (onGround) airJumpsUsed.put(uuid, 0);

        if (!onGround && !wasOnGround && prevY < 0.1 && currY > 0.35) {
            int jumps = airJumpsUsed.getOrDefault(uuid, 0);
            long now = System.currentTimeMillis();
            long lastJump = lastAirJumpTime.getOrDefault(uuid, 0L);

            if (jumps < MAX_AIR_JUMPS && (now - lastJump) >= AIR_JUMP_CD_MS) {
                airJumpsUsed.put(uuid, jumps + 1);
                lastAirJumpTime.put(uuid, now);

                Vector vel = player.getVelocity();
                vel.setY(0.65);
                player.setVelocity(vel);
                player.setFallDistance(0f);

                World world = player.getWorld();

                if (style == Style.POISON) {
                    // Poison puff: 1 damage to nearby
                    world.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.5f, 2f);
                    world.spawnParticle(Particle.ITEM_SLIME, player.getLocation(), 15, 0.5, 0.3, 0.5, 0.05);
                    List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(player.getLocation(), 3, player, plugin);
                    for (LivingEntity e : hit) {
                        e.damage(2, player);
                        e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20, 0, false, false));
                    }
                } else if (style == Style.FIRE) {
                    // Fire damage below
                    world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 2f);
                    world.spawnParticle(Particle.FLAME, player.getLocation(), 15, 0.5, 0.3, 0.5, 0.1);
                    List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(player.getLocation(), 3, player, plugin);
                    for (LivingEntity e : hit) {
                        e.damage(2, player);
                        e.setFireTicks(20);
                    }
                }
            }
        }

        prevOnGround.put(uuid, onGround);
        prevYVelocity.put(uuid, currY);
    }

    @Override
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {
        if (style == Style.ARMOR) {
            // Dragon Scale Resilience: 8% damage reduction
            event.setDamage(event.getDamage() * 0.92);

            // If damage < 2, no knockback
            if (event.getFinalDamage() < 4) { // < 2 hearts = < 4 hp
                // Can't fully cancel knockback server-side, but we can reset velocity after
                runLater(() -> {
                    if (player.isOnline()) {
                        Vector vel = player.getVelocity();
                        vel.setX(vel.getX() * 0.1);
                        vel.setZ(vel.getZ() * 0.1);
                        player.setVelocity(vel);
                    }
                }, 1L);
            }

            // Give attacker Weakness I for 2s
            if (event.getDamager() instanceof LivingEntity attacker) {
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0));
            }
        }
    }
}
