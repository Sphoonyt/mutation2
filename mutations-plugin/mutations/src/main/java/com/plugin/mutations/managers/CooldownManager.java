package com.plugin.mutations.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    // UUID -> (abilityKey -> lastUsedMillis)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public boolean isOnCooldown(UUID player, String ability) {
        Map<String, Long> map = cooldowns.get(player);
        if (map == null) return false;
        Long last = map.get(ability);
        if (last == null) return false;
        return System.currentTimeMillis() < last;
    }

    public long getRemainingSeconds(UUID player, String ability) {
        Map<String, Long> map = cooldowns.get(player);
        if (map == null) return 0;
        Long last = map.get(ability);
        if (last == null) return 0;
        long rem = last - System.currentTimeMillis();
        return rem <= 0 ? 0 : (rem / 1000) + 1;
    }

    public void setCooldown(UUID player, String ability, int seconds) {
        cooldowns.computeIfAbsent(player, k -> new HashMap<>())
                 .put(ability, System.currentTimeMillis() + (seconds * 1000L));
    }

    public void clearCooldowns(UUID player) {
        cooldowns.remove(player);
    }

    public void clearAbility(UUID player, String ability) {
        Map<String, Long> map = cooldowns.get(player);
        if (map != null) map.remove(ability);
    }
}
