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

        // Custom model data for resource pack textures
        meta.setCustomModelData(getCustomModelData(type));

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
        item.setAmount(1);
        // Force max stack size of 1 so orbs never stack
        item.setMaxStackSize(1);
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


    private int getCustomModelData(MutationType type) {
        return switch (type) {
            case WIND               -> 1001;
            case BLOOD_SOLDIER      -> 1002;
            case FROZEN             -> 1003;
            case BYPASS             -> 1004;
            case ROCK               -> 1005;
            case HELLFIRE           -> 1006;
            case DRAGONBORNE_POISON -> 1007;
            case DRAGONBORNE_FIRE   -> 1008;
            case DRAGONBORNE_ARMOR  -> 1009;
            case DRAGONBORNE_LIGHT              -> 1010;
            case TRUE_SHOT          -> 1011;
            case LOVE               -> 1012;
            case DEBUFF             -> 1013;
            case WITCH              -> 1014;
            case SHADOW             -> 1015;
            case LIGHTNING          -> 1016;
            case NATURE             -> 1017;
        };
    }

    private Material getMaterial(MutationType type) {
        // All orbs use PAPER with CustomModelData for resource pack support
        return Material.PAPER;
    }
}
