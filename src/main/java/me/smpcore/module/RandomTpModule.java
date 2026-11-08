package me.smpcore.module;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Random teleport for new players: the first time someone joins they are sent to a random safe spot
 * within random-tp.radius blocks of the centre (default: the spawn).
 */
public final class RandomTpModule implements Listener {
    private static final Set<Material> UNSAFE = EnumSet.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.LAVA, Material.WATER, Material.COBWEB, Material.BEDROCK);

    private final SMPCore plugin;

    public RandomTpModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("random-tp.enabled", false);
    }

    private World world() {
        String name = plugin.getConfig().getString("random-tp.world", "");
        World w = name == null || name.isBlank() ? null : Bukkit.getWorld(name);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    /** centre of the search: "spawn", "world" (the world spawn) or "x,z". */
    private double[] centre(World w) {
        String c = plugin.getConfig().getString("random-tp.center", "spawn");
        if (c != null && c.contains(",")) {
            try {
                String[] p = c.split(",");
                return new double[] {Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim())};
            } catch (NumberFormatException ignored) {
                // fall through to the spawn
            }
        }
        Location l = "world".equalsIgnoreCase(c) ? w.getSpawnLocation() : plugin.spawn.spawn();
        if (l.getWorld() != w) l = w.getSpawnLocation();
        return new double[] {l.getX(), l.getZ()};
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!enabled() || p.hasPlayedBefore() || p.hasPermission("smpcore.randomtp.bypass")) return;
        Bukkit.getScheduler().runTask(plugin, () -> teleport(p, false));
    }

    /** Finds a safe random spot and teleports the player there. {@code tell} also reports failures. */
    public void teleport(Player p, boolean tell) {
        World w = world();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            if (tell) Msg.send(p, Theme.red("Random TP only works in a normal (overworld) world."));
            return;
        }
        Msg.bar(p, Theme.c(Theme.DIM, "Finding a spot..."));
        attempt(p, w, 0, Math.max(1, plugin.getConfig().getInt("random-tp.tries", 30)), tell);
    }

    private void attempt(Player p, World w, int n, int max, boolean tell) {
        if (!p.isOnline()) return;
        if (n >= max) {
            if (tell) Msg.send(p, Theme.red("Couldn't find a safe spot."));
            return;
        }
        int radius = Math.max(50, plugin.getConfig().getInt("random-tp.radius", 5000));
        double[] c = centre(w);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble() * Math.PI * 2;
        // uniform over the whole disc
        double dist = Math.sqrt(r.nextDouble()) * radius;
        int x = (int) Math.floor(c[0] + Math.cos(angle) * dist);
        int z = (int) Math.floor(c[1] + Math.sin(angle) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Location spot = safe(w, x, z);
            if (spot == null) {
                attempt(p, w, n + 1, max, tell);
                return;
            }
            p.teleportAsync(spot).thenAccept(ok -> {
                if (!ok) return;
                Msg.send(p, "Welcome! You were sent to " + Theme.yellow(spot.getBlockX() + ", " + spot.getBlockY() + ", " + spot.getBlockZ()) + ".");
                Msg.sound(p, "minecraft:entity.enderman.teleport", 0.8f, 1.1f);
            });
        });
    }

    private Location safe(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block ground = w.getBlockAt(x, y, z);
        if (!ground.getType().isSolid() || UNSAFE.contains(ground.getType())) return null;
        Block feet = w.getBlockAt(x, y + 1, z), head = w.getBlockAt(x, y + 2, z);
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) return null;
        Location l = new Location(w, x + 0.5, y + 1, z + 0.5);
        return w.getWorldBorder().isInside(l) ? l : null;
    }
}
