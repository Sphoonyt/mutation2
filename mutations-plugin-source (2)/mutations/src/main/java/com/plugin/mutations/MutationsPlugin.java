package com.plugin.mutations;

import com.plugin.mutations.commands.MutationCommand;
import com.plugin.mutations.listeners.AbilityListener;
import com.plugin.mutations.listeners.PassiveListener;
import com.plugin.mutations.managers.CooldownManager;
import com.plugin.mutations.managers.MutationManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MutationsPlugin extends JavaPlugin {

    private static MutationsPlugin instance;
    private MutationManager mutationManager;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        instance = this;
        cooldownManager = new CooldownManager();
        mutationManager = new MutationManager(this);

        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new PassiveListener(this), this);

        getCommand("mutation").setExecutor(new MutationCommand(this));

        getLogger().info("╔══════════════════════════╗");
        getLogger().info("║   Mutations Plugin v1.0  ║");
        getLogger().info("║       Enabled!           ║");
        getLogger().info("╚══════════════════════════╝");
    }

    @Override
    public void onDisable() {
        mutationManager.cleanup();
        getLogger().info("Mutations Plugin disabled.");
    }

    public static MutationsPlugin getInstance() { return instance; }
    public MutationManager getMutationManager() { return mutationManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
}
