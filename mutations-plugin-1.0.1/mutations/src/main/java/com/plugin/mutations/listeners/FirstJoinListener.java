package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class FirstJoinListener implements Listener {

    private final MutationsPlugin plugin;
    private final Random random = new Random();

    // All mutations except the three Dragonborne variants
    private static final List<MutationType> POOL = Arrays.stream(MutationType.values())
            .filter(t -> t != MutationType.DRAGONBORNE_POISON
                    && t != MutationType.DRAGONBORNE_FIRE
                    && t != MutationType.DRAGONBORNE_ARMOR)
            .collect(Collectors.toList());

    public FirstJoinListener(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Only trigger on true first join (never played before)
        if (player.hasPlayedBefore()) return;

        // Don't overwrite if somehow already assigned (shouldn't happen on first join)
        if (plugin.getMutationManager().hasMutation(player)) return;

        MutationType chosen = POOL.get(random.nextInt(POOL.size()));
        plugin.getMutationManager().setMutation(player, chosen);

        // Slight delay so the player fully loads in before receiving messages
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("§6§l✦ Welcome to the server! ✦");
            player.sendMessage("§7You have been bestowed the " + chosen.getDisplayName() + "§7!");
            player.sendMessage("§7Use §f/ability 1§7, §f/ability 2§7, §f/ability 3 §7to use your abilities.");
            player.sendMessage("§7Use §f/mutation info §7to view your mutation details.");
        }, 40L); // 2s delay
    }
}
