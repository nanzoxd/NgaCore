package me.smpcore.module;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import me.smpcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Temp-bans players when they die, or (death spectator) keeps them in spectator mode for the same time. */
public final class DeathBanModule implements Listener {
    private final SMPCore plugin;
    private final File file;
    /** uuid -> end time in ms, -1 = until released by hand. */
    private final Map<UUID, Long> spectating = new ConcurrentHashMap<>();

    public DeathBanModule(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spectators.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private void load() {
        spectating.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                spectating.put(UUID.fromString(k), y.getLong(k));
            } catch (IllegalArgumentException ignored) {
                // skip bad key
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        spectating.forEach((id, t) -> y.set(id.toString(), t));
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save spectators.yml: " + ex.getMessage());
        }
    }

    private boolean active(long until) {
        return until < 0 || System.currentTimeMillis() < until;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        var c = plugin.getConfig();
        if (!c.getBoolean("death-ban.enabled")) return;
        Player p = e.getEntity();
        if (p.hasPermission("smpcore.bypass.deathban")) return;
        if (c.getBoolean("death-ban.pvp-only") && p.getKiller() == null) return;
        Long ms = TimeUtil.parse(c.getString("death-ban.duration", "1h"));
        if (ms == null) ms = 3_600_000L;
        if (c.getBoolean("death-ban.spectator")) {
            spectating.put(p.getUniqueId(), ms < 0 ? -1L : System.currentTimeMillis() + ms);
            save();
            return;
        }
        plugin.tempBans.ban(p.getUniqueId(), p.getName(), ms,
                c.getString("death-ban.message", "You died."), "Death Ban");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Long until = spectating.get(p.getUniqueId());
        if (until == null || !active(until)) return;
        Bukkit.getScheduler().runTask(plugin, () -> spectate(p, until));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Long until = spectating.get(p.getUniqueId());
        if (until == null) return;
        if (active(until)) Bukkit.getScheduler().runTask(plugin, () -> spectate(p, until));
        else release(p);
    }

    private void spectate(Player p, long until) {
        p.setGameMode(GameMode.SPECTATOR);
        String left = until < 0 ? "Permanent" : TimeUtil.format(until - System.currentTimeMillis());
        Msg.send(p, "You " + Theme.red("died") + ". You are in spectator mode for " + Theme.orange(left) + ".");
    }

    private void tick() {
        for (Map.Entry<UUID, Long> en : spectating.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null && !active(en.getValue())) release(p);
        }
    }

    private void release(Player p) {
        spectating.remove(p.getUniqueId());
        save();
        p.setGameMode(Bukkit.getDefaultGameMode());
        Location loc = p.getPotentialRespawnLocation();
        if (loc == null) loc = Bukkit.getWorlds().get(0).getSpawnLocation();
        p.teleport(loc);
        Msg.send(p, Theme.green("Your spectator time is over.") + " Welcome back.");
    }
}
