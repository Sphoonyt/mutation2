package com.plugin.mutations.commands;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.Mutation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AbilityCommand implements CommandExecutor, TabCompleter {

    private final MutationsPlugin plugin;

    public AbilityCommand(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§6Usage: §f/ability <1|2|3>");
            player.sendMessage("§71 = Ability 1 | 2 = Ability 2 | 3 = Ability 3");
            return true;
        }

        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (mutation == null) {
            player.sendMessage("§cYou don't have a mutation! Ask an admin to assign you one.");
            return true;
        }

        switch (args[0]) {
            case "1" -> mutation.activateAbility1(player);
            case "2" -> mutation.activateAbility2(player);
            case "3" -> mutation.activateAbility3(player);
            default -> {
                player.sendMessage("§cInvalid ability. Use §f/ability 1§c, §f/ability 2§c, or §f/ability 3§c.");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("1", "2", "3");
        }
        return Collections.emptyList();
    }
}
