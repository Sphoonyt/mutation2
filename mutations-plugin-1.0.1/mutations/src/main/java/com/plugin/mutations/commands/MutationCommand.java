package com.plugin.mutations.commands;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.mutations.Mutation;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.Random;
import java.util.stream.Collectors;

public class MutationCommand implements CommandExecutor, TabCompleter {

    private final MutationsPlugin plugin;

    public MutationCommand(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "set"      -> handleSet(sender, args);
            case "remove"   -> handleRemove(sender, args);
            case "list"     -> handleList(sender);
            case "info"     -> handleInfo(sender, args);
            case "clear"    -> handleClear(sender, args);
            case "give"     -> handleGive(sender, args);
            case "random"   -> handleRandom(sender, args);
            case "withdraw" -> handleWithdraw(sender, args);
            default         -> sendHelp(sender);
        }
        return true;
    }

    /** /mutation set <player> <mutation> — assign without item */
    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mutations.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length < 3) { sender.sendMessage("§cUsage: /mutation set <player> <mutation>"); return; }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return; }

        MutationType type = MutationType.fromId(args[2].toLowerCase());
        if (type == null) { sender.sendMessage("§cUnknown mutation: §f" + args[2] + "\n§7Available: " + getMutationIds()); return; }

        plugin.getMutationManager().setMutation(target, type);
        sender.sendMessage("§aSet §f" + target.getName() + "§a's mutation to §f" + type.getDisplayName());
    }

    /** /mutation remove <player> — remove mutation (no item returned) */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mutations.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length < 2) { sender.sendMessage("§cUsage: /mutation remove <player>"); return; }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return; }
        if (!plugin.getMutationManager().hasMutation(target)) { sender.sendMessage("§c" + target.getName() + " has no mutation."); return; }

        plugin.getMutationManager().removeMutation(target);
        sender.sendMessage("§aRemoved §f" + target.getName() + "§a's mutation.");
        target.sendMessage("§cYour mutation has been removed.");
    }

    /** /mutation give <player> <mutation|mace> — give the physical orb item or the Mace of Meiosis */
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mutations.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length < 3) { sender.sendMessage("§cUsage: /mutation give <player> <mutation|mace>"); return; }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return; }

        // Special case: Mace of Meiosis
        String arg2 = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).toLowerCase();
        if (arg2.equals("mace") || arg2.equals("mace of meiosis")) {
            ItemStack mace = plugin.getMaceManager().createMace();
            target.getInventory().addItem(mace);
            sender.sendMessage("§aGave §f" + target.getName() + " §athe §dMace of Meiosis§a.");
            target.sendMessage("§d✦ You received the Mace of Meiosis! Hold it to copy nearby mutation passives.");
            return;
        }

        MutationType type = MutationType.fromId(args[2].toLowerCase());
        if (type == null) { sender.sendMessage("§cUnknown mutation: §f" + args[2] + "\n§7Available: " + getMutationIds() + ", mace"); return; }

        ItemStack orb = plugin.getMutationManager().getItemManager().createMutationItem(type);
        target.getInventory().addItem(orb);

        sender.sendMessage("§aGave §f" + target.getName() + " §athe §f" + type.getDisplayName() + " §aorb.");
        target.sendMessage("§aYou received the " + type.getDisplayName() + " §aorb! Right-click it to absorb the mutation.");
    }

    /** /mutation withdraw [player] — remove mutation and return the orb */
    private void handleWithdraw(CommandSender sender, String[] args) {
        Player target;

        if (args.length >= 2 && sender.hasPermission("mutations.admin")) {
            target = plugin.getServer().getPlayer(args[1]);
            if (target == null) { sender.sendMessage("§cPlayer not found."); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cSpecify a player."); return;
        }

        if (!plugin.getMutationManager().hasMutation(target)) {
            sender.sendMessage("§c" + (target == sender ? "You don't" : target.getName() + " doesn't") + " have a mutation.");
            return;
        }

        MutationType type = plugin.getMutationManager().getMutation(target).getType();
        plugin.getMutationManager().removeMutation(target);

        // Return the orb
        ItemStack orb = plugin.getMutationManager().getItemManager().createMutationItem(type);
        target.getInventory().addItem(orb);

        target.sendMessage("§eYour " + type.getDisplayName() + " §emutation has been withdrawn. Orb returned to inventory.");
        if (sender != target) sender.sendMessage("§aWithdrew §f" + target.getName() + "§a's mutation and returned the orb.");
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage("§6=== Available Mutations ===");
        for (MutationType type : MutationType.values()) {
            sender.sendMessage("§7• §f" + type.getId() + " §8→ " + type.getDisplayName());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayer(args[1]);
            if (target == null) { sender.sendMessage("§cPlayer not found."); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else { sender.sendMessage("§cSpecify a player."); return; }

        Mutation mutation = plugin.getMutationManager().getMutation(target);
        if (mutation == null) { sender.sendMessage("§7" + target.getName() + " has no mutation."); return; }

        sender.sendMessage("§6=== " + target.getName() + "'s Mutation ===");
        sender.sendMessage("§7Type: " + mutation.getType().getDisplayName());
        sender.sendMessage("§7Locked: " + (plugin.getMutationManager().isMutationLocked(target) ? "§cYes" : "§aNo"));

        String[] cdKeys = getCooldownKeys(mutation.getType());
        for (int i = 0; i < cdKeys.length; i++) {
            long rem = plugin.getCooldownManager().getRemainingSeconds(target.getUniqueId(), cdKeys[i]);
            String status = rem > 0 ? "§c" + rem + "s" : "§aReady";
            sender.sendMessage("§7Ability " + (i + 1) + ": " + status);
        }
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mutations.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length < 2) { sender.sendMessage("§cUsage: /mutation clear <player>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return; }
        plugin.getCooldownManager().clearCooldowns(target.getUniqueId());
        sender.sendMessage("§aCleared cooldowns for §f" + target.getName());
    }

    private String[] getCooldownKeys(MutationType type) {
        return switch (type) {
            case WIND -> new String[]{"A1_Tornado", "A2_Shuriken", "A3_WindLeap"};
            case BLOOD_SOLDIER -> new String[]{"A1_NauseBlood", "A2_Sacrifice", "A3_Katana"};
            case FROZEN -> new String[]{"A1_FrozenRec", "A2_IceSpike", "A3_GlacialDomain"};
            case BYPASS -> new String[]{"A1_Rapier", "A2_MutLock", "A3_PhantomDash"};
            case ROCK -> new String[]{"A1_StoneSlamm", "A2_SkinHarden", "A3_BoulderBarrage"};
            case HELLFIRE -> new String[]{"A1_HellfireRush", "A2_FlameWhip", "A3_HellPull"};
            case DRAGONBORNE_POISON -> new String[]{"A1_VenomBurst", "A2_ToxicFang", "A3_SerpentGlide"};
            case DRAGONBORNE_FIRE -> new String[]{"A1_BlazingErupt", "A2_FlameTalon", "A3_InfernoLeap"};
            case DRAGONBORNE_ARMOR -> new String[]{"A1_Ironclad", "A2_SteelFist", "A3_ArmorConstruct"};
            case DRAGONBORNE_LIGHT -> new String[]{"A1_LuminousRoar", "A2_SolarWing", "A3_CelestialExp"};
            case TRUE_SHOT -> new String[]{"A1_PenVolley", "A2_GuidedLight", "A3_TrueShotApex"};
            case LOVE -> new String[]{"A1_HeartPulse", "A2_AdorationBeam", "A3_LoveOverdrive"};
            case DEBUFF -> new String[]{"A1_DebuffAll"};
            case WITCH  -> new String[]{"A1_CovenFury", "A2_MassDispel"};
            case SHADOW    -> new String[]{"A1_Dissipate", "A2_ShadowGrab", "A3_HideAndSeek"};
            case LIGHTNING -> new String[]{"A1_Strike", "A2_Siphon", "A3_Arena"};
            case NATURE    -> new String[]{"A1_NaturesGrasp", "A2_TrunkSlam", "A3_ViolentNature"};
        };
    }

    private String getMutationIds() {
        return Arrays.stream(MutationType.values()).map(MutationType::getId).collect(Collectors.joining(", "));
    }


    /** /mutation random <player> — give a random non-dragonborne mutation */
    private void handleRandom(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mutations.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length < 2) { sender.sendMessage("§cUsage: /mutation random <player>"); return; }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return; }

        List<MutationType> pool = Arrays.stream(MutationType.values())
                .filter(t -> t != MutationType.DRAGONBORNE_POISON
                        && t != MutationType.DRAGONBORNE_FIRE
                        && t != MutationType.DRAGONBORNE_ARMOR)
                .collect(Collectors.toList());

        MutationType chosen = pool.get(new Random().nextInt(pool.size()));
        plugin.getMutationManager().setMutation(target, chosen);
        sender.sendMessage("§aGave §f" + target.getName() + " §athe random mutation: " + chosen.getDisplayName());
        target.sendMessage("§6✦ You have been given a random mutation: " + chosen.getDisplayName() + " §6✦");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Mutations Plugin ===");
        sender.sendMessage("§7/mutation give <player> <mutation> §8- Give mutation orb item");
        sender.sendMessage("§7/mutation withdraw [player] §8- Remove mutation & return orb");
        sender.sendMessage("§7/mutation set <player> <mutation> §8- Force assign mutation");
        sender.sendMessage("§7/mutation remove <player> §8- Force remove mutation");
        sender.sendMessage("§7/mutation list §8- List all mutations");
        sender.sendMessage("§7/mutation info [player] §8- View mutation & cooldowns");
        sender.sendMessage("§7/mutation clear <player> §8- Reset cooldowns");
        sender.sendMessage("§7/mutation random <player> §8- Give a random mutation");
        sender.sendMessage("§7");
        sender.sendMessage("§7Mutations: §f" + getMutationIds());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return Arrays.asList("give", "withdraw", "set", "remove", "list", "info", "clear", "random");
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.stream(MutationType.values()).map(MutationType::getId).collect(Collectors.toList());
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            List<String> opts = new ArrayList<>(Arrays.stream(MutationType.values()).map(MutationType::getId).collect(Collectors.toList()));
            opts.add("mace");
            return opts;
        }
        return Collections.emptyList();
    }
}
