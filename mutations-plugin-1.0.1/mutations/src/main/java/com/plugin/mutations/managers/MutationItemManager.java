package com.plugin.mutations.managers;

import com.plugin.mutations.MutationType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MutationItemManager {

    public static final String MUTATION_KEY = "mutation_type";
    private final NamespacedKey key;

    public MutationItemManager(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, MUTATION_KEY);
    }

    public NamespacedKey getKey() { return key; }

    /** Create the physical mutation item for a given type */
    public ItemStack createMutationItem(MutationType type) {
        Material mat = getMaterial(type);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(type.getDisplayName() + " §r§7[Mutation Orb]");
        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Right-click to §aabsorb§7 this mutation.");
        lore.add("§7Use §f/mutation withdraw §7to reclaim it.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§8ID: " + type.getId());
        meta.setLore(lore);

        // Glow effect + fully unbreakable
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // Store mutation type in persistent data
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.STRING, type.getId());

        item.setItemMeta(meta);
        return item;
    }

    /** Get the MutationType from a physical item, or null if not a mutation item */
    public MutationType getMutationType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.STRING)) return null;
        String id = pdc.get(key, PersistentDataType.STRING);
        return MutationType.fromId(id);
    }

    /** Check if an item is a mutation orb */
    public boolean isMutationItem(ItemStack item) {
        return getMutationType(item) != null;
    }

    private Material getMaterial(MutationType type) {
        return switch (type) {
            case WIND -> Material.FEATHER;
            case BLOOD_SOLDIER -> Material.FERMENTED_SPIDER_EYE;
            case FROZEN -> Material.BLUE_ICE;
            case BYPASS -> Material.ENDER_EYE;
            case ROCK -> Material.COBBLESTONE;
            case HELLFIRE -> Material.BLAZE_ROD;
            case DRAGONBORNE_POISON -> Material.DRAGON_BREATH;
            case DRAGONBORNE_FIRE -> Material.FIRE_CHARGE;
            case DRAGONBORNE_ARMOR -> Material.IRON_INGOT;
            case LIGHT -> Material.GLOWSTONE;
            case TRUE_SHOT -> Material.ARROW;
            case LOVE -> Material.PINK_DYE;
            case DEBUFF -> Material.FERMENTED_SPIDER_EYE;
        };
    }
}
