package com.plugin.mutations.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Uses ProtocolLib to send fake SET_SLOT packets so the client displays
 * oversized stack sizes beyond vanilla limits.
 * The real server-side items are kept at vanilla sizes — we only lie to the client.
 */
public class OverstackUtil {

    private static ProtocolManager pm() {
        try {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return null;
            return ProtocolLibrary.getProtocolManager();
        } catch (Exception e) { return null; }
    }

    /**
     * Sends a fake SET_SLOT packet to a player showing an oversized stack.
     * windowId 0 = player inventory. Slot numbering follows Minecraft protocol:
     *   0 = crafting output, 1-4 = crafting grid, 5-8 = armour, 9-35 = main inv, 36-44 = hotbar, 45 = offhand
     */
    public static void sendFakeSlot(Player player, int windowId, int slot, ItemStack displayItem) {
        ProtocolManager manager = pm();
        if (manager == null) return;
        try {
            PacketContainer packet = manager.createPacket(PacketType.Play.Server.SET_SLOT);
            packet.getIntegers().write(0, windowId);      // window id
            packet.getIntegers().write(1, 0);             // state id (0 is fine)
            packet.getIntegers().write(2, slot);          // slot
            packet.getItemModifier().write(0, displayItem);
            manager.sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Refreshes the player's entire inventory display with doubled stack sizes
     * for all stackable items. Server-side items are unchanged.
     */
    public static void applyDoubleStackDisplay(Player player) {
        ProtocolManager manager = pm();
        if (manager == null) return;

        ItemStack[] contents = player.getInventory().getStorageContents();
        // Storage contents slots 0-26 = main (protocol slots 9-35), 27-35 = hotbar (protocol 36-44)
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            int vanillaMax = item.getType().getMaxStackSize();
            if (vanillaMax <= 1) continue;
            if (item.getAmount() >= vanillaMax) continue; // already at or above max, would show double

            // Create fake display item with doubled amount (capped at 127 for protocol)
            ItemStack display = item.clone();
            display.setAmount(Math.min(item.getAmount() * 2, 127));

            // Map storage index to protocol slot:
            // storage 0-26 = main inv (protocol 9-35), storage 27-35 = hotbar (protocol 36-44)
            int protocolSlot = i < 27 ? i + 9 : i + 9;
            sendFakeSlot(player, 0, protocolSlot, display);
        }
    }

    /**
     * Sends real item data back to the client, reverting any fake display.
     */
    public static void revertDisplay(Player player) {
        player.updateInventory(); // triggers the client to re-sync all slots with real server data
    }
}
