package com.plugin.mutations.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class EquipmentPacketUtil {

    /**
     * Sends fake empty equipment to all other online players so the shadow player
     * appears to have no armor or held items — while the real armor stays server-side.
     */
    public static void sendEmptyEquipment(Player target) {
        PacketContainer packet = buildEquipmentPacket(target, true);
        for (Player observer : target.getServer().getOnlinePlayers()) {
            if (observer == target) continue;
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Restores real equipment by sending the actual equipment to all other players.
     */
    public static void restoreEquipment(Player target) {
        PacketContainer packet = buildEquipmentPacket(target, false);
        for (Player observer : target.getServer().getOnlinePlayers()) {
            if (observer == target) continue;
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static PacketContainer buildEquipmentPacket(Player target, boolean empty) {
        PacketContainer packet = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);

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
    }
}
