package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.Mutation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WandListener implements Listener {

    private final MutationsPlugin plugin;

    public WandListener(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onWandInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getWandManager().isWand(held)) return;

        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (mutation == null) {
            player.sendMessage("§cYou don't have a mutation to activate!");
            event.setCancelled(true);
            return;
        }

        Action action = event.getAction();
        boolean shift = player.isSneaking();

        event.setCancelled(true);

        if (!shift && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            // RMB → Ability 1
            mutation.activateAbility1(player);
        } else if (shift && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            // Shift + LMB → Ability 2
            mutation.activateAbility2(player);
        } else if (shift && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            // Shift + RMB → Ability 3
            mutation.activateAbility3(player);
        }
    }

    // Prevent accidentally swapping the wand to off-hand with F key
    @EventHandler
    public void onWandSwap(PlayerSwapHandItemsEvent event) {
        ItemStack main = event.getMainHandItem();
        ItemStack off  = event.getOffHandItem();
        if (plugin.getWandManager().isWand(main) || plugin.getWandManager().isWand(off)) {
            event.setCancelled(true);
        }
    }
}
