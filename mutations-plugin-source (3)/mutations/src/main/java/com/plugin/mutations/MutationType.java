package com.plugin.mutations;

public enum MutationType {
    WIND("wind", "§b🌪️ Wind Mutation"),
    BLOOD_SOLDIER("blood_soldier", "§c🩸 Blood-Soldier Mutation"),
    FROZEN("frozen", "§f❄️ Frozen Mutation"),
    BYPASS("bypass", "§7🚫 Bypass Mutation"),
    ROCK("rock", "§6🪨 Rock Mutation"),
    HELLFIRE("hellfire", "§c🔥 Hellfire Mutation"),
    DRAGONBORNE_POISON("dragonborne_poison", "§2🐉 Dragonborne (Poison)"),
    DRAGONBORNE_FIRE("dragonborne_fire", "§c🐉 Dragonborne (Fire)"),
    DRAGONBORNE_ARMOR("dragonborne_armor", "§8🐉 Dragonborne (Armor)"),
    LIGHT("light", "§e☀️ Light Mutation"),
    TRUE_SHOT("true_shot", "§a🏹 True Shot Mutation"),
    LOVE("love", "§d💖 Love Mutation");

    private final String id;
    private final String displayName;

    MutationType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }

    public static MutationType fromId(String id) {
        for (MutationType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
