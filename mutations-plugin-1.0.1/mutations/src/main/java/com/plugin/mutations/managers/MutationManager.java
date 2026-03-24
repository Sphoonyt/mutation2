package com.plugin.mutations.managers;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MutationManager {

    private final MutationsPlugin plugin;
    private final Map<UUID, Mutation> playerMutations = new HashMap<>();
    private final Set<UUID> lockedMutations = new HashSet<>();
    private final MutationItemManager itemManager;

    // Persistence file: plugins/Mutations/player_mutations.yml
    private final File dataFile;
    private final YamlConfiguration dataConfig;

    public MutationManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.itemManager = new MutationItemManager(plugin);

        dataFile = new File(plugin.getDataFolder(), "player_mutations.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public MutationItemManager getItemManager() { return itemManager; }

    // ---- Mutation assignment ----

    public void setMutation(Player player, MutationType type) {
        removeMutation(player);

        Mutation mutation = createMutation(type, player);
        if (mutation == null) return;

        playerMutations.put(player.getUniqueId(), mutation);
        mutation.onAssign(player);
        saveMutation(player.getUniqueId(), type);
    }

    public void removeMutation(Player player) {
        Mutation old = playerMutations.remove(player.getUniqueId());
        if (old != null) old.onRemove(player);
        // Cooldowns intentionally NOT cleared — they persist through withdrawal/death
        lockedMutations.remove(player.getUniqueId());
        clearSavedMutation(player.getUniqueId());
    }

    public Mutation getMutation(Player player) {
        return playerMutations.get(player.getUniqueId());
    }

    public boolean hasMutation(Player player) {
        return playerMutations.containsKey(player.getUniqueId());
    }

    // ---- Persistence ----

    /** Save mutation type to file for this player */
    private void saveMutation(UUID uuid, MutationType type) {
        dataConfig.set(uuid.toString(), type.getId());
        saveFile();
    }

    /** Remove saved mutation from file */
    private void clearSavedMutation(UUID uuid) {
        dataConfig.set(uuid.toString(), null);
        saveFile();
    }

    /** Load mutation from file and apply to player on join */
    public void loadMutationForPlayer(Player player) {
        // Load persisted cooldowns first so they're available when mutation is restored
        plugin.getCooldownManager().loadPlayer(player.getUniqueId());

        String saved = dataConfig.getString(player.getUniqueId().toString());
        if (saved == null || saved.isEmpty()) return;

        MutationType type = MutationType.fromId(saved);
        if (type == null) return;

        Mutation mutation = createMutation(type, player);
        if (mutation == null) return;

        // Silently restore — no onAssign messages, player already knows their mutation
        playerMutations.put(player.getUniqueId(), mutation);
        player.sendMessage("§7Your mutation §f" + type.getDisplayName() + " §7has been restored.");
    }

    /** Check if a player has a saved mutation (used by FirstJoinListener) */
    public boolean hasSavedMutation(UUID uuid) {
        return dataConfig.contains(uuid.toString());
    }

    private void saveFile() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player_mutations.yml: " + e.getMessage());
        }
    }

    // ---- Locking ----

    public boolean isMutationLocked(Player player) {
        return lockedMutations.contains(player.getUniqueId());
    }

    public void lockMutation(Player player, int seconds) {
        lockedMutations.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            lockedMutations.remove(player.getUniqueId()), seconds * 20L);
    }

    // ---- Utilities ----

    public Collection<Mutation> getAllActiveMutations() {
        return playerMutations.values();
    }

    public Map<UUID, Mutation> getPlayerMutations() {
        return playerMutations;
    }

    /** Called on player quit — cleans up memory WITHOUT touching the save file */
    public void unloadPlayer(Player player) {
        Mutation old = playerMutations.remove(player.getUniqueId());
        if (old != null) old.onRemove(player);
        // Persist cooldowns to disk so they survive relog and re-equip
        plugin.getCooldownManager().savePlayer(player.getUniqueId());
        // Remove from memory after saving
        plugin.getCooldownManager().clearCooldowns(player.getUniqueId());
        lockedMutations.remove(player.getUniqueId());
        // NOTE: intentionally does NOT call clearSavedMutation — file stays intact
    }

    public void cleanup() {
        playerMutations.clear();
        lockedMutations.clear();
    }

    private Mutation createMutation(MutationType type, Player player) {
        return switch (type) {
            case WIND               -> new WindMutation(plugin);
            case BLOOD_SOLDIER      -> new BloodSoldierMutation(plugin);
            case FROZEN             -> new FrozenMutation(plugin);
            case BYPASS             -> new BypassMutation(plugin);
            case ROCK               -> new RockMutation(plugin);
            case HELLFIRE           -> new HellfireMutation(plugin);
            case DRAGONBORNE_POISON -> new DragonborneMutation(plugin, DragonborneMutation.Style.POISON);
            case DRAGONBORNE_FIRE   -> new DragonborneMutation(plugin, DragonborneMutation.Style.FIRE);
            case DRAGONBORNE_ARMOR  -> new DragonborneMutation(plugin, DragonborneMutation.Style.ARMOR);
            case DRAGONBORNE_LIGHT              -> new DragonborneLightMutation(plugin);
            case TRUE_SHOT          -> new TrueShotMutation(plugin);
            case LOVE               -> new LoveMutation(plugin);
            case DEBUFF             -> new DebuffMutation(plugin);
            case WITCH              -> new WitchMutation(plugin);
            case SHADOW             -> new ShadowMutation(plugin);
            case LIGHTNING          -> new LightningMutation(plugin);
            case NATURE             -> new NatureMutation(plugin);
        };
    }
}
