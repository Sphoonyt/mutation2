package com.plugin.mutations.managers;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.Mutation;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages the Mace of Meiosis.
 * Every 5 ticks, for each mace holder we:
 *   1. Build a map of MutationType → live Mutation instance from nearby players.
 *   2. Call mutation.onTick(maceHolder) so tick-based passives (fire resistance,
 *      invisibility, ice runner speed, air-jump setup, etc.) run for the holder.
 *
 * Event-based passives (damage dealt/received, fall damage, potion effects, air jumps)
 * are wired in PassiveListener via hasPassive() / getLiveMutation().
 */
public class MaceManager {

    public static final String MACE_KEY = "mace_of_meiosis";
    private final NamespacedKey key;
    private final MutationsPlugin plugin;

    // Per mace-holder: map of type → live Mutation instance from nearby player
    // Updated every 5 ticks.
    private final Map<UUID, Map<MutationType, Mutation>> activeMutations = new HashMap<>();

    private int taskId = -1;

    public MaceManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, MACE_KEY);
        startLoop();
    }

    /** Create the Mace of Meiosis item */
    public ItemStack createMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5✦ §dMace of Meiosis §5✦");
        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Copies §dall passives §7of mutation");
        lore.add("§7holders within §b25 blocks§7.");
        lore.add("§7Must be held in main hand.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMace(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Is this player currently holding the mace and has at least one active passive? */
    public boolean isActive(UUID uuid) {
        Map<MutationType, Mutation> map = activeMutations.get(uuid);
        return map != null && !map.isEmpty();
    }

    /** Does this mace holder have the given mutation type's passives active? */
    public boolean hasPassive(UUID uuid, MutationType type) {
        Map<MutationType, Mutation> map = activeMutations.get(uuid);
        return map != null && map.containsKey(type);
    }

    /**
     * Get the live Mutation instance for a copied type on this mace holder.
     * Used by PassiveListener to call onDamageDealt / onDamageReceived etc.
     */
    public Mutation getLiveMutation(UUID uuid, MutationType type) {
        Map<MutationType, Mutation> map = activeMutations.get(uuid);
        return map == null ? null : map.get(type);
    }

    /**
     * Returns all live Mutation instances currently active for this mace holder.
     * Used by PassiveListener to fire event hooks across all copied passives.
     */
    public Collection<Mutation> getLiveMutations(UUID uuid) {
        Map<MutationType, Mutation> map = activeMutations.get(uuid);
        return map == null ? Collections.emptyList() : map.values();
    }

    // ---- Main loop ----

    private void startLoop() {
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!isMace(player.getInventory().getItemInMainHand())) {
                    activeMutations.remove(player.getUniqueId());
                    continue;
                }
                updatePassives(player);
                tickPassives(player);
            }
        }, 5L, 5L).getTaskId();
    }

    private void updatePassives(Player maceHolder) {
        Map<MutationType, Mutation> current = new HashMap<>();
        Collection<? extends Player> nearby = maceHolder.getWorld()
                .getNearbyPlayers(maceHolder.getLocation(), 25, 25, 25);
        for (Player p : nearby) {
            if (p == maceHolder) continue;
            Mutation mut = plugin.getMutationManager().getMutation(p);
            if (mut == null) continue;
            current.put(mut.getType(), mut);
        }
        activeMutations.put(maceHolder.getUniqueId(), current);
    }

    /** Call each copied mutation's onTick for the mace holder so tick-based passives fire. */
    private void tickPassives(Player maceHolder) {
        Map<MutationType, Mutation> map = activeMutations.get(maceHolder.getUniqueId());
        if (map == null || map.isEmpty()) return;
        for (Mutation mut : map.values()) {
            try {
                mut.onTick(maceHolder);
            } catch (Exception ignored) {}
        }
    }

    public void shutdown() {
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
    }
}
