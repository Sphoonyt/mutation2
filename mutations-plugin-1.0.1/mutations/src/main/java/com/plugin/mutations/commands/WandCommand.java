package com.plugin.mutations.commands;

import com.plugin.mutations.MutationsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WandCommand implements CommandExecutor {

    private final MutationsPlugin plugin;

    public WandCommand(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        // Check if player already has a wand
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getWandManager().isWand(item)) {
                player.sendMessage("§cYou already have an Ability Wand!");
                return true;
            }
        }

        ItemStack wand = plugin.getWandManager().createWand();
        var leftover = player.getInventory().addItem(wand);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover.get(0));
            player.sendMessage("§e✦ Ability Wand dropped at your feet (inventory full).");
        } else {
            player.sendMessage("§e✦ Ability Wand added to your inventory!");
            player.sendMessage("§7RMB = Ability 1 | Shift+LMB = Ability 2 | Shift+RMB = Ability 3");
        }
        return true;
    }
}
