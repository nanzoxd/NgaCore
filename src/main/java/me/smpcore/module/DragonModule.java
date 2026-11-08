package me.smpcore.module;

import me.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/** Sets the Ender Dragon's health (vanilla 200, attribute cap 1024). */
public final class DragonModule implements Listener {
    private final SMPCore plugin;

    public DragonModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent e) {
        if (e.getEntity() instanceof EnderDragon d) Bukkit.getScheduler().runTask(plugin, () -> apply(d));
    }

    public void applyAll() {
        for (World w : Bukkit.getWorlds()) for (EnderDragon d : w.getEntitiesByClass(EnderDragon.class)) apply(d);
    }

    private void apply(EnderDragon d) {
        var c = plugin.getConfig();
        if (!c.getBoolean("dragon-health.enabled")) return;
        double h = Math.max(1, Math.min(1024, c.getDouble("dragon-health.health", 200)));
        AttributeInstance ai = d.getAttribute(Attribute.MAX_HEALTH);
        if (ai == null) return;
        ai.setBaseValue(h);
        d.setHealth(Math.min(h, ai.getValue()));
    }
}
