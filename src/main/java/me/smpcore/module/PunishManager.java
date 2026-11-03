package me.smpcore.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import me.smpcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Mutes, warnings (with auto-escalation to a temp-ban) and kicks. Bans live in TempBanManager. */
public final class PunishManager implements Listener {
    public record Warn(String reason, String staff, long time) {}

    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, Long> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, List<Warn>> warns = new ConcurrentHashMap<>();

    public PunishManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "punish.yml");
        load();
    }

    public synchronized void load() {
        mutes.clear();
        warns.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection m = y.getConfigurationSection("mutes");
        if (m != null) {
            for (String k : m.getKeys(false)) {
                try {
                    mutes.put(UUID.fromString(k), m.getLong(k));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
        ConfigurationSection w = y.getConfigurationSection("warns");
        if (w != null) {
            for (String k : w.getKeys(false)) {
                ConfigurationSection per = w.getConfigurationSection(k);
                if (per == null) continue;
                List<Warn> l = new ArrayList<>();
                for (String i : per.getKeys(false)) {
                    ConfigurationSection s = per.getConfigurationSection(i);
                    if (s != null) l.add(new Warn(s.getString("reason", ""), s.getString("staff", "Console"), s.getLong("time")));
                }
                try {
                    warns.put(UUID.fromString(k), l);
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        mutes.forEach((id, t) -> y.set("mutes." + id, t));
        warns.forEach((id, l) -> {
            for (int i = 0; i < l.size(); i++) {
                String b = "warns." + id + "." + i + ".";
                y.set(b + "reason", l.get(i).reason());
                y.set(b + "staff", l.get(i).staff());
                y.set(b + "time", l.get(i).time());
            }
        });
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save punish.yml: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- mute

    public boolean muted(UUID id) {
        Long t = mutes.get(id);
        if (t == null) return false;
        if (t >= 0 && System.currentTimeMillis() >= t) {
            mutes.remove(id);
            save();
            return false;
        }
        return true;
    }

    public void mute(UUID id, long ms) {
        mutes.put(id, ms < 0 ? -1L : System.currentTimeMillis() + ms);
        save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) Msg.send(p, Theme.red("You were muted") + " for " + Theme.orange(TimeUtil.format(ms)) + ".");
    }

    public void unmute(UUID id) {
        if (mutes.remove(id) != null) save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) Msg.send(p, Theme.green("You were unmuted."));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!muted(id)) return;
        e.setCancelled(true);
        long t = mutes.getOrDefault(id, -1L);
        Msg.send(e.getPlayer(), Theme.red("You are muted.") + " Remaining "
                + Theme.orange(t < 0 ? "Permanent" : TimeUtil.format(t - System.currentTimeMillis())));
    }

    // ---------------------------------------------------------------- warn / kick

    public int warnings(UUID id) {
        return warns.getOrDefault(id, List.of()).size();
    }

    public int warn(UUID id, String name, String reason, String staff) {
        List<Warn> l = warns.computeIfAbsent(id, k -> new ArrayList<>());
        l.add(new Warn(reason, staff, System.currentTimeMillis()));
        save();
        int n = l.size();
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            Msg.send(p, Theme.orange("Warning " + n) + " " + Theme.c(Theme.DIM, Theme.esc(reason)));
        }
        ConfigurationSection esc = plugin.getConfig().getConfigurationSection("punish.escalation");
        if (esc != null && esc.contains(String.valueOf(n))) {
            Long ms = TimeUtil.parse(esc.getString(String.valueOf(n), ""));
            if (ms != null) plugin.tempBans.ban(id, name, ms, "Reached " + n + " warnings", "Auto");
        }
        return n;
    }

    public void kick(Player p, String reason) {
        p.kick(Theme.parse(Theme.GRADIENT + "You were kicked</gradient>\n\n<#abc4d1>Reason <#E8847C>" + Theme.esc(reason)));
    }
}
