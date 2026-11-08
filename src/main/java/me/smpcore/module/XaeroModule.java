package me.smpcore.module;

import me.smpcore.SMPCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Xaero's Minimap / World Map control (replaces XaeroForceDisabler, remove that plugin when you use this).
 * Sends the hidden chat strings Xaero's mods listen for:
 *  - disable   -> the minimap and world map are switched off
 *  - fair-mode -> "fair play": radar, cave mode and other advantages are turned off
 * Priority: disable > fair-mode > reset. Global switches live under "xaero." in config.yml and can be
 * overridden per world with "xaero.worlds.<world>.disable|fair-mode". Players with
 * smpcore.bypass.xaero are never restricted.
 */
public final class XaeroModule implements Listener {
    private static final String PACKET_DISABLE = "§n§o§m§i§n§i§m§a§p";
    private static final String PACKET_FAIR = "§f§a§i§r§x§a§e§r§o";
    private static final String PACKET_RESET = "§r§e§s§e§t§x§a§e§r§o";
    private static final String PACKET_FAIR_NETHER_MAP = "§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r";
    private static final String PACKET_FAIR_NETHER_MINIMAP = "§x§a§e§r§o§m§m§n§e§t§h§e§r§i§s§f§a§i§r";

    private final SMPCore plugin;

    public XaeroModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    private boolean flag(World w, String key) {
        ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("xaero.worlds");
        if (w != null && worlds != null && worlds.isSet(w.getName() + "." + key)) {
            return worlds.getBoolean(w.getName() + "." + key);
        }
        return plugin.getConfig().getBoolean("xaero." + key);
    }

    /** Sends the right packet(s) to one player for the world they are in. */
    public void apply(Player p) {
        if (!p.isOnline()) return;
        if (!plugin.getConfig().getBoolean("xaero.enabled", true) || p.hasPermission("smpcore.bypass.xaero")) {
            send(p, PACKET_RESET);
            return;
        }
        World w = p.getWorld();
        if (flag(w, "disable")) {
            send(p, PACKET_DISABLE);
        } else if (flag(w, "fair-mode")) {
            send(p, PACKET_FAIR);
            if (w.getEnvironment() == World.Environment.NETHER) {
                send(p, PACKET_FAIR_NETHER_MAP);
                send(p, PACKET_FAIR_NETHER_MINIMAP);
            }
        } else {
            send(p, PACKET_RESET);
        }
    }

    public void applyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    private static void send(Player p, String packet) {
        p.sendMessage(Component.text(packet));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Xaero's mods are not ready to read chat right at join, so wait a moment
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(p), 20L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(p), 10L);
    }
}
