package com.plugin.mutations.managers;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.Mutation;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class MaceManager {

    public static final String MACE_KEY = "mace_of_meiosis";
    private final NamespacedKey key;
    private final MutationsPlugin plugin;

    // Tick task ID for the passive copy loop
    private int taskId = -1;

    public MaceManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, MACE_KEY);
        startPassiveLoop();
    }

    /** Create the Mace of Meiosis item */
    public ItemStack createMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5✦ §dMace of Meiosis §5✦");
        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Copies the §dpassive effects §7of all");
        lore.add("§7mutation holders within §b25 blocks§7.");
        lore.add("§7Must be held in main hand.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Check if an ItemStack is the Mace of Meiosis */
    public boolean isMace(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Every 5 ticks — scan mace holders and copy nearby mutation passives */
    private void startPassiveLoop() {
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (!isMace(held)) continue;
                copyNearbyPassives(player);
            }
        }, 5L, 5L).getTaskId();
    }

    private void copyNearbyPassives(Player maceHolder) {
        Collection<? extends Player> nearby = maceHolder.getWorld().getNearbyPlayers(
                maceHolder.getLocation(), 25, 25, 25);

        for (Player nearby_p : nearby) {
            if (nearby_p == maceHolder) continue;
            if (!plugin.getMutationManager().hasMutation(nearby_p)) continue;

            // Copy their current potion effects (these are their active passive buffs)
            for (PotionEffect effect : nearby_p.getActivePotionEffects()) {
                // Only copy positive/neutral effects, not curses
                if (isNegativeEffect(effect)) continue;
                // Apply with a short duration so it expires if mace user moves away
                // Duration = 30 ticks (1.5s), refreshed every 5 ticks
                PotionEffect copy = new PotionEffect(
                        effect.getType(), 30, effect.getAmplifier(), false, false);
                maceHolder.addPotionEffect(copy, true);
            }
        }
    }

    private boolean isNegativeEffect(PotionEffect effect) {
        org.bukkit.potion.PotionEffectType type = effect.getType();
        return type == org.bukkit.potion.PotionEffectType.SLOWNESS
                || type == org.bukkit.potion.PotionEffectType.MINING_FATIGUE
                || type == org.bukkit.potion.PotionEffectType.INSTANT_DAMAGE
                || type == org.bukkit.potion.PotionEffectType.NAUSEA
                || type == org.bukkit.potion.PotionEffectType.BLINDNESS
                || type == org.bukkit.potion.PotionEffectType.HUNGER
                || type == org.bukkit.potion.PotionEffectType.WEAKNESS
                || type == org.bukkit.potion.PotionEffectType.POISON
                || type == org.bukkit.potion.PotionEffectType.WITHER
                || type == org.bukkit.potion.PotionEffectType.LEVITATION
                || type == org.bukkit.potion.PotionEffectType.UNLUCK
                || type == org.bukkit.potion.PotionEffectType.BAD_OMEN
                || type == org.bukkit.potion.PotionEffectType.INFESTED
                || type == org.bukkit.potion.PotionEffectType.OOZING
                || type == org.bukkit.potion.PotionEffectType.WEAVING
                || type == org.bukkit.potion.PotionEffectType.WIND_CHARGED;
    }

    public void shutdown() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }
}
