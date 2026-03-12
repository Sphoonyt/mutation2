package com.plugin.mutations.mutations;

import com.plugin.mutations.MutationsPlugin;
import com.plugin.mutations.MutationType;
import com.plugin.mutations.utils.AbilityUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class LightningMutation extends Mutation {

    private final Random random = new Random();
    private final Set<UUID> arenaActive = new HashSet<>();

    public LightningMutation(MutationsPlugin plugin) {
        super(plugin);
    }

    @Override
    public MutationType getType() { return MutationType.LIGHTNING; }

    @Override
    public void onAssign(Player player) {
        player.sendMessage("§e⚡ You have been granted the §eLightning Mutation§e!");
        player.sendMessage("§ePassive: Zeus's Disciple — 5% chance to strike lightning on hit.");
    }

    @Override
    public void onRemove(Player player) {
        arenaActive.remove(player.getUniqueId());
    }

    // ── Passive: Zeus's Disciple — 5% lightning on hit ───────────────────────
    @Override
    public void onDamageDealt(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (random.nextFloat() < 0.05f) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            player.sendMessage("§e⚡ Zeus's Disciple triggered!");
        }
    }

    // ── A1: Strike (22s CD) — lightning on up to 3 nearest enemies ───────────
    @Override
    public void activateAbility1(Player player) {
        if (!checkCooldown(player, "A1_Strike", 22)) return;
        player.sendMessage("§e⚡ Strike!");

        World world = player.getWorld();
        List<Player> targets = getNearestEnemies(player, 20, 3);

        if (targets.isEmpty()) {
            player.sendMessage("§c No targets found!");
            plugin.getCooldownManager().clearAbility(player.getUniqueId(), "A1_Strike");
            return;
        }

        for (Player target : targets) {
            world.strikeLightning(target.getLocation());
            target.sendMessage("§e⚡ You were struck by " + player.getName() + "!");
        }

        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
    }

    // ── A2: Lightning Siphon (75s CD) — lightning on self, heals 5 hearts ────
    @Override
    public void activateAbility2(Player player) {
        if (!checkCooldown(player, "A2_Siphon", 75)) return;
        player.sendMessage("§e⚡ Lightning Siphon!");

        World world = player.getWorld();
        world.strikeLightningEffect(player.getLocation()); // effect only — no damage to self

        // Heal 5 hearts = 10 HP
        double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHp, player.getHealth() + 10.0));

        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.5f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0,1,0),
                60, 0.4, 0.8, 0.4, 0.2);
    }

    // ── A3: Lightning Arena (90s CD, 10s) — lightning ring every 1s ──────────
    @Override
    public void activateAbility3(Player player) {
        if (!checkCooldown(player, "A3_Arena", 90)) return;

        UUID uuid = player.getUniqueId();
        arenaActive.add(uuid);
        player.sendMessage("§e⚡ Lightning Arena! (10s)");

        World world = player.getWorld();
        Location center = player.getLocation().clone();

        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !arenaActive.contains(uuid)) { task.cancel(); return; }

            Location current = player.getLocation().clone();

            // Strike lightning at 8 points on a 3-block radius circle around player
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2 / 8) * i;
                Location strike = current.clone().add(
                        Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
                world.strikeLightningEffect(strike);
                // Damage any enemy near that strike point
                for (LivingEntity e : AbilityUtils.getNearbyTrusted(strike, 1.5, player, plugin)) {
                    AbilityUtils.trueDamage(e, 1.5, player);
                }
            }

            world.spawnParticle(Particle.ELECTRIC_SPARK, current.add(0,1,0),
                    30, 3, 0.5, 3, 0.3);

        }, 0, 20L);

        runLater(() -> {
            arenaActive.remove(uuid);
            if (player.isOnline()) {
                player.sendMessage("§e⚡ Lightning Arena ended.");
                player.getWorld().playSound(player.getLocation(),
                        Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.5f);
            }
        }, 200L);
    }

    @Override
    public void applyPassiveEffects(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        30, 0, false, false), true);
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,   30, 0, false, false), true);
    }

    @Override
    public String[] getCooldownKeys() {
        return new String[]{"A1_Strike", "A2_Siphon", "A3_Arena"};
    }

    @Override
    public boolean isAbilityActive(int slot, UUID uuid) {
        if (slot == 3) return arenaActive.contains(uuid);
        return false;
    }

    private List<Player> getNearestEnemies(Player source, double maxDist, int limit) {
        List<Player> result = new ArrayList<>();
        List<Player> candidates = new ArrayList<>();
        for (Player p : source.getWorld().getPlayers()) {
            if (p == source) continue;
            if (plugin.getTrustManager().isTrusted(source.getUniqueId(), p.getUniqueId())) continue;
            if (p.getLocation().distanceSquared(source.getLocation()) <= maxDist * maxDist) {
                candidates.add(p);
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(source.getLocation())));
        for (int i = 0; i < Math.min(limit, candidates.size()); i++) result.add(candidates.get(i));
        return result;
    }
}
