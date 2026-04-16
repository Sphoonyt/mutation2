package com.plugin.mutations;

import com.plugin.mutations.commands.WandCommand;
import com.plugin.mutations.commands.AbilityCommand;
import com.plugin.mutations.commands.GraceCommand;
import com.plugin.mutations.commands.MutationCommand;
import com.plugin.mutations.commands.TrustCommand;
import com.plugin.mutations.listeners.AbilityListener;
import com.plugin.mutations.listeners.FirstJoinListener;
import com.plugin.mutations.listeners.MutationDropListener;
import com.plugin.mutations.listeners.PassiveListener;
import com.plugin.mutations.managers.AdvancementManager;
import com.plugin.mutations.managers.ProgressManager;
import com.plugin.mutations.managers.CooldownManager;
import com.plugin.mutations.managers.MutationManager;
import com.plugin.mutations.managers.ActionBarManager;
import com.plugin.mutations.managers.MaceManager;
import com.plugin.mutations.listeners.WandListener;
import com.plugin.mutations.managers.WandManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MutationsPlugin extends JavaPlugin {

    private static MutationsPlugin instance;
    private MutationManager mutationManager;
    private CooldownManager cooldownManager;
    private TrustManager trustManager;
    private MaceManager maceManager;
    private ActionBarManager actionBarManager;
    private AdvancementManager advancementManager;
    private ProgressManager progressManager;
    private GraceCommand graceCommand;
    private WandManager wandManager;

    @Override
    public void onEnable() {
        instance = this;
        cooldownManager = new CooldownManager(this);
        mutationManager = new MutationManager(this);
        trustManager = new TrustManager(this);
        maceManager = new MaceManager(this);
        actionBarManager = new ActionBarManager(this);
        progressManager = new ProgressManager(this);
        advancementManager = new AdvancementManager(this);
        graceCommand = new GraceCommand(this);
        getCommand("grace").setExecutor(graceCommand);
        getCommand("grace").setTabCompleter(graceCommand);
        wandManager = new WandManager(this);
        getCommand("wand").setExecutor(new WandCommand(this));
        getServer().getPluginManager().registerEvents(new WandListener(this), this);

        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new FirstJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PassiveListener(this), this);
        getServer().getPluginManager().registerEvents(new MutationDropListener(this), this);

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
        actionBarManager.shutdown();
        mutationManager.cleanup();
        getLogger().info("Mutations Plugin disabled.");
    }

    public static MutationsPlugin getInstance() { return instance; }
    public MutationManager getMutationManager() { return mutationManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public MaceManager getMaceManager() { return maceManager; }
    public AdvancementManager getAdvancementManager() { return advancementManager; }
    public ProgressManager getProgressManager() { return progressManager; }
    public GraceCommand getGraceCommand() { return graceCommand; }
    public WandManager getWandManager() { return wandManager; }
    public ActionBarManager getActionBarManager() { return actionBarManager; }
}
