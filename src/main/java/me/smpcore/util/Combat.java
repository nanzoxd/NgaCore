package me.smpcore.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class Combat {
    private Combat() {}

    /** The player behind a damage event (direct hit or projectile shooter), or null. */
    public static Player attacker(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Player s) return s;
            if (d instanceof Player p) return p;
        }
        return null;
    }
}
