package com.plugin.mutations;

import com.plugin.mutations.commands.AbilityCommand;
import com.plugin.mutations.commands.MutationCommand;
import com.plugin.mutations.commands.TrustCommand;
import com.plugin.mutations.listeners.AbilityListener;
import com.plugin.mutations.listeners.FirstJoinListener;
import com.plugin.mutations.listeners.PassiveListener;
import com.plugin.mutations.managers.CooldownManager;
import com.plugin.mutations.managers.MutationManager;
import com.plugin.mutations.managers.MaceManager;
import com.plugin.mutations.managers.TrustManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MutationsPlugin extends JavaPlugin {

    private static MutationsPlugin instance;
    private MutationManager mutationManager;
    private CooldownManager cooldownManager;
    private TrustManager trustManager;
    private MaceManager maceManager;

    @Override
    public void onEnable() {
        instance = this;
        cooldownManager = new CooldownManager();
        mutationManager = new MutationManager(this);
        trustManager = new TrustManager();
        maceManager = new MaceManager(this);

        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new PassiveListener(this), this);

        getCommand("mutation").setExecutor(new MutationCommand(this));
        AbilityCommand abilityCmd = new AbilityCommand(this);
        getCommand("ability").setExecutor(abilityCmd);
        getCommand("ability").setTabCompleter(abilityCmd);
        TrustCommand trustCmd = new TrustCommand(this);
        getCommand("trust").setExecutor(trustCmd);
        getCommand("trust").setTabCompleter(trustCmd);

        getLogger().info("╔══════════════════════════╗");
        getLogger().info("║   Mutations Plugin v1.0  ║");
        getLogger().info("║       Enabled!           ║");
        getLogger().info("╚══════════════════════════╝");
    }

    @Override
    public void onDisable() {
        maceManager.shutdown();
        mutationManager.cleanup();
        getLogger().info("Mutations Plugin disabled.");
    }

    public static MutationsPlugin getInstance() { return instance; }
    public MutationManager getMutationManager() { return mutationManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public MaceManager getMaceManager() { return maceManager; }
}
