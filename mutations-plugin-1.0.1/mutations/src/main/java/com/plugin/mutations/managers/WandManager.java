package com.plugin.mutations.managers;

import com.plugin.mutations.MutationsPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class WandManager {

    public static final String WAND_KEY = "ability_wand";
    private final NamespacedKey key;
    private final MutationsPlugin plugin;

    public WandManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, WAND_KEY);
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6✦ §eAbility Wand §6✦");
        meta.setLore(List.of(
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
            "§e§lRight-Click       §7→ §aAbility 1",
            "§e§lShift + Left-Click §7→ §aAbility 2",
            "§e§lShift + Right-Click§7→ §aAbility 3",
            "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        meta.setCustomModelData(4001);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public NamespacedKey getKey() { return key; }
}
