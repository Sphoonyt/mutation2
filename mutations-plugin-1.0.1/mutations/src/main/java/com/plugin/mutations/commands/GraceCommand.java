package com.plugin.mutations.commands;

import com.plugin.mutations.MutationsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraceCommand implements CommandExecutor, TabCompleter {

    private final MutationsPlugin plugin;
    private BukkitTask graceTask = null;
    private long graceExpiry     = 0L; // ms timestamp when grace ends

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([tTsSmMhH])");

    public GraceCommand(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true if the server-wide grace period is currently active. */
    public boolean isGraceActive() {
        if (graceExpiry == 0) return false;
        if (System.currentTimeMillis() >= graceExpiry) {
            graceExpiry = 0;
            graceTask = null;
            return false;
        }
        return true;
    }

    public void endGrace() {
        if (graceTask != null) { graceTask.cancel(); graceTask = null; }
        graceExpiry = 0;
        plugin.getServer().broadcastMessage("§a🛡 Server grace period has ended. Combat is now enabled.");
        plugin.getServer().getOnlinePlayers().forEach(p ->
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f));
    }

    // ── Command ───────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mutations.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("end")) {
            if (!isGraceActive()) { sender.sendMessage("§cNo grace period is active."); return true; }
            endGrace();
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("status")) {
            if (!isGraceActive()) {
                sender.sendMessage("§7No grace period is currently active.");
            } else {
                long rem = graceExpiry - System.currentTimeMillis();
                sender.sendMessage("§a🛡 Grace is active — §f" + formatMs(rem) + " §aremaining.");
            }
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6Usage: §f/grace <duration>");
            sender.sendMessage("§7Examples: §f30s §7(seconds), §f5m §7(minutes), §f2h §7(hours), §f100t §7(ticks)");
            sender.sendMessage("§7Chain units: §f1m30s§7, §f2h30m");
            sender.sendMessage("§7Other: §f/grace end §7— end early  |  §f/grace status");
            return true;
        }

        long ticks = parseDuration(args[0]);
        if (ticks <= 0) {
            sender.sendMessage("§cInvalid duration. Examples: §f30s§c, §f5m§c, §f2h§c, §f100t");
            return true;
        }

        // Cancel existing grace if one is running
        if (graceTask != null) { graceTask.cancel(); graceTask = null; }

        graceExpiry = System.currentTimeMillis() + (ticks * 50L);
        String display = formatTicks(ticks);

        plugin.getServer().broadcastMessage("§a🛡 Server grace period started for §f" + display + "§a. All combat and abilities are disabled.");
        plugin.getServer().getOnlinePlayers().forEach(p -> {
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
            plugin.getAdvancementManager().onGraceJoin(p);
        });

        graceTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            graceTask = null;
            graceExpiry = 0;
            plugin.getServer().broadcastMessage("§c🛡 Server grace period has ended. Combat is now enabled.");
            plugin.getServer().getOnlinePlayers().forEach(p ->
                    p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f));
        }, ticks);

        return true;
    }

    // ── Duration parsing ──────────────────────────────────────────────────────

    private long parseDuration(String input) {
        Matcher m = TIME_PATTERN.matcher(input);
        long total = 0;
        int lastEnd = 0;
        while (m.find()) {
            lastEnd = m.end();
            long value = Long.parseLong(m.group(1));
            char unit = Character.toLowerCase(m.group(2).charAt(0));
            total += switch (unit) {
                case 't' -> value;
                case 's' -> value * 20L;
                case 'm' -> value * 20L * 60L;
                case 'h' -> value * 20L * 3600L;
                default  -> 0L;
            };
        }
        if (lastEnd != input.length() || total <= 0) return -1;
        return total;
    }

    private String formatTicks(long ticks) {
        return formatMs(ticks * 50L);
    }

    private String formatMs(long ms) {
        long totalSeconds = ms / 1000;
        if (totalSeconds == 0) return (ms / 50) + " ticks";
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0)   sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("30s", "1m", "5m", "1h", "end", "status");
        return Collections.emptyList();
    }
}
