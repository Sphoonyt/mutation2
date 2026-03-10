package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public abstract class Mutation {

    protected final MutationsPlugin plugin;

    public Mutation(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called when mutation is assigned to a player */
    public abstract void onAssign(Player player);

    /** Called when mutation is removed from a player */
    public abstract void onRemove(Player player);

    /** Get the mutation type */
    public abstract MutationType getType();

    /** Activate ability 1 */
    public abstract void activateAbility1(Player player);

    /** Activate ability 2 */
    public abstract void activateAbility2(Player player);

    /** Activate ability 3 */
    public abstract void activateAbility3(Player player);

    // ---- Optional hooks for passives ----

    /** Called on any damage dealt by this player (for hit-counting passives) */
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {}

    /** Called on any damage received by this player */
    public void onDamageReceived(Player player, EntityDamageByEntityEvent event) {}

    /** Called every tick (20x/s) via scheduler for movement-based passives */
    public void onTick(Player player) {}

    /**
     * Called by the Mace of Meiosis every 5 ticks.
     * Apply short-duration potion effects representing this mutation's passives to the target.
     * Effects should use duration ~30 ticks so they expire if the mace user walks away.
     */
    public void applyPassiveEffects(Player target) {}

    // ---- Shared helpers ----

    protected boolean checkCooldown(Player player, String key, int seconds) {
        if (plugin.getMutationManager().isMutationLocked(player)) {
            AbilityUtils.sendLockedMessage(player);
            return false;
        }
        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), key)) {
            AbilityUtils.sendCooldownMessage(player, key, plugin.getCooldownManager().getRemainingSeconds(player.getUniqueId(), key));
            return false;
        }
        plugin.getCooldownManager().setCooldown(player.getUniqueId(), key, seconds);
        return true;
    }

    protected void runLater(Runnable task, long ticks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, ticks);
    }

    protected void runRepeating(Runnable task, long delay, long period) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period);
    }
}
