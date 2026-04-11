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

import java.util.*;

public class InventoryMutation extends Mutation {

    // Tracks which players have the mutation active (for passive checks in listener)
    private static final Set<UUID> activeHolders = new HashSet<>();

    public InventoryMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override public MutationType getType() { return MutationType.INVENTORY; }

    @Override
    public void onAssign(Player player) {
        activeHolders.add(player.getUniqueId());
        player.sendMessage("§a📦 You have been granted the §aInventory Mutation§a!");
        player.sendMessage("§7Passive: Items in your inventory stack to double their normal limit.");
        player.sendMessage("§7Use §f/ability 1 §7to scramble a nearby enemy's hotbar.");
    }

    @Override
    public void onRemove(Player player) {
        activeHolders.remove(player.getUniqueId());
        // Revert oversized stacks in player's inventory back to vanilla limits
        revertInventory(player);
    }

    @Override
    public void onTick(Player player) {
        // Nothing per-tick; passive is handled via inventory events in PassiveListener
    }

    // ── A1: Scramble (200s CD) ────────────────────────────────────────────────
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_Scramble", 200)) return;
        plugin.getAdvancementManager().onDamagingAbilityUsed(player, "inventory_a1");

        Player target = getNearestEnemy(player, 15);
        if (target == null) {
            player.sendMessage("§cNo player found within 15 blocks!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A1_Scramble");
            return;
        }

        player.sendMessage("§a📦 Hotbar Scramble on §f" + target.getName() + "§a!");
        target.sendMessage("§c📦 §f" + player.getName() + " §cscrambled your hotbar!");

        // Collect all hotbar items (slots 0-8), shuffle, then put back
        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            hotbar[i] = target.getInventory().getItem(i);
        }
        List<ItemStack> shuffled = new ArrayList<>(Arrays.asList(hotbar));
        Collections.shuffle(shuffled);
        for (int i = 0; i < 9; i++) {
            target.getInventory().setItem(i, shuffled.get(i));
        }

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
        target.getWorld().spawnParticle(Particle.ITEM_SLIME,
                target.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.1);
    }

    @Override
    public void activateAbility2(Player player) {
        player.sendMessage("§7No Ability 2 for Inventory Mutation.");
    }

    @Override
    public void activateAbility3(Player player) {
        player.sendMessage("§7No Ability 3 for Inventory Mutation.");
    }

    // ── Static helpers for PassiveListener ───────────────────────────────────

    public static boolean isActive(UUID uuid) {
        return activeHolders.contains(uuid);
    }

    /**
     * Called when an item enters a player's inventory.
     * Sets the item's amount to fill the stack up to double the vanilla max.
     * This is called AFTER the item is already in inventory — we just adjust quantity.
     * The doubling is purely an in-memory effect; we cap at double vanilla max.
     */
    public static void applyDoubleStack(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        int vanillaMax = item.getType().getMaxStackSize();
        if (vanillaMax <= 1) return; // unstackable items stay unstackable
        item.setMaxStackSize(Math.min(vanillaMax * 2, 127));
    }

    /**
     * Reverts all stacks in the player's inventory back to vanilla limits.
     * Called on mutation remove or when items are dropped/put in containers.
     */
    public static void revertInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            int vanillaMax = item.getType().getMaxStackSize();
            if (vanillaMax <= 1) continue;
            // Reset max stack size to vanilla
            item.setMaxStackSize(vanillaMax);
            // If amount exceeds vanilla max, drop the excess
            if (item.getAmount() > vanillaMax) {
                int excess = item.getAmount() - vanillaMax;
                item.setAmount(vanillaMax);
                ItemStack drop = item.clone();
                drop.setAmount(excess);
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private Player getNearestEnemy(Player source, double maxDist) {
        Player nearest = null;
        double minDist = maxDist * maxDist;
        for (Player p : source.getWorld().getPlayers()) {
            if (p == source) continue;
            if (plugin.getTrustManager().isTrusted(source.getUniqueId(), p.getUniqueId())) continue;
            double d = p.getLocation().distanceSquared(source.getLocation());
            if (d < minDist) { minDist = d; nearest = p; }
        }
        return nearest;
    }

    @Override
    public void applyPassiveEffects(Player player) {}

    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_Scramble"};
    }

    @Override
    public boolean isAbilityActive(int slot, UUID uuid) {
        return false;
    }
}
