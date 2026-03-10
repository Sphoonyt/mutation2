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

    // Air jump tracking (for poison and fire styles) — uses allowFlight trick
    private final Map<UUID, Integer> airJumpsLeft = new HashMap<>();
    private final Set<UUID> airJumpFallImmune = new HashSet<>();
    public static final int MAX_AIR_JUMPS = 2;

    // Armor style
    private final Set<UUID> ircladActive = new HashSet<>();
    private final Map<UUID, List<ArmorStand>> constructStands = new HashMap<>();

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
        airJumpsLeft.put(uuid, MAX_AIR_JUMPS);
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
        airJumpsLeft.remove(uuid);
        // Ensure flight is disabled on removal
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
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
            AbilityUtils.trueDamage(e, 3, player);
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
                AbilityUtils.trueDamage(e, 0.5, player);
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
            for (LivingEntity e : hit) AbilityUtils.trueDamage(e, 0.5, player);
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
                        AbilityUtils.trueDamage(e, 3.5, player);
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
            for (LivingEntity e : hit) AbilityUtils.trueDamage(e, 3, player); // 3 AOE

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
        if (style == Style.ARMOR) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        UUID uuid = player.getUniqueId();

        if (player.isOnGround()) {
            // Reset jumps on landing
            airJumpsLeft.put(uuid, MAX_AIR_JUMPS);
            airJumpFallImmune.remove(uuid);
            if (player.getAllowFlight() && !player.isFlying()) {
                player.setAllowFlight(false);
            }
        } else {
            // Airborne with jumps remaining: enable allowFlight so space triggers toggle event
            int left = airJumpsLeft.getOrDefault(uuid, 0);
            if (left > 0 && !player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        }
    }

    /**
     * Called by PassiveListener's PlayerToggleFlightEvent handler.
     * Returns true if a jump was consumed.
     */
    public boolean tryAirJump(Player player) {
        if (style == Style.ARMOR) return false;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        if (player.isOnGround()) return false;

        UUID uuid = player.getUniqueId();
        int left = airJumpsLeft.getOrDefault(uuid, 0);
        if (left <= 0) return false;

        airJumpsLeft.put(uuid, left - 1);

        // 2 blocks up + 1 block forward in look direction
        Vector lookFlat = player.getEyeLocation().getDirection().clone();
        lookFlat.setY(0);
        if (lookFlat.lengthSquared() > 0) lookFlat.normalize().multiply(0.13);
        Vector vel = lookFlat;
        vel.setY(0.55); // ~2 blocks up
        player.setVelocity(vel);
        player.setFallDistance(0f);
        airJumpFallImmune.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> airJumpFallImmune.remove(player.getUniqueId()), 200L);

        World world = player.getWorld();

        if (style == Style.POISON) {
            world.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.6f, 2f);
            world.spawnParticle(Particle.ITEM_SLIME, player.getLocation(), 15, 0.5, 0.3, 0.5, 0.05);
            List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(player.getLocation(), 3, player, plugin);
            for (LivingEntity e : hit) {
                AbilityUtils.trueDamage(e, 1, player);
                e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20, 0, false, false));
            }
        } else if (style == Style.FIRE) {
            world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 2f);
            world.spawnParticle(Particle.FLAME, player.getLocation(), 15, 0.5, 0.3, 0.5, 0.1);
            List<LivingEntity> hit = AbilityUtils.getNearbyTrusted(player.getLocation(), 3, player, plugin);
            for (LivingEntity e : hit) {
                AbilityUtils.trueDamage(e, 1, player);
                e.setFireTicks(20);
            }
        }

        player.sendMessage("§6🐉 Double Jump! §7(" + (left - 1) + " jumps left)");

        if (left - 1 <= 0) {
            player.setAllowFlight(false);
        }
        return true;
    }

    public boolean isAirJumpFallImmune(UUID uuid) { return airJumpFallImmune.contains(uuid); }

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

    @Override
    public void applyPassiveEffects(Player target) {
        switch (style) {
            case POISON -> {
                // Venom body: absorption and speed
                target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 30, 0, false, false), true);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      30, 0, false, false), true);
            }
            case FIRE -> {
                // Dragon fire: fire resistance and strength
                target.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, false, false), true);
                target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,         30, 0, false, false), true);
            }
            case ARMOR -> {
                // Iron scales: heavy resistance and absorption
                target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, false, false), true);
                target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 30, 1, false, false), true);
            }
        }
    }

}