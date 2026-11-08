package me.smpcore.module;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.PortalType;
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

    /**
     * A boat, minecart, horse ... with players on board goes through a portal as one entity: the passengers
     * never get their own PlayerPortalEvent. So the vehicle itself is checked, and it stays out when any passenger
     * (also a passenger of a passenger) is a player who may not enter.
     */
    @EventHandler(ignoreCancelled = true)
    public void onVehiclePortal(EntityPortalEvent e) {
        if (e.getEntity() instanceof Player) return;
        List<Player> riders = new ArrayList<>();
        collect(e.getEntity(), riders);
        if (riders.isEmpty()) return;
        World.Environment target = e.getTo() != null && e.getTo().getWorld() != null ? e.getTo().getWorld().getEnvironment() : fallbackTarget(e);
        String name = target == null ? null : closed(target);
        if (name == null) return;
        boolean any = false;
        for (Player p : riders) {
            if (p.hasPermission("smpcore.bypass.dimension")) continue;
            any = true;
            Msg.bar(p, plugin.getConfig().getString("dimension-ban.message", "<dimension> is closed.").replace("<dimension>", name));
        }
        if (any) e.setCancelled(true);
    }

    private void collect(Entity vehicle, List<Player> out) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player p) out.add(p);
            collect(passenger, out);
        }
    }

    /** When the event has no destination yet, work it out from the portal type and where the vehicle is. */
    private World.Environment fallbackTarget(EntityPortalEvent e) {
        World.Environment from = e.getFrom().getWorld().getEnvironment();
        PortalType t = e.getPortalType();
        if (t == PortalType.NETHER) return from == World.Environment.NETHER ? World.Environment.NORMAL : World.Environment.NETHER;
        if (t == PortalType.ENDER) return from == World.Environment.THE_END ? World.Environment.NORMAL : World.Environment.THE_END;
        return null;
    }

    /** Last safety net: whatever way a player got into a closed dimension, they are sent back. */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("smpcore.bypass.dimension")) return;
        String name = closed(p.getWorld().getEnvironment());
        if (name == null) return;
        Msg.bar(p, plugin.getConfig().getString("dimension-ban.message", "<dimension> is closed.").replace("<dimension>", name));
        Location back = e.getFrom().getSpawnLocation();
        if (closed(e.getFrom().getEnvironment()) != null) back = plugin.spawn.spawn();
        final Location dest = back;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> p.teleportAsync(dest));
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
