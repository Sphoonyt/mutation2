package com.plugin.mutations.listeners;

import com.plugin.mutations.MutationsPlugin;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle main hand, right-click actions
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Mutation mutation = plugin.getMutationManager().getMutation(player);
        if (mutation == null) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        int slot = AbilityUtils.getAbilitySlot(held);
        if (slot == -1) return;

        // Cancel to prevent block placement etc.
        event.setCancelled(true);

        switch (slot) {
            case 1 -> mutation.activateAbility1(player);
            case 2 -> mutation.activateAbility2(player);
            case 3 -> mutation.activateAbility3(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getMutationManager().removeMutation(event.getPlayer());
    }
}
