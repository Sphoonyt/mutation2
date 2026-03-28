package com.plugin.mutations.managers;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Utility for granting custom advancements to players.
 * Advancements must be provided by an external datapack — this class only handles granting them.
 * Namespace: "mutations", e.g. grant(player, "obtain_wind") → mutations:mutations/obtain_wind
 */
public class AdvancementManager {

    private final MutationsPlugin plugin;
    private static final String NAMESPACE = "mutations";

    public AdvancementManager(MutationsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Grant an advancement by its short ID (e.g. "obtain_wind").
     * Full key resolved as: mutations:mutations/<id>
     */
    public void grant(Player player, String advancementId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                NamespacedKey key = new NamespacedKey(NAMESPACE, NAMESPACE + "/" + advancementId);
                Advancement adv = Bukkit.getAdvancement(key);
                if (adv == null) return; // advancement not installed, skip silently
                AdvancementProgress prog = player.getAdvancementProgress(adv);
                for (String criterion : prog.getRemainingCriteria()) {
                    prog.awardCriteria(criterion);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to grant advancement " + advancementId + " to " + player.getName(), e);
            }
        });
    }

    /** Grant the obtain advancement for whatever mutation type the player just received. */
    public void grantMutationObtain(Player player, MutationType type) {
        String id = switch (type) {
            case WIND              -> "obtain_wind";
            case FROZEN            -> "obtain_frozen";
            case ROCK              -> "obtain_rock";
            case HELLFIRE          -> "obtain_hellfire";
            case BLOOD_SOLDIER     -> "obtain_blood_soldier";
            case BYPASS            -> "obtain_bypass";
            case DEBUFF            -> "obtain_debuff";
            case WITCH             -> "obtain_witch";
            case SHADOW            -> "obtain_shadow";
            case LIGHTNING         -> "obtain_lightning";
            case NATURE            -> "obtain_nature";
            case LOVE              -> "obtain_love";
            case TRUE_SHOT         -> "obtain_true_shot";
            case DRAGONBORNE_POISON -> "obtain_dragonborne_p";
            case DRAGONBORNE_FIRE   -> "obtain_dragonborne_f";
            case DRAGONBORNE_ARMOR  -> "obtain_dragonborne_a";
            case DRAGONBORNE_LIGHT  -> "obtain_dragonborne_l";
        };
        grant(player, id);
    }
}
