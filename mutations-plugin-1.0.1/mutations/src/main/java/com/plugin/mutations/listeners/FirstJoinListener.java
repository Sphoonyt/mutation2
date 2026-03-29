package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Random;

public class FirstJoinListener implements Listener {

    private final MutationsPlugin plugin;
    private final Random random = new Random();

    // Only Wind, Frozen, Rock, Hellfire are obtainable on first join
    private static final List<MutationType> POOL = List.of(
            MutationType.WIND,
            MutationType.FROZEN,
            MutationType.ROCK,
            MutationType.HELLFIRE
    );

    public FirstJoinListener(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay slightly so the player is fully loaded before we restore/assign
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // If they already have a mutation loaded in memory (shouldn't happen), skip
            if (plugin.getMutationManager().hasMutation(player)) return;

            // Try to load saved mutation from file
            if (plugin.getMutationManager().hasSavedMutation(player.getUniqueId())) {
                plugin.getMutationManager().loadMutationForPlayer(player);
                return;
            }

            // True first join — no saved mutation exists at all
            if (!player.hasPlayedBefore()) {
                MutationType chosen = POOL.get(random.nextInt(POOL.size()));
                plugin.getMutationManager().setMutation(player, chosen);
                player.sendMessage("§6§l✦ Welcome! You have been bestowed the " + chosen.getDisplayName() + "§6§l! ✦");
                player.sendMessage("§7Use §f/ability 1§7, §f/ability 2§7, §f/ability 3 §7to use abilities.");
                player.sendMessage("§7Use §f/mutation info §7to see your mutation details.");
            }
        }, 20L); // 1s delay
    }
}
