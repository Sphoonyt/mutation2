package com.plugin.mutations.managers;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.Mutation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ActionBarManager {

    private final MutationsPlugin plugin;
    private int taskId = -1;

    public ActionBarManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                Mutation mutation = plugin.getMutationManager().getMutation(player);
                if (mutation == null) continue;
                sendBar(player, mutation);
            }
        }, 2L, 2L).getTaskId();
    }

    private void sendBar(Player player, Mutation mutation) {
        String[] keys = mutation.getCooldownKeys();
        UUID uuid = player.getUniqueId();

        String a1 = slotStatus(mutation, uuid, keys, 0, 1);
        String a2 = slotStatus(mutation, uuid, keys, 1, 2);
        String a3 = slotStatus(mutation, uuid, keys, 2, 3);

        String raw = mutation.getType().getDisplayName()
                + "  §7│  §fA1 " + a1
                + "  §7│  §fA2 " + a2
                + "  §7│  §fA3 " + a3;

        Component component = LegacyComponentSerializer.legacySection().deserialize(raw);
        player.sendActionBar(component);
    }

    private String slotStatus(Mutation mutation, UUID uuid, String[] keys, int keyIndex, int slot) {
        if (keyIndex >= keys.length || keys[keyIndex].isEmpty()) {
            return "§8—";
        }
        if (mutation.isAbilityActive(slot, uuid)) {
            return "§a§lACTIVE";
        }
        long rem = plugin.getCooldownManager().getRemainingSeconds(uuid, keys[keyIndex]);
        if (rem > 0) {
            return "§c" + rem + "s";
        }
        return "§aREADY";
    }

    public void shutdown() {
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
    }
}
