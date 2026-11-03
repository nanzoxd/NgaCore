package me.smpcore.module;

import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/** Closes the Nether and / or the End. */
public final class DimensionModule implements Listener {
    private final SMPCore plugin;

    public DimensionModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    private String closed(World.Environment env) {
        if (env == World.Environment.NETHER && plugin.getConfig().getBoolean("dimension-ban.nether")) return "The Nether";
        if (env == World.Environment.THE_END && plugin.getConfig().getBoolean("dimension-ban.end")) return "The End";
        return null;
    }

    private boolean block(Player p, Location to) {
        if (to == null || to.getWorld() == null || p.hasPermission("smpcore.bypass.dimension")) return false;
        String name = closed(to.getWorld().getEnvironment());
        if (name == null) return false;
        Msg.bar(p, plugin.getConfig().getString("dimension-ban.message", "<dimension> is closed.")
                .replace("<dimension>", name));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (block(e.getPlayer(), e.getTo())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!plugin.getConfig().getBoolean("dimension-ban.block-teleports")) return;
        TeleportCause c = e.getCause();
        if (c == TeleportCause.NETHER_PORTAL || c == TeleportCause.END_PORTAL) return;
        if (e.getTo() == null || e.getTo().getWorld() == null || e.getFrom().getWorld() == e.getTo().getWorld()) return;
        if (block(e.getPlayer(), e.getTo())) e.setCancelled(true);
    }
}
