package me.smpcore.module;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * /spawn, /setspawn and warps. Teleports wait a few seconds (spawn.delay-seconds) with a countdown and are
 * cancelled when the player moves (or takes damage). Locations are saved in plugins/SMPCore/locations.yml.
 */
public final class SpawnModule implements Listener {
    private record Pending(BukkitTask task, Location from) {}

    private final SMPCore plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public SpawnModule(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        load();
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save locations.yml: " + e.getMessage());
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("spawn.enabled", true);
    }

    public boolean warpsEnabled() {
        return plugin.getConfig().getBoolean("warps.enabled", true);
    }

    public int delay() {
        return Math.max(0, plugin.getConfig().getInt("spawn.delay-seconds", 5));
    }

    /** The saved spawn, or the main world's spawn when none is set. */
    public Location spawn() {
        Location l = data.getLocation("spawn");
        return l != null && l.getWorld() != null ? l : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public boolean hasSpawn() {
        Location l = data.getLocation("spawn");
        return l != null && l.getWorld() != null;
    }

    public void setSpawn(Location l) {
        data.set("spawn", l);
        save();
    }

    public List<String> warps() {
        ConfigurationSection s = data.getConfigurationSection("warps");
        return s == null ? List.of() : s.getKeys(false).stream().sorted().toList();
    }

    public Location warp(String name) {
        Location l = data.getLocation("warps." + name.toLowerCase());
        return l != null && l.getWorld() != null ? l : null;
    }

    public void setWarp(String name, Location l) {
        data.set("warps." + name.toLowerCase(), l);
        save();
    }

    public boolean delWarp(String name) {
        if (!data.contains("warps." + name.toLowerCase())) return false;
        data.set("warps." + name.toLowerCase(), null);
        save();
        return true;
    }

    public static boolean validName(String n) {
        return n.matches("[A-Za-z0-9_-]{1,24}");
    }

    /** Teleports after the configured wait. label is shown in the messages, e.g. "spawn" or "warp Mines". */
    public void teleport(Player p, Location dest, String label) {
        cancel(p, false);
        int wait = delay();
        if (wait <= 0 || p.hasPermission("smpcore.spawn.bypass")) {
            go(p, dest, label);
            return;
        }
        Location from = p.getLocation();
        boolean move = plugin.getConfig().getBoolean("spawn.cancel-on-move", true);
        Msg.send(p, "Teleporting to " + Theme.blue(label) + " in " + Theme.yellow(wait + "s") + "."
                + (move ? " " + Theme.red("Don't move.") : ""));
        int[] left = {wait};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) {
                cancel(p, false);
                return;
            }
            if (left[0] <= 0) {
                pending.remove(p.getUniqueId()).task().cancel();
                go(p, dest, label);
                return;
            }
            Msg.bar(p, Theme.c(Theme.DIM, "Teleporting in ") + Theme.yellow(left[0]) + Theme.c(Theme.DIM, "s"));
            Msg.sound(p, "minecraft:block.note_block.hat", 0.7f, 1.4f);
            left[0]--;
        }, 0L, 20L);
        pending.put(p.getUniqueId(), new Pending(task, from));
    }

    private void go(Player p, Location dest, String label) {
        p.teleportAsync(dest).thenAccept(ok -> {
            if (!ok) return;
            Msg.send(p, "Teleported to " + Theme.blue(label) + ".");
            Msg.sound(p, "minecraft:entity.enderman.teleport", 0.8f, 1.1f);
        });
    }

    /** Cancels a waiting teleport. Returns true when there was one. */
    public boolean cancel(Player p, boolean tell) {
        Pending pe = pending.remove(p.getUniqueId());
        if (pe == null) return false;
        pe.task().cancel();
        if (tell) {
            Msg.send(p, Theme.red("Teleport cancelled."));
            Msg.bar(p, Theme.red("Teleport cancelled"));
            Msg.sound(p, "minecraft:block.note_block.bass", 0.8f, 0.8f);
        }
        return true;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Pending pe = pending.get(e.getPlayer().getUniqueId());
        if (pe == null || !plugin.getConfig().getBoolean("spawn.cancel-on-move", true) || !e.hasChangedPosition()) return;
        Location f = pe.from(), t = e.getTo();
        if (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ()) {
            cancel(e.getPlayer(), true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && pending.containsKey(p.getUniqueId())
                && plugin.getConfig().getBoolean("spawn.cancel-on-damage", true)) {
            cancel(p, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancel(e.getPlayer(), false);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (enabled() && plugin.getConfig().getBoolean("spawn.respawn-at-spawn", false) && !e.isBedSpawn() && !e.isAnchorSpawn()) {
            e.setRespawnLocation(spawn());
        }
    }
}
