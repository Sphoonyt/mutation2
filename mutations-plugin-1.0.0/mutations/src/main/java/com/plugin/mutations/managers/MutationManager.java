package com.plugin.mutations.managers;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.*;
import com.plugin.mutations.managers.MutationItemManager;
import org.bukkit.entity.Player;

import java.util.*;

public class MutationManager {

    private final MutationsPlugin plugin;
    private final Map<UUID, Mutation> playerMutations = new HashMap<>();
    private final Set<UUID> lockedMutations = new HashSet<>();
    private MutationItemManager itemManager;

    public MutationManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.itemManager = new MutationItemManager(plugin);
    }

    public MutationItemManager getItemManager() { return itemManager; }

    public void setMutation(Player player, MutationType type) {
        // Remove old mutation first
        removeMutation(player);

        Mutation mutation = createMutation(type, player);
        if (mutation == null) return;

        playerMutations.put(player.getUniqueId(), mutation);
        mutation.onAssign(player);
    }

    public void removeMutation(Player player) {
        Mutation old = playerMutations.remove(player.getUniqueId());
        if (old != null) old.onRemove(player);
        plugin.getCooldownManager().clearCooldowns(player.getUniqueId());
        lockedMutations.remove(player.getUniqueId());
    }

    public Mutation getMutation(Player player) {
        return playerMutations.get(player.getUniqueId());
    }

    public boolean hasMutation(Player player) {
        return playerMutations.containsKey(player.getUniqueId());
    }

    public boolean isMutationLocked(Player player) {
        return lockedMutations.contains(player.getUniqueId());
    }

    public void lockMutation(Player player, int seconds) {
        lockedMutations.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            lockedMutations.remove(player.getUniqueId()), seconds * 20L);
    }

    public Collection<Mutation> getAllActiveMutations() {
        return playerMutations.values();
    }

    public Map<UUID, Mutation> getPlayerMutations() {
        return playerMutations;
    }

    public void cleanup() {
        playerMutations.clear();
        lockedMutations.clear();
    }

    private Mutation createMutation(MutationType type, Player player) {
        return switch (type) {
            case WIND -> new WindMutation(plugin);
            case BLOOD_SOLDIER -> new BloodSoldierMutation(plugin);
            case FROZEN -> new FrozenMutation(plugin);
            case BYPASS -> new BypassMutation(plugin);
            case ROCK -> new RockMutation(plugin);
            case HELLFIRE -> new HellfireMutation(plugin);
            case DRAGONBORNE_POISON -> new DragonborneMutation(plugin, DragonborneMutation.Style.POISON);
            case DRAGONBORNE_FIRE -> new DragonborneMutation(plugin, DragonborneMutation.Style.FIRE);
            case DRAGONBORNE_ARMOR -> new DragonborneMutation(plugin, DragonborneMutation.Style.ARMOR);
            case LIGHT -> new LightMutation(plugin);
            case TRUE_SHOT -> new TrueShotMutation(plugin);
            case LOVE -> new LoveMutation(plugin);
            case DEBUFF -> new DebuffMutation(plugin);
        };
    }
}
