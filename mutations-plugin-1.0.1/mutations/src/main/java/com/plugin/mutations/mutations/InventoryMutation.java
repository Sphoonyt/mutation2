package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class InventoryMutation extends Mutation {

    private static final Set<UUID> activeHolders = new HashSet<>();
    // Default inventory max stack size
    private static final int VANILLA_INV_MAX = 64;
    private static final int DOUBLE_INV_MAX  = 99; // server cap at 99 for safety

    public InventoryMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override public MutationType getType() { return MutationType.INVENTORY; }

    @Override
    public void onAssign(Player player) {
        activeHolders.add(player.getUniqueId());
        applyDoubleStack(player);
        player.sendMessage("§a📦 You have been granted the §aInventory Mutation§a!");
        player.sendMessage("§7Passive: Items in your inventory stack to double their normal limit.");
        player.sendMessage("§7Use §f/ability 1 §7to scramble a nearby enemy's hotbar.");
    }

    @Override
    public void onRemove(Player player) {
        activeHolders.remove(player.getUniqueId());
        revertInventory(player);
    }

    @Override
    public void onTick(Player player) {
        // Re-apply every few seconds to catch any newly added items
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

        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) hotbar[i] = target.getInventory().getItem(i);
        List<ItemStack> shuffled = new ArrayList<>(Arrays.asList(hotbar));
        Collections.shuffle(shuffled);
        for (int i = 0; i < 9; i++) target.getInventory().setItem(i, shuffled.get(i));

        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);
        target.getWorld().spawnParticle(Particle.ITEM_SLIME,
                target.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.1);
    }

    @Override public void activateAbility2(Player player) { player.sendMessage("§7No Ability 2."); }
    @Override public void activateAbility3(Player player) { player.sendMessage("§7No Ability 3."); }

    // ── Static helpers ────────────────────────────────────────────────────────

    public static boolean isActive(UUID uuid) {
        return activeHolders.contains(uuid);
    }

    /**
     * Raises the inventory's max stack size to 99, allowing items to stack higher.
     * Individual item amounts are set on pickup in PassiveListener.
     */
    public static void applyDoubleStack(Player player) {
        player.getInventory().setMaxStackSize(DOUBLE_INV_MAX);
    }

    /**
     * Restores inventory max to vanilla 64 and trims any stacks that exceed
     * the item's natural vanilla max, dropping excess at the player's feet.
     */
    public static void revertInventory(Player player) {
        player.getInventory().setMaxStackSize(VANILLA_INV_MAX);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            int vanillaMax = item.getType().getMaxStackSize();
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

    @Override public void applyPassiveEffects(Player player) {}

    @Override
    public String[] getCooldownKeys() { return new String[]{"A1_Scramble"}; }

    @Override
    public boolean isAbilityActive(int slot, UUID uuid) { return false; }
}
