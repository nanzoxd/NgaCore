package me.smpcore.module;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * Potion limiter. Banned potions cannot be brewed or used, but effects applied by
 * /effect give or other plugins still work while "allow-command-effects" is on.
 */
public final class PotionModule implements Listener {
    public static final List<String> EFFECTS = List.of(
            "speed", "slowness", "strength", "instant_health", "instant_damage", "jump_boost",
            "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility",
            "night_vision", "weakness", "poison", "slow_falling", "luck",
            "wind_charged", "weaving", "oozing", "infested");

    private static final String BYPASS = "smpcore.bypass.potion";

    private final SMPCore plugin;

    public PotionModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    public static PotionEffectType type(String key) {
        return Registry.EFFECT.get(NamespacedKey.minecraft(key));
    }

    public int limit(PotionEffectType t) {
        return plugin.getConfig().getInt("potion-limiter.effects." + t.getKey().getKey(), -1);
    }

    private boolean opt(String key) {
        return plugin.getConfig().getBoolean("potion-limiter." + key);
    }

    private boolean violates(PotionEffect pe) {
        int lim = limit(pe.getType());
        return lim == 0 || (lim > 0 && pe.getAmplifier() + 1 > lim);
    }

    private List<PotionEffect> effectsOf(ItemStack item) {
        List<PotionEffect> out = new ArrayList<>();
        if (item == null || !(item.getItemMeta() instanceof PotionMeta pm)) return out;
        PotionType base = pm.getBasePotionType();
        if (base != null) out.addAll(base.getPotionEffects());
        out.addAll(pm.getCustomEffects());
        return out;
    }

    private boolean anyViolation(ItemStack item) {
        for (PotionEffect pe : effectsOf(item)) if (violates(pe)) return true;
        return false;
    }

    private void warn(Player p) {
        Msg.bar(p, plugin.getConfig().getString("potion-limiter.message", "Limited potion."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        if (!opt("enabled") || !opt("block-use") || e.getPlayer().hasPermission(BYPASS)) return;
        if (anyViolation(e.getItem())) {
            e.setCancelled(true);
            warn(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!opt("enabled") || !opt("block-use")) return;
        ThrownPotion tp = e.getEntity();
        Player thrower = tp.getShooter() instanceof Player p ? p : null;
        if (thrower != null && thrower.hasPermission(BYPASS)) return;
        if (anyViolation(tp.getItem())) {
            e.setCancelled(true);
            if (thrower != null) warn(thrower);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLingering(LingeringPotionSplashEvent e) {
        if (!opt("enabled") || !opt("block-use")) return;
        ThrownPotion tp = e.getEntity();
        Player thrower = tp.getShooter() instanceof Player p ? p : null;
        if (thrower != null && thrower.hasPermission(BYPASS)) return;
        if (anyViolation(tp.getItem())) {
            e.setCancelled(true);
            if (thrower != null) warn(thrower);
        }
    }

    /** Catch-all: tipped arrows, clouds and (optionally) command / plugin effects. */
    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!opt("enabled") || !(e.getEntity() instanceof Player p) || p.hasPermission(BYPASS)) return;
        PotionEffect ne = e.getNewEffect();
        if (ne == null || !violates(ne)) return;
        switch (e.getCause()) {
            case POTION_DRINK, POTION_SPLASH, ARROW, AREA_EFFECT_CLOUD -> {
                if (opt("block-use")) e.setCancelled(true);
            }
            case COMMAND, PLUGIN -> {
                if (!opt("allow-command-effects")) e.setCancelled(true);
            }
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("potion-limiter.enabled") || !c.getBoolean("potion-limiter.block-brewing")) return;
        List<HumanEntity> viewers = e.getContents().getViewers();
        if (viewers.stream().anyMatch(v -> v.hasPermission(BYPASS))) return;
        for (ItemStack r : e.getResults()) {
            if (anyViolation(r)) {
                e.setCancelled(true);
                for (HumanEntity v : viewers) if (v instanceof Player p) warn(p);
                return;
            }
        }
    }
}
