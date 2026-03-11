package com.plugin.mutations.commands;

import com.plugin.mutations.MutationsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TrustCommand implements CommandExecutor, TabCompleter {

    private final MutationsPlugin plugin;

    public TrustCommand(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /trust.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> handleAdd(player, args);
            case "remove" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "clear" -> handleClear(player);
            default -> {
                // /trust <player> shorthand for /trust add <player>
                handleAddDirect(player, args[0]);
            }
        }
        return true;
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUsage: /trust add <player>"); return; }
        handleAddDirect(player, args[1]);
    }

    private void handleAddDirect(Player player, String name) {
        if (name.equalsIgnoreCase(player.getName())) {
            player.sendMessage("§cYou cannot trust yourself.");
            return;
        }
        Player target = plugin.getServer().getPlayer(name);
        if (target == null) {
            player.sendMessage("§cPlayer §f" + name + " §cis not online.");
            return;
        }
        if (plugin.getTrustManager().isTrusted(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage("§e" + target.getName() + " §eis already trusted.");
            return;
        }
        plugin.getTrustManager().trust(player.getUniqueId(), target.getUniqueId());
        player.sendMessage("§a✔ §f" + target.getName() + " §ais now trusted — your abilities will not affect them.");
        target.sendMessage("§a✔ §f" + player.getName() + " §ahas trusted you. Their abilities won't affect you.");
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage("§cUsage: /trust remove <player>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer §f" + args[1] + " §cis not online.");
            return;
        }
        if (!plugin.getTrustManager().isTrusted(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage("§e" + target.getName() + " §eis not in your trust list.");
            return;
        }
        plugin.getTrustManager().untrust(player.getUniqueId(), target.getUniqueId());
        player.sendMessage("§c✖ §f" + target.getName() + " §chas been removed from your trust list.");
        target.sendMessage("§c✖ §f" + player.getName() + " §chas revoked your trust. Their abilities may now affect you.");
    }

    private void handleList(Player player) {
        Set<UUID> trusted = plugin.getTrustManager().getTrusted(player.getUniqueId());
        if (trusted.isEmpty()) {
            player.sendMessage("§7Your trust list is empty.");
            return;
        }
        player.sendMessage("§6=== Your Trusted Players ===");
        for (UUID uuid : trusted) {
            Player p = plugin.getServer().getPlayer(uuid);
            String name = p != null ? p.getName() : "§8[offline] " + uuid.toString().substring(0, 8);
            player.sendMessage("§7• §f" + name);
        }
    }

    private void handleClear(Player player) {
        plugin.getTrustManager().clearTrust(player.getUniqueId());
        player.sendMessage("§aCleared your trust list.");
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== Trust System ===");
        player.sendMessage("§f/trust <player> §7- Trust a player (abilities won't affect them)");
        player.sendMessage("§f/trust add <player> §7- Same as above");
        player.sendMessage("§f/trust remove <player> §7- Remove trust");
        player.sendMessage("§f/trust list §7- View your trusted players");
        player.sendMessage("§f/trust clear §7- Clear all trusted players");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("add", "remove", "list", "clear"));
            options.addAll(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());
            return options.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
