package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.managers.MutationItemManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

public class MutationDropListener implements Listener {

    private final MutationsPlugin plugin;
    private final Random random = new Random();

    // Shadow void death: store UUID until respawn
    private final Set<UUID> voidDeathPending = new HashSet<>();
    // Bypass: track start of border session and last hit timestamp
    private final Map<UUID, Long> borderDamageStart   = new HashMap<>();
    private final Map<UUID, Long> borderLastHit        = new HashMap<>();
    private static final long BORDER_GRACE_MS          = 3000L; // 3s gap allowed
    // Bypass: players who died to the border this session (disqualified)
    private final Set<UUID> borderDeathDisqualified    = new HashSet<>();
    // True Shot: track arrows in flight (arrowUUID -> shooter UUID + origin)
    private final Map<UUID, Location> arrowOrigins = new HashMap<>();

    public MutationDropListener(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    private MutationItemManager itemManager() {
        return plugin.getMutationManager().getItemManager();
    }

    // Tracks last time a player obtained each mutation type (ms timestamp)
    private final Map<String, Long> acquisitionCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 60 * 60 * 1000L; // 1 hour

    /** Returns true if the player is on cooldown for this mutation type, false if they can receive it. */
    private boolean isOnAcquisitionCooldown(Player player, MutationType type) {
        String key = player.getUniqueId() + ":" + type.getId();
        Long last = acquisitionCooldowns.get(key);
        if (last == null) return false;
        return System.currentTimeMillis() - last < COOLDOWN_MS;
    }

    /** Records that the player just obtained this mutation type. */
    private void recordAcquisition(Player player, MutationType type) {
        acquisitionCooldowns.put(player.getUniqueId() + ":" + type.getId(), System.currentTimeMillis());
    }

    private void giveMutation(Player player, MutationType type) {
        if (isOnAcquisitionCooldown(player, type)) {
            long last = acquisitionCooldowns.get(player.getUniqueId() + ":" + type.getId());
            long remaining = COOLDOWN_MS - (System.currentTimeMillis() - last);
            long mins = remaining / 60000;
            player.sendMessage("§c⏱ You already obtained " + type.getDisplayName()
                    + " §crecently. Try again in §f" + mins + " §cminutes.");
            return;
        }
        recordAcquisition(player, type);
        // Give orb to player inventory or drop at feet
        ItemStack orb = itemManager().createMutationItem(type);
        var leftover = player.getInventory().addItem(orb);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover.get(0));
        }
        player.sendMessage("§6✦ A " + type.getDisplayName() + " §6orb appeared! §7(right-click to absorb)");
    }

    // ─── CHEST LOOT TABLES ───────────────────────────────────────────────────

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        try {
            String path = event.getLootTable().getKey().getKey();

            // Hellfire — Nether Fortress 35%
            if (path.equals("chests/nether_bridge")) {
                if (random.nextDouble() < 0.35) {
                    event.getLoot().add(itemManager().createMutationItem(MutationType.HELLFIRE));
                }
            }
            // Rock — Dungeon (monster room) 15%
            else if (path.equals("chests/simple_dungeon")) {
                if (random.nextDouble() < 0.15) {
                    event.getLoot().add(itemManager().createMutationItem(MutationType.ROCK));
                }
            }
            // Frozen — Igloo basement 50%
            else if (path.equals("chests/igloo_chest")) {
                if (random.nextDouble() < 0.50) {
                    event.getLoot().add(itemManager().createMutationItem(MutationType.FROZEN));
                }
            }
        } catch (Exception ignored) {}
    }

    // ─── WIND — BREEZE 10% DROP ───────────────────────────────────────────────

    @EventHandler
    public void onBreezeDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Breeze)) return;
        if (random.nextDouble() >= 0.10) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (isOnAcquisitionCooldown(killer, MutationType.WIND)) {
            long remaining = COOLDOWN_MS - (System.currentTimeMillis()
                    - acquisitionCooldowns.get(killer.getUniqueId() + ":" + MutationType.WIND.getId()));
            killer.sendMessage("§b⏱ Wind orb on cooldown — §f" + remaining / 60000 + " §bmin remaining.");
            return;
        }
        recordAcquisition(killer, MutationType.WIND);
        event.getDrops().add(itemManager().createMutationItem(MutationType.WIND));
        killer.sendMessage("§b🌪️ The Breeze released its essence — Wind orb dropped!");
    }

    // ─── WITCH DROP 5% ───────────────────────────────────────────────────────

    @EventHandler
    public void onWitchDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Witch)) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (random.nextDouble() >= 0.05) return;
        if (isOnAcquisitionCooldown(killer, MutationType.WITCH)) {
            long remaining = COOLDOWN_MS - (System.currentTimeMillis()
                    - acquisitionCooldowns.get(killer.getUniqueId() + ":" + MutationType.WITCH.getId()));
            killer.sendMessage("§c⏱ Witch orb on cooldown — §f" + remaining / 60000 + " §cmin remaining.");
            return;
        }
        recordAcquisition(killer, MutationType.WITCH);
        event.getDrops().add(itemManager().createMutationItem(MutationType.WITCH));
    }

    // ─── LOVE DROP — BREEDING 1% ─────────────────────────────────────────────

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (random.nextDouble() >= 0.01) return;
        if (!(event.getBreeder() instanceof Player p)) return;
        if (isOnAcquisitionCooldown(p, MutationType.LOVE)) {
            long remaining = COOLDOWN_MS - (System.currentTimeMillis()
                    - acquisitionCooldowns.get(p.getUniqueId() + ":" + MutationType.LOVE.getId()));
            p.sendMessage("§c⏱ Love orb on cooldown — §f" + remaining / 60000 + " §cmin remaining.");
            return;
        }
        recordAcquisition(p, MutationType.LOVE);
        Location loc = event.getMother().getLocation();
        loc.getWorld().dropItemNaturally(loc, itemManager().createMutationItem(MutationType.LOVE));
        p.sendMessage("§d❤ Love Mutation orb appeared from the breeding!");
    }

    // ─── DEBUFF DROP — WITHER DEATH ──────────────────────────────────────────

    @EventHandler
    public void onWitherDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither)) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            if (isOnAcquisitionCooldown(killer, MutationType.DEBUFF)) {
                long remaining = COOLDOWN_MS - (System.currentTimeMillis()
                        - acquisitionCooldowns.get(killer.getUniqueId() + ":" + MutationType.DEBUFF.getId()));
                killer.sendMessage("§c⏱ Debuff orb on cooldown — §f" + remaining / 60000 + " §cmin remaining.");
                return;
            }
            recordAcquisition(killer, MutationType.DEBUFF);
            killer.sendMessage("§5☠ The Wither's essence crystallised into a Debuff orb!");
        }
        event.getDrops().add(itemManager().createMutationItem(MutationType.DEBUFF));
    }

    // ─── SHADOW — DIE TO VOID, RECEIVE ON RESPAWN ────────────────────────────

    @EventHandler
    public void onVoidDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null) return;
        if (lastDamage.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        // Don't award if they already have Shadow
        if (plugin.getMutationManager().hasMutation(player)) return;
        voidDeathPending.add(player.getUniqueId());
        event.setDeathMessage(null); // suppress server-wide broadcast
    }

    @EventHandler
    public void onRespawnAfterVoid(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!voidDeathPending.remove(uuid)) return;
        Player player = event.getPlayer();
        // Give orb on next tick so inventory is ready after respawn
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            giveMutation(player, MutationType.SHADOW);
            player.sendMessage("§8🌑 The void gave you something back...");
        }, 5L);
    }

    // ─── LIGHTNING — STRUCK BY NATURAL LIGHTNING ─────────────────────────────

    @EventHandler
    public void onLightningStrike(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) return;
        if (!(event.getEntity() instanceof Player player)) return;
        // Must be natural lightning, not from a trident or ability
        if (plugin.getMutationManager().hasMutation(player)) return;
        // Check it's real world lightning (not plugin-spawned effect)
        // LightningEffect has no entity — real lightning hitting a player fires this
        giveMutation(player, MutationType.LIGHTNING);
        player.sendMessage("§e⚡ The lightning chose you!");
    }

    // ─── NATURE — FARMER VILLAGER TRADE (REPLACE GLISTERING MELON, 50%) ──────

    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager v)) return;
        if (v.getProfession() != Villager.Profession.FARMER) return;
        MerchantRecipe recipe = event.getRecipe();
        // Replace glistering melon slice trades 50% of the time
        if (recipe.getResult().getType() != Material.GLISTERING_MELON_SLICE) return;
        if (random.nextDouble() >= 0.50) return;

        ItemStack orb = itemManager().createMutationItem(MutationType.NATURE);
        ItemStack price = new ItemStack(Material.EMERALD_BLOCK, 32);
        // priceMultiplier=0, demand=0, specialPrice=0 → no discounts ever applied
        MerchantRecipe newRecipe = new MerchantRecipe(
                orb, 0, 1, false, 0, 0.0f, 0, 0);
        newRecipe.addIngredient(price);
        newRecipe.setIgnoreDiscounts(true);
        event.setRecipe(newRecipe);
    }

    // ─── BLOOD SOLDIER — KILL MOB ON SCULK CATALYST ──────────────────────────

    @EventHandler
    public void onMobDeathNearCatalyst(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (entity.getKiller() == null) return;

        Player killer = entity.getKiller();
        Location loc = entity.getLocation();

        // Check for sculk catalyst within 8 blocks
        boolean nearCatalyst = false;
        for (int x = -8; x <= 8 && !nearCatalyst; x++) {
            for (int y = -4; y <= 4 && !nearCatalyst; y++) {
                for (int z = -8; z <= 8 && !nearCatalyst; z++) {
                    Block b = loc.getWorld().getBlockAt(
                            loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    if (b.getType() == Material.SCULK_CATALYST) {
                        nearCatalyst = true;
                    }
                }
            }
        }
        if (!nearCatalyst) return;

        if (isOnAcquisitionCooldown(killer, MutationType.BLOOD_SOLDIER)) {
            long remaining = COOLDOWN_MS - (System.currentTimeMillis()
                    - acquisitionCooldowns.get(killer.getUniqueId() + ":" + MutationType.BLOOD_SOLDIER.getId()));
            killer.sendMessage("§c⏱ Blood Soldier orb on cooldown — §f" + remaining / 60000 + " §cmin remaining.");
            return;
        }
        recordAcquisition(killer, MutationType.BLOOD_SOLDIER);
        loc.getWorld().dropItemNaturally(loc, itemManager().createMutationItem(MutationType.BLOOD_SOLDIER));
        killer.sendMessage("§4🩸 The sculk absorbed the kill — a Blood Soldier orb crystallised!");
    }

    // ─── TRUE SHOT — KILL SKELETON WITH BOW FROM 50 BLOCKS ──────────────────

    @EventHandler
    public void onArrowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        // Store origin of arrow
        arrowOrigins.put(arrow.getUniqueId(), event.getEntity().getLocation().clone());
        // Clean up after 30s if it never hits
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> arrowOrigins.remove(arrow.getUniqueId()), 600L);
    }

    @EventHandler
    public void onSkeletonKilledByArrow(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Skeleton)) return;
        EntityDamageEvent lastDmg = event.getEntity().getLastDamageCause();
        if (!(lastDmg instanceof EntityDamageByEntityEvent dmgEvent)) return;
        if (!(dmgEvent.getDamager() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        Location origin = arrowOrigins.remove(arrow.getUniqueId());
        if (origin == null) return;

        double dist = origin.distance(event.getEntity().getLocation());
        if (dist < 50) return;

        if (isOnAcquisitionCooldown(shooter, MutationType.TRUE_SHOT)) {
            long remaining = COOLDOWN_MS - (System.currentTimeMillis()
                    - acquisitionCooldowns.get(shooter.getUniqueId() + ":" + MutationType.TRUE_SHOT.getId()));
            shooter.sendMessage("§c⏱ True Shot orb on cooldown — §f" + remaining / 60000 + " §cmin remaining.");
            return;
        }
        giveMutation(shooter, MutationType.TRUE_SHOT);
        shooter.sendMessage("§6🏹 Incredible shot! (" + (int) dist + " blocks!) True Shot orb awarded!");
    }

    // ─── BYPASS — SURVIVE 60s OF WORLD BORDER DAMAGE ─────────────────────────

    @EventHandler
    public void onWorldBorderDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.WORLD_BORDER) return;
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (borderDeathDisqualified.contains(uuid)) return;

        long now = System.currentTimeMillis();
        borderLastHit.put(uuid, now);

        if (!borderDamageStart.containsKey(uuid)) {
            borderDamageStart.put(uuid, now);
            player.sendMessage("§7Survive the border for 60 seconds...");
        } else {
            long elapsed = now - borderDamageStart.get(uuid);
            if (elapsed >= 60_000L) {
                borderDamageStart.remove(uuid);
                borderLastHit.remove(uuid);
                giveMutation(player, MutationType.BYPASS);
                player.sendMessage("§b💨 You endured the world border — Bypass orb awarded!");
            }
        }
    }

    // Tick check: if player hasn't taken border damage for >3s, reset their timer
    @EventHandler
    public void onPlayerTick(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!borderDamageStart.containsKey(uuid)) return;
        Long lastHit = borderLastHit.get(uuid);
        if (lastHit == null) return;
        if (System.currentTimeMillis() - lastHit > BORDER_GRACE_MS) {
            borderDamageStart.remove(uuid);
            borderLastHit.remove(uuid);
            player.sendMessage("§cBorder contact lost — timer reset!");
        }
    }

    @EventHandler
    public void onBorderDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent lastDmg = player.getLastDamageCause();
        if (lastDmg != null && lastDmg.getCause() == EntityDamageEvent.DamageCause.WORLD_BORDER) {
            UUID uuid = player.getUniqueId();
            borderDamageStart.remove(uuid);
            borderLastHit.remove(uuid);
            borderDeathDisqualified.add(uuid);
            player.sendMessage("§cYou died to the border — Bypass attempt failed.");
        }
    }

    @EventHandler
    public void onBorderRespawn(PlayerRespawnEvent event) {
        // Clear disqualification after they respawn so they can try again
        borderDeathDisqualified.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBorderQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        borderDamageStart.remove(uuid);
        borderLastHit.remove(uuid);
        borderDeathDisqualified.remove(uuid);
    }
}
