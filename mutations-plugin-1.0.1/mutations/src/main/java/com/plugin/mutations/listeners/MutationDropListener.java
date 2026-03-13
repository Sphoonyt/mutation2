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
import org.bukkit.loot.LootTables;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

public class MutationDropListener implements Listener {

    private final MutationsPlugin plugin;
    private final Random random = new Random();

    // Shadow void death: store UUID until respawn
    private final Set<UUID> voidDeathPending = new HashSet<>();
    // Bypass: track how long player has been taking world border damage (ticks)
    private final Map<UUID, Long> borderDamageStart = new HashMap<>();
    // True Shot: track arrows in flight (arrowUUID -> shooter UUID + origin)
    private final Map<UUID, Location> arrowOrigins = new HashMap<>();

    public MutationDropListener(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    private MutationItemManager itemManager() {
        return plugin.getMutationManager().getItemManager();
    }

    private void giveMutation(Player player, MutationType type) {
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
        LootTables table = null;
        try {
            // NamespacedKey comparison for known tables
            NamespacedKey key = event.getLootTable().getKey();
            String path = key.getKey();

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
            // Wind — Ominous Vault (Trial Chamber) 10%
            else if (path.equals("chests/trial_chambers/reward_ominous")
                    || path.contains("ominous_vault")) {
                if (random.nextDouble() < 0.10) {
                    event.getLoot().add(itemManager().createMutationItem(MutationType.WIND));
                }
            }
        } catch (Exception ignored) {}
    }

    // ─── WITCH DROP 5% ───────────────────────────────────────────────────────

    @EventHandler
    public void onWitchDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Witch)) return;
        if (random.nextDouble() < 0.05) {
            event.getDrops().add(itemManager().createMutationItem(MutationType.WITCH));
        }
    }

    // ─── LOVE DROP — BREEDING 1% ─────────────────────────────────────────────

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (random.nextDouble() < 0.01) {
            Location loc = event.getMother().getLocation();
            loc.getWorld().dropItemNaturally(loc, itemManager().createMutationItem(MutationType.LOVE));
            if (event.getBreeder() instanceof Player p) {
                p.sendMessage("§d❤ Love Mutation orb appeared from the breeding!");
            }
        }
    }

    // ─── DEBUFF DROP — WITHER DEATH ──────────────────────────────────────────

    @EventHandler
    public void onWitherDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither)) return;
        event.getDrops().add(itemManager().createMutationItem(MutationType.DEBUFF));
        Player killer = event.getEntity().getKiller();
        if (killer != null) killer.sendMessage("§5☠ The Wither's essence crystallised into a Debuff orb!");
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
        event.setDeathMessage("§8" + player.getName() + " fell into the void and found something in the darkness...");
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
        MerchantRecipe newRecipe = new MerchantRecipe(
                orb, 0, 1, false, 0, 1.0f, 0, 0);
        newRecipe.addIngredient(new ItemStack(Material.EMERALD_BLOCK, 32));
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

        // Drop Blood Soldier orb at kill location (not given to killer directly — they pick it up)
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

        giveMutation(shooter, MutationType.TRUE_SHOT);
        shooter.sendMessage("§6🏹 Incredible shot! (" + (int) dist + " blocks!) True Shot orb awarded!");
    }

    // ─── BYPASS — SURVIVE 60s OF WORLD BORDER DAMAGE ─────────────────────────

    @EventHandler
    public void onWorldBorderDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.WORLD_BORDER) return;
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (!borderDamageStart.containsKey(uuid)) {
            // Start tracking
            borderDamageStart.put(uuid, System.currentTimeMillis());
            player.sendMessage("§7Survive the border for 60 seconds...");
        } else {
            long elapsed = System.currentTimeMillis() - borderDamageStart.get(uuid);
            if (elapsed >= 60_000L) {
                borderDamageStart.remove(uuid);
                giveMutation(player, MutationType.BYPASS);
                player.sendMessage("§b💨 You endured the world border — Bypass orb awarded!");
            }
        }
    }

    @EventHandler
    public void onBorderDamageStop(EntityDamageEvent event) {
        // If player takes damage from something OTHER than border, reset timer
        if (event.getCause() == EntityDamageEvent.DamageCause.WORLD_BORDER) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (borderDamageStart.containsKey(player.getUniqueId())) {
            borderDamageStart.remove(player.getUniqueId());
            player.sendMessage("§cBorder survival interrupted — timer reset!");
        }
    }

    @EventHandler
    public void onBorderQuit(PlayerQuitEvent event) {
        borderDamageStart.remove(event.getPlayer().getUniqueId());
    }
}
