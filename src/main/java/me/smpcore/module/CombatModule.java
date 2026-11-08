package me.smpcore.module;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.smpcore.SMPCore;
import me.smpcore.util.Combat;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.util.Vector;

/**
 * Combat tag. Hitting or being hit by a player starts a timer (combat.seconds, default 20) that shows in the
 * action bar as "🕛 ᴄᴏᴍʙᴀᴛ : 20". While it runs: blocked commands are refused, logging out kills the player and
 * the player can't walk into a safe zone. Players inside a safe zone (the spawn area and any zones made with
 * /smp safezone) can't hit or be hit by other players.
 */
public final class CombatModule implements Listener {
    private record Zone(String name, String world, double x, double z, double radius) {}

    private final SMPCore plugin;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();   // combat end, epoch millis
    private final Set<UUID> kicked = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    private volatile List<Zone> zones = List.of();
    private final Map<UUID, Boolean> zoneState = new ConcurrentHashMap<>();   // last known "in a safe zone" per player
    private WorldGuardHook wg;
    private boolean wgTried;

    /** A player who disconnected in combat and may still come back (combat.rejoin). */
    private record Pending(long deadline, long combatLeft, String name, boolean expired) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final File pendingFile;

    public CombatModule(SMPCore plugin) {
        this.plugin = plugin;
        this.pendingFile = new File(plugin.getDataFolder(), "combat-rejoin.yml");
        load();
        loadPending();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    // ---------------------------------------------------------------- config

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    public boolean enabled() {
        return c().getBoolean("combat.enabled", true);
    }

    public long millis() {
        return Math.max(1000L, (long) (c().getDouble("combat.seconds", 20) * 1000));
    }

    private String msg(String key) {
        return c().getString("combat.messages." + key, "");
    }

    private boolean bypass(Player p) {
        return p.hasPermission("smpcore.bypass.combat");
    }

    /** Reads the safe zones from config.yml. Called on start, on /smp reload and after zone changes. */
    public void load() {
        List<Zone> out = new ArrayList<>();
        ConfigurationSection s = c().getConfigurationSection("combat.safe-zone.zones");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                ConfigurationSection z = s.getConfigurationSection(k);
                if (z == null) continue;
                out.add(new Zone(k, z.getString("world", ""), z.getDouble("x"), z.getDouble("z"), z.getDouble("radius", 10)));
            }
        }
        zones = out;
    }

    // ---------------------------------------------------------------- combat tag

    public boolean inCombat(Player p) {
        Long t = until.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    /** Whole seconds left, 0 when not in combat. */
    public int secondsLeft(Player p) {
        Long t = until.get(p.getUniqueId());
        if (t == null) return 0;
        return (int) Math.max(0, (t - System.currentTimeMillis() + 999) / 1000);
    }

    public void tag(Player p) {
        if (!enabled() || bypass(p)) return;
        boolean fresh = !inCombat(p);
        until.put(p.getUniqueId(), System.currentTimeMillis() + millis());
        if (fresh) {
            Msg.send(p, msg("tagged"));
            title(p, "tagged");
            Msg.sound(p, "block.note_block.bass", 0.8f, 0.7f);
        }
    }

    public void clear(Player p, boolean tell) {
        if (until.remove(p.getUniqueId()) != null && tell) end(p);
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p, true);
    }

    private void end(Player p) {
        Msg.send(p, msg("ended"));
        title(p, "ended");
        Msg.sound(p, "entity.experience_orb.pickup", 0.8f, 1.4f);
    }

    /** Optional big text: combat.messages.<key>-title and <key>-subtitle (switch: combat.titles). */
    private void title(Player p, String key) {
        if (!c().getBoolean("combat.titles", true)) return;
        String t = msg(key + "-title"), s = msg(key + "-subtitle");
        if (t.isEmpty() && s.isEmpty()) return;
        p.showTitle(Title.title(Theme.parse(t), Theme.parse(s),
                Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(400))));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean on = enabled();
        for (var it = until.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline()) continue;   // the quit handler removes them
            if (!on) {
                it.remove();
                continue;
            }
            if (now >= en.getValue()) {
                it.remove();
                end(p);
                continue;
            }
            long left = (en.getValue() - now + 999) / 1000;
            Msg.bar(p, msg("bar").replace("<time>", String.valueOf(left)));
        }
    }

    // ---------------------------------------------------------------- tagging and safe-zone pvp

    /** Players in a safe zone can't hit or be hit by other players. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSafeHit(EntityDamageByEntityEvent e) {
        if (!enabled() || !c().getBoolean("combat.safe-zone.block-pvp", true)) return;
        if (!(e.getEntity() instanceof Player v)) return;
        Player a = Combat.attacker(e);
        if (a == null || a.equals(v) || bypass(a)) return;
        if (inSafeZone(v.getLocation()) || inSafeZone(a.getLocation())) {
            e.setCancelled(true);
            Msg.bar(a, msg("safe-pvp"));
        }
    }

    /** Runs last so it only sees hits that really happened. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!enabled() || !(e.getEntity() instanceof Player v)) return;
        Player a = Combat.attacker(e);
        if (a == null || a.equals(v)) return;
        tag(a);
        tag(v);
    }

    // ---------------------------------------------------------------- command blocking

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!enabled() || !c().getBoolean("combat.commands.enabled", true) || !inCombat(p) || bypass(p)) return;
        String raw = e.getMessage().length() > 1 ? e.getMessage().substring(1).trim() : "";
        if (raw.isEmpty()) return;
        String name = raw.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (name.indexOf(':') >= 0) name = name.substring(name.indexOf(':') + 1);   // minecraft:tp -> tp

        boolean whitelist = "whitelist".equalsIgnoreCase(c().getString("combat.commands.mode", "blacklist"));
        List<String> list = lower(c().getStringList("combat.commands." + (whitelist ? "allowed" : "blocked")));
        boolean listed = false;
        for (String n : names(name)) {
            if (list.contains(n)) {
                listed = true;
                break;
            }
        }
        if (whitelist == listed) return;   // blacklist: not listed -> fine, whitelist: listed -> fine

        e.setCancelled(true);
        Msg.send(p, msg("command").replace("<command>", Theme.esc(name)).replace("<time>", String.valueOf(secondsLeft(p))));
        Msg.sound(p, "block.vault.insert_item_fail", 0.8f, 1.0f);
    }

    /** The typed name plus the real command name and all its aliases, so /tell is caught when "msg" is listed. */
    private Set<String> names(String typed) {
        Set<String> out = new HashSet<>();
        out.add(typed);
        Command cmd = Bukkit.getCommandMap().getCommand(typed);
        if (cmd != null) {
            out.add(cmd.getName().toLowerCase(Locale.ROOT));
            for (String al : cmd.getAliases()) out.add(al.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(s.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        return out;
    }

    // ---------------------------------------------------------------- logout, death

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        if (c().getBoolean("combat.logout.exempt-kick", true)) kicked.add(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean was = inCombat(p);
        long combatEnd = until.getOrDefault(id, 0L);
        until.remove(id);
        lastWarn.remove(id);
        zoneState.remove(id);
        boolean wasKicked = kicked.remove(id);
        if (Bukkit.isStopping()) return;   // a server restart isn't a combat logout
        if (!was || wasKicked || !enabled() || bypass(p) || !c().getBoolean("combat.logout.punish", true)) return;
        if (rejoinEnabled()) {
            // rejoin time: no penalty yet, the player has a while to come back
            startRejoin(p, combatEnd);
            return;
        }
        if (c().getBoolean("combat.logout.kill", true)) p.setHealth(0.0);
        if (c().getBoolean("combat.logout.broadcast", true)) {
            Bukkit.broadcast(Msg.body(msg("logout").replace("<player>", Theme.esc(p.getName()))));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        until.remove(e.getEntity().getUniqueId());
    }

    // ---------------------------------------------------------------- rejoin time (combat.rejoin)

    public boolean rejoinEnabled() {
        return c().getBoolean("combat.rejoin.enabled", false);
    }

    public int rejoinSeconds() {
        return Math.max(1, c().getInt("combat.rejoin.seconds", 30));
    }

    private void startRejoin(Player p, long combatEnd) {
        long now = System.currentTimeMillis();
        long deadline = now + rejoinSeconds() * 1000L;
        UUID id = p.getUniqueId();
        pending.put(id, new Pending(deadline, Math.max(0, combatEnd - now), p.getName(), false));
        savePending();
        if (c().getBoolean("combat.rejoin.broadcast", true)) {
            Bukkit.broadcast(Msg.body(msg("rejoin-quit").replace("<player>", Theme.esc(p.getName())).replace("<time>", String.valueOf(rejoinSeconds()))));
        }
        scheduleExpiry(id, rejoinSeconds() * 20L);
    }

    private void scheduleExpiry(UUID id, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending pe = pending.get(id);
            if (pe == null || pe.expired()) return;
            if (Bukkit.getPlayer(id) != null) return;   // they came back, onJoin handled it
            pending.put(id, new Pending(pe.deadline(), pe.combatLeft(), pe.name(), true));
            savePending();
            if (c().getBoolean("combat.rejoin.broadcast", true)) {
                Bukkit.broadcast(Msg.body(msg("rejoin-expired").replace("<player>", Theme.esc(pe.name()))));
            }
        }, Math.max(1L, ticks));
    }

    /**
     * A player who is offline can't be killed through the server API, so a player who didn't come back in time
     * is killed the moment they join again (they appear where they logged out, so their items drop there).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRejoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Pending pe = pending.remove(p.getUniqueId());
        if (pe == null) return;
        savePending();
        boolean late = pe.expired() || System.currentTimeMillis() > pe.deadline();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (late) {
                Msg.send(p, msg("rejoin-late"));
                title(p, "rejoin-late");
                Msg.sound(p, "entity.wither.hurt", 0.8f, 1.0f);
                if (c().getBoolean("combat.logout.kill", true)) p.setHealth(0.0);
            } else {
                Msg.send(p, msg("rejoin-ok").replace("<time>", String.valueOf(Math.max(1, pe.combatLeft() / 1000))));
                title(p, "rejoin-ok");
                Msg.sound(p, "entity.player.levelup", 0.8f, 1.2f);
                if (c().getBoolean("combat.rejoin.retag", true) && pe.combatLeft() > 0) {
                    until.put(p.getUniqueId(), System.currentTimeMillis() + pe.combatLeft());
                }
            }
        }, 10L);
    }

    private void loadPending() {
        if (!pendingFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(pendingFile);
        long now = System.currentTimeMillis();
        for (String k : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                long deadline = y.getLong(k + ".deadline");
                boolean expired = y.getBoolean(k + ".expired") || now >= deadline;
                pending.put(id, new Pending(deadline, y.getLong(k + ".combat-left"), y.getString(k + ".name", "?"), expired));
                if (!expired) scheduleExpiry(id, (deadline - now) / 50L);
            } catch (IllegalArgumentException ignored) {
                // not a uuid
            }
        }
    }

    private void savePending() {
        YamlConfiguration y = new YamlConfiguration();
        pending.forEach((id, pe) -> {
            y.set(id + ".deadline", pe.deadline());
            y.set(id + ".combat-left", pe.combatLeft());
            y.set(id + ".name", pe.name());
            y.set(id + ".expired", pe.expired());
        });
        try {
            y.save(pendingFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save combat-rejoin.yml: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- safe zones

    /** True when the location is inside the spawn area or any safe zone (flat distance, any height). */
    public boolean inSafeZone(Location l) {
        if (l == null || l.getWorld() == null || !c().getBoolean("combat.safe-zone.enabled", true)) return false;
        double sr = c().getDouble("combat.safe-zone.spawn-radius", 25);
        if (sr > 0 && plugin.spawn != null) {
            Location s = plugin.spawn.spawn();
            if (s.getWorld() != null && s.getWorld().equals(l.getWorld()) && flat(l, s.getX(), s.getZ()) <= sr * sr) return true;
        }
        String w = l.getWorld().getName();
        for (Zone z : zones) {
            if (z.world().equals(w) && flat(l, z.x(), z.z()) <= z.radius() * z.radius()) return true;
        }
        // automatic safe zones: every WorldGuard region with pvp: deny, and every warp when warps-radius is set
        if (c().getBoolean("combat.safe-zone.auto.worldguard", true)) {
            WorldGuardHook h = worldGuard();
            if (h != null && h.pvpDenied(l)) return true;
        }
        double wr = c().getDouble("combat.safe-zone.auto.warps-radius", 0);
        if (wr > 0 && plugin.spawn != null) {
            for (String name : plugin.spawn.warps()) {
                Location wl = plugin.spawn.warp(name);
                if (wl != null && wl.getWorld() != null && wl.getWorld().equals(l.getWorld()) && flat(l, wl.getX(), wl.getZ()) <= wr * wr) return true;
            }
        }
        return false;
    }

    /** The WorldGuard link, created the first time it's needed. null when WorldGuard isn't installed. */
    private WorldGuardHook worldGuard() {
        if (!wgTried) {
            wgTried = true;
            if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
                wg = WorldGuardHook.create();
                plugin.getLogger().info(wg != null ? "WorldGuard found: regions with pvp: deny are safe zones."
                        : "WorldGuard found but its API couldn't be used; WorldGuard safe zones are off.");
            }
        }
        return wg;
    }

    /** For /smp safezone list. */
    public String wgStatus() {
        if (!c().getBoolean("combat.safe-zone.auto.worldguard", true)) return "off";
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return "WorldGuard not installed";
        return worldGuard() != null ? "connected (pvp: deny regions are safe zones)" : "found, but the API couldn't be used";
    }

    // ---------------------------------------------------------------- "you entered / left a safe zone" messages

    private void notifyZone(Player p, Location to, boolean tell) {
        if (!enabled() || to == null) return;
        boolean now = inSafeZone(to);
        Boolean prev = zoneState.put(p.getUniqueId(), now);
        if (!tell || prev == null || prev == now || !c().getBoolean("combat.safe-zone.auto.notify", true)) return;
        Msg.bar(p, msg(now ? "safe-enter" : "safe-leave"));
        Msg.sound(p, now ? "block.note_block.chime" : "block.note_block.bass", 0.7f, now ? 1.4f : 0.8f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onZoneMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) return;
        notifyZone(e.getPlayer(), to, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onZoneTeleport(PlayerTeleportEvent e) {
        notifyZone(e.getPlayer(), e.getTo(), true);
    }

    @EventHandler
    public void onZoneJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> notifyZone(p, p.getLocation(), false));
    }

    private static double flat(Location l, double x, double z) {
        double dx = l.getX() - x, dz = l.getZ() - z;
        return dx * dx + dz * dz;
    }

    /** Players in combat can't walk into a safe zone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) return;
        Player p = e.getPlayer();
        if (until.isEmpty() || !inCombat(p) || !enabled() || bypass(p)) return;
        if (!c().getBoolean("combat.safe-zone.block-entry", true)) return;
        if (!inSafeZone(to) || inSafeZone(from)) return;
        e.setCancelled(true);
        Vector back = from.toVector().subtract(to.toVector()).setY(0);
        if (back.lengthSquared() > 0) p.setVelocity(back.normalize().multiply(0.4).setY(0.1));
        warnEntry(p);
    }

    /** Pearls and chorus fruit can't be used to get into a safe zone while in combat. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        Player p = e.getPlayer();
        if (!enabled() || !inCombat(p) || bypass(p) || !c().getBoolean("combat.safe-zone.block-entry", true)) return;
        if (inSafeZone(e.getTo()) && !inSafeZone(e.getFrom())) {
            e.setCancelled(true);
            warnEntry(p);
        }
    }

    private void warnEntry(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last != null && now - last < 3000) return;
        lastWarn.put(p.getUniqueId(), now);
        Msg.send(p, msg("safe-entry").replace("<time>", String.valueOf(secondsLeft(p))));
        Msg.sound(p, "block.vault.insert_item_fail", 0.8f, 1.0f);
    }

    // ---------------------------------------------------------------- zone management (/smp safezone)

    public List<String> zoneNames() {
        List<String> out = new ArrayList<>();
        for (Zone z : zones) out.add(z.name());
        return out;
    }

    public String describe(String name) {
        for (Zone z : zones) {
            if (z.name().equalsIgnoreCase(name)) {
                return z.world() + " " + (int) z.x() + ", " + (int) z.z() + " r" + Theme.PLAIN_NUM(z.radius());
            }
        }
        return "";
    }

    public void addZone(String name, Location l, double radius) {
        String k = "combat.safe-zone.zones." + name.toLowerCase(Locale.ROOT);
        c().set(k + ".world", l.getWorld().getName());
        c().set(k + ".x", l.getX());
        c().set(k + ".z", l.getZ());
        c().set(k + ".radius", radius);
        plugin.saveConfig();
        load();
    }

    public boolean removeZone(String name) {
        String k = "combat.safe-zone.zones." + name.toLowerCase(Locale.ROOT);
        if (!c().contains(k)) return false;
        c().set(k, null);
        plugin.saveConfig();
        load();
        return true;
    }
}
