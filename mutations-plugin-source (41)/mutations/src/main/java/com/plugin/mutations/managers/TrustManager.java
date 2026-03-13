package com.plugin.mutations.managers;

import java.util.*;

/**
 * Manages per-player trust lists.
 * If player A trusts player B, A's abilities will not affect B (unless the ability
 * is explicitly marked as ignoring trust, like Love's passive Devotion).
 */
public class TrustManager {

    // owner UUID -> set of trusted player UUIDs
    private final Map<UUID, Set<UUID>> trustMap = new HashMap<>();

    /** Add a trusted player */
    public void trust(UUID owner, UUID target) {
        trustMap.computeIfAbsent(owner, k -> new HashSet<>()).add(target);
    }

    /** Remove a trusted player */
    public void untrust(UUID owner, UUID target) {
        Set<UUID> set = trustMap.get(owner);
        if (set != null) set.remove(target);
    }

    /** Check if owner trusts target */
    public boolean isTrusted(UUID owner, UUID target) {
        Set<UUID> set = trustMap.get(owner);
        return set != null && set.contains(target);
    }

    /** Get all trusted UUIDs for an owner */
    public Set<UUID> getTrusted(UUID owner) {
        return trustMap.getOrDefault(owner, Collections.emptySet());
    }

    /** Clear all trust entries for an owner */
    public void clearTrust(UUID owner) {
        trustMap.remove(owner);
    }
}
