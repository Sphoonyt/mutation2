package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.mutations.Mutation;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class AbilityListener implements Listener {

    private final MutationsPlugin plugin;

    public AbilityListener(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Right-clicking a mutation orb absorbs it */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        MutationType type = plugin.getMutationManager().getItemManager().getMutationType(held);
        if (type == null) return;

        event.setCancelled(true);

        // Already has a mutation — can't absorb another
        if (plugin.getMutationManager().hasMutation(player)) {
            player.sendMessage("§cYou already have a mutation! Use §f/mutation withdraw §cfirst.");
            return;
        }

        // Remove the orb from inventory and assign mutation
        held.setAmount(held.getAmount() - 1);
        plugin.getMutationManager().setMutation(player, type);

        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        player.sendMessage("§aYou absorbed the " + type.getDisplayName() + "§a!");
        player.sendMessage("§7Use §f/ability 1§7, §f/ability 2§7, §f/ability 3 §7to activate abilities.");
        player.sendMessage("§7Use §f/mutation withdraw §7to remove the mutation and get the orb back.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getMutationManager().removeMutation(event.getPlayer());
    }
}
