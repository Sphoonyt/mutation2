package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class InventoryMutation extends Mutation {

    private static final Set<UUID> activeHolders = new HashSet<>();
    private final Map<UUID, BukkitTask> sortTasks = new HashMap<>();

    public InventoryMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override public MutationType getType() { return MutationType.INVENTORY; }

    @Override
    public void onAssign(Player player) {
        activeHolders.add(player.getUniqueId());
        startSortLoop(player);
        player.sendMessage("§a📦 You have been granted the §aInventory Mutation§a!");
        player.sendMessage("§7Passive: Your inventory automatically merges and compresses partial stacks.");
        player.sendMessage("§7Use §f/ability 1 §7to scramble a nearby enemy's hotbar.");
    }

    @Override
    public void onRemove(Player player) {
        activeHolders.remove(player.getUniqueId());
        BukkitTask task = sortTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @Override
    public void onTick(Player player) {}

    // ── Sort loop ─────────────────────────────────────────────────────────────

    private void startSortLoop(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask existing = sortTasks.remove(uuid);
        if (existing != null) existing.cancel();

        // Compress inventory every 20 ticks (1 second)
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !activeHolders.contains(uuid)) return;
            compressInventory(player);
        }, 20L, 20L);
        sortTasks.put(uuid, task);
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

    // ── Compression logic ─────────────────────────────────────────────────────

    /**
     * Scans the player's inventory and merges partial stacks of the same item type
     * into as few slots as possible, up to each item's vanilla max stack size.
     */
    public static void compressInventory(Player player) {
        org.bukkit.inventory.Inventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();

        // Group all items by their type+meta into a map: representative item → total count
        // We preserve item identity (enchants, name, PDC, etc.) by using isSimilar
        List<List<Integer>> groups = new ArrayList<>();

        boolean[] grouped = new boolean[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null || contents[i].getType() == Material.AIR || grouped[i]) continue;
            List<Integer> group = new ArrayList<>();
            group.add(i);
            grouped[i] = true;
            for (int j = i + 1; j < contents.length; j++) {
                if (contents[j] == null || grouped[j]) continue;
                if (contents[i].isSimilar(contents[j])) {
                    group.add(j);
                    grouped[j] = true;
                }
            }
            if (group.size() > 1) groups.add(group); // only care about groups with partials
        }

        for (List<Integer> group : groups) {
            // Count total items across all slots in this group
            int total = 0;
            for (int slot : group) total += contents[slot].getAmount();

            int maxStack = contents[group.get(0)].getType().getMaxStackSize();
            ItemStack template = contents[group.get(0)].clone();

            // Clear all slots in group
            for (int slot : group) contents[slot] = null;

            // Refill from first slot onwards with full stacks
            int remaining = total;
            for (int slot : group) {
                if (remaining <= 0) break;
                ItemStack stack = template.clone();
                int amount = Math.min(remaining, maxStack);
                stack.setAmount(amount);
                contents[slot] = stack;
                remaining -= amount;
            }
        }

        inv.setStorageContents(contents);
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    public static boolean isActive(UUID uuid) {
        return activeHolders.contains(uuid);
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
