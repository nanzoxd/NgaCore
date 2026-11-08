package me.smpcore.module;

import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.util.Combat;
import me.smpcore.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/** Caps the final damage of one hit per weapon / explosive type. */
public final class DamageLimiterModule implements Listener {
    public record Limiter(String key, Material icon, String label) {}

    public static final List<Limiter> LIMITERS = List.of(
            new Limiter("mace", Material.MACE, "Mace"),
            new Limiter("spear", Items.mat("IRON_SPEAR", Material.TRIDENT), "Spear"),
            new Limiter("trident", Material.TRIDENT, "Trident"),
            new Limiter("bow", Material.BOW, "Bow"),
            new Limiter("crossbow", Material.CROSSBOW, "Crossbow"),
            new Limiter("tnt", Material.TNT, "TNT"),
            new Limiter("tnt_minecart", Material.TNT_MINECART, "TNT Minecart"),
            new Limiter("end_crystal", Material.END_CRYSTAL, "End Crystal"),
            new Limiter("block_explosion", Material.RESPAWN_ANCHOR, "Anchor / Bed"),
            new Limiter("wind_charge", Material.WIND_CHARGE, "Wind Charge"));

    private final SMPCore plugin;

    public DamageLimiterModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("damage-limiters.enabled")) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;
        if (c.getBoolean("damage-limiters.players-only", true) && !(e.getEntity() instanceof Player)) return;
        String key = classify(e);
        if (key == null) return;
        String base = "damage-limiters.limits." + key;
        if (!c.getBoolean(base + ".enabled")) return;
        Player attacker = Combat.attacker(e);
        if (attacker != null && attacker.hasPermission("smpcore.bypass.damage")) return;
        double max = c.getDouble(base + ".max", 20.0);
        double fin = e.getFinalDamage();
        if (fin > max && fin > 0) e.setDamage(e.getDamage() * (max / fin));
    }

    private String classify(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof TNTPrimed) return "tnt";
            if (d instanceof ExplosiveMinecart) return "tnt_minecart";
            if (d instanceof EnderCrystal) return "end_crystal";
            if (d instanceof WindCharge) return "wind_charge";
            if (d instanceof Trident) return "trident";
            if (d instanceof AbstractArrow a) return a.isShotFromCrossbow() ? "crossbow" : "bow";
            DamageCause cause = e.getCause();
            if ((cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK)
                    && d instanceof LivingEntity le && le.getEquipment() != null) {
                Material m = le.getEquipment().getItemInMainHand().getType();
                if (m == Material.MACE) return "mace";
                if (Items.isSpear(m)) return "spear";
            }
            return null;
        }
        if (e.getCause() == DamageCause.BLOCK_EXPLOSION) return "block_explosion";
        return null;
    }
}
