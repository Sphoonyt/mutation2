package com.plugin.mutations.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class EquipmentPacketUtil {

    /** Returns the ProtocolLib manager, or null if ProtocolLib is not available. */
    private static ProtocolManager manager() {
        try {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return null;
            return ProtocolLibrary.getProtocolManager();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sends fake empty equipment to all other online players so the shadow player
     * appears to have no armor or held items — while the real armor stays server-side.
     */
    public static void sendEmptyEquipment(Player target) {
        ProtocolManager pm = manager();
        if (pm == null) return;
        try {
            PacketContainer packet = buildEquipmentPacket(pm, target, true);
            if (packet == null) return;
            for (Player observer : target.getServer().getOnlinePlayers()) {
                if (observer == target) continue;
                try { pm.sendServerPacket(observer, packet); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Restores real equipment by sending the actual equipment to all other players.
     */
    public static void restoreEquipment(Player target) {
        ProtocolManager pm = manager();
        if (pm == null) return;
        try {
            PacketContainer packet = buildEquipmentPacket(pm, target, false);
            if (packet == null) return;
            for (Player observer : target.getServer().getOnlinePlayers()) {
                if (observer == target) continue;
                try { pm.sendServerPacket(observer, packet); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static PacketContainer buildEquipmentPacket(ProtocolManager pm, Player target, boolean empty) {
        try {
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, target.getEntityId());

            List<Pair<ItemSlot, ItemStack>> slots = new ArrayList<>();
            if (empty) {
                slots.add(new Pair<>(ItemSlot.MAINHAND, null));
                slots.add(new Pair<>(ItemSlot.OFFHAND,  null));
                slots.add(new Pair<>(ItemSlot.HEAD,     null));
                slots.add(new Pair<>(ItemSlot.CHEST,    null));
                slots.add(new Pair<>(ItemSlot.LEGS,     null));
                slots.add(new Pair<>(ItemSlot.FEET,     null));
            } else {
                slots.add(new Pair<>(ItemSlot.MAINHAND, target.getInventory().getItemInMainHand()));
                slots.add(new Pair<>(ItemSlot.OFFHAND,  target.getInventory().getItemInOffHand()));
                slots.add(new Pair<>(ItemSlot.HEAD,     target.getInventory().getHelmet()));
                slots.add(new Pair<>(ItemSlot.CHEST,    target.getInventory().getChestplate()));
                slots.add(new Pair<>(ItemSlot.LEGS,     target.getInventory().getLeggings()));
                slots.add(new Pair<>(ItemSlot.FEET,     target.getInventory().getBoots()));
            }

            packet.getSlotStackPairLists().write(0, slots);
            return packet;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
