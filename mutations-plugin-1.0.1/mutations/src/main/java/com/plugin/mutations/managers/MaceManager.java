package com.plugin.mutations.managers;

import com.plugin.mutations.MutationType;
import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.mutations.*;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Mace of Meiosis — copies all passives of mutation holders within 25 blocks.
 *
 * Each mace holder gets their own fresh Mutation instances (one per nearby mutation type).
 * These are re-created whenever the nearby set changes so internal UUID maps are clean.
 * Every 5 ticks:
 *   - updatePassives(): rebuild the holder→type→instance map from nearby players
 *   - tickPassives(): call onTick(holder) and applyPassiveEffects(holder) on each instance
 * Event hooks (onDamageDealt, onDamageReceived, potion effects, air jumps, fall damage)
 * are handled in PassiveListener via getLiveMutations(uuid).
 */
public class MaceManager {

    public static final String MACE_KEY = "mace_of_meiosis";
    private final NamespacedKey key;
    private final MutationsPlugin plugin;

    // uuid → (MutationType → fresh Mutation instance dedicated to that holder)
    private final Map<UUID, Map<MutationType, Mutation>> holderMutations = new HashMap<>();
    // Track which types a holder had last update so we only recreate on change
    private final Map<UUID, Set<MutationType>> lastNearbyTypes = new HashMap<>();

    private int taskId = -1;

    public MaceManager(MutationsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, MACE_KEY);
        startLoop();
    }

    public ItemStack createMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5✦ §dMace of Meiosis §5✦");
        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Copies §dall passives §7of mutation");
        lore.add("§7holders within §b25 blocks§7.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        meta.setCustomModelData(2001);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMace(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public boolean hasMaceInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMace(item)) return true;
        }
        return false;
    }

    public boolean isActive(UUID uuid) {
        Map<MutationType, Mutation> map = holderMutations.get(uuid);
        return map != null && !map.isEmpty();
    }

    public boolean hasPassive(UUID uuid, MutationType type) {
        Map<MutationType, Mutation> map = holderMutations.get(uuid);
        return map != null && map.containsKey(type);
    }

    public Mutation getLiveMutation(UUID uuid, MutationType type) {
        Map<MutationType, Mutation> map = holderMutations.get(uuid);
        return map == null ? null : map.get(type);
    }

    public Collection<Mutation> getLiveMutations(UUID uuid) {
        Map<MutationType, Mutation> map = holderMutations.get(uuid);
        return map == null ? Collections.emptyList() : map.values();
    }

    // ---- Main loop ----

    private void startLoop() {
        // Main 5-tick loop: update and tick passives
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!hasMaceInInventory(player)) {
                    cleanupHolder(uuid);
                    continue;
                }
                updatePassives(player);
                tickPassives(player);
            }
        }, 5L, 5L).getTaskId();

        // 1-tick strip loop: ensure slow falling and jump boost NEVER persist on mace holders
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!hasMaceInInventory(player)) continue;
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
            }
        }, 1L, 1L);
    }

    private void updatePassives(Player holder) {
        UUID uuid = holder.getUniqueId();

        // Collect which mutation types are nearby right now
        Set<MutationType> nowTypes = new HashSet<>();
        Map<MutationType, Mutation> sourceInstances = new HashMap<>();
        for (Player p : holder.getWorld().getNearbyPlayers(holder.getLocation(), 25, 25, 25)) {
            if (p == holder) continue;
            Mutation mut = plugin.getMutationManager().getMutation(p);
            if (mut == null) continue;
            nowTypes.add(mut.getType());
            sourceInstances.put(mut.getType(), mut);
        }

        Set<MutationType> lastTypes = lastNearbyTypes.getOrDefault(uuid, Collections.emptySet());

        // Only rebuild instances for types that changed (appeared or disappeared)
        if (!nowTypes.equals(lastTypes)) {
            Map<MutationType, Mutation> current = holderMutations.computeIfAbsent(uuid, k -> new HashMap<>());

            // Remove instances for types no longer nearby
            for (MutationType gone : new HashSet<>(current.keySet())) {
                if (!nowTypes.contains(gone)) {
                    current.get(gone).onRemove(holder);
                    current.remove(gone);
                }
            }

            // Add fresh instances for newly appearing types
            for (MutationType type : nowTypes) {
                if (!current.containsKey(type)) {
                    Mutation fresh = createFreshMutation(type);
                    if (fresh != null) {
                        // Don't call onAssign — avoids spamming welcome messages
                        current.put(type, fresh);
                    }
                }
            }

            lastNearbyTypes.put(uuid, new HashSet<>(nowTypes));
        }
    }

    private void tickPassives(Player holder) {
        Map<MutationType, Mutation> map = holderMutations.get(holder.getUniqueId());
        if (map == null || map.isEmpty()) return;
        for (Mutation mut : map.values()) {
            try {
                mut.onTick(holder);
                mut.applyPassiveEffects(holder);
            } catch (Exception ignored) {}
        }
        // Mace never grants slow falling or jump boost regardless of copied passives
        holder.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
        holder.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
    }

    private void cleanupHolder(UUID uuid) {
        Map<MutationType, Mutation> map = holderMutations.remove(uuid);
        lastNearbyTypes.remove(uuid);
        if (map != null) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                for (Mutation mut : map.values()) {
                    try { mut.onRemove(p); } catch (Exception ignored) {}
                }
            }
        }
    }

    /** Create a brand-new Mutation instance not tied to any existing player state. */
    private Mutation createFreshMutation(MutationType type) {
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
            case INVENTORY          -> new InventoryMutation(plugin);
        };
    }

    public void shutdown() {
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
    }
}
