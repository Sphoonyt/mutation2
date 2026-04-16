package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.OverstackUtil;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class InventoryMutation extends Mutation {

    private static final Set<UUID> activeHolders = new HashSet<>();
    private final Map<UUID, BukkitTask> displayTasks = new HashMap<>();

    public InventoryMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override public MutationType getType() { return MutationType.INVENTORY; }

    @Override
    public void onAssign(Player player) {
        activeHolders.add(player.getUniqueId());
        startDisplayLoop(player);
        player.sendMessage("§a📦 You have been granted the §aInventory Mutation§a!");
        player.sendMessage("§7Passive: Your inventory displays double stack sizes.");
        player.sendMessage("§7Use §f/ability 1 §7to scramble a nearby enemy's hotbar.");
    }

    @Override
    public void onRemove(Player player) {
        activeHolders.remove(player.getUniqueId());
        stopDisplayLoop(player);
        OverstackUtil.revertDisplay(player);
    }

    @Override
    public void onTick(Player player) {}

    private void startDisplayLoop(Player player) {
        UUID uuid = player.getUniqueId();
        stopDisplayLoop(player);
        // Refresh fake doubled display every 10 ticks (0.5s) to catch newly acquired items
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !activeHolders.contains(uuid)) return;
            OverstackUtil.applyDoubleStackDisplay(player);
        }, 10L, 10L);
        displayTasks.put(uuid, task);
    }

    private void stopDisplayLoop(Player player) {
        BukkitTask t = displayTasks.remove(player.getUniqueId());
        if (t != null) t.cancel();
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

    public static boolean isActive(UUID uuid) { return activeHolders.contains(uuid); }

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
    @Override public String[] getCooldownKeys() { return new String[]{"A1_Scramble"}; }
    @Override public boolean isAbilityActive(int slot, UUID uuid) { return false; }
}
