package me.smpcore.module;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.smpcore.SMPCore;
import me.smpcore.util.Theme;
import me.smpcore.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/** Stores temporary bans (bans.yml), kicks with a custom screen and blocks login while banned. */
public final class TempBanManager implements Listener {
    public record Entry(UUID uuid, String name, String reason, String staff, long start, long expires) {
        public boolean permanent() {
            return expires < 0;
        }

        public boolean expired() {
            return expires >= 0 && System.currentTimeMillis() >= expires;
        }
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final SMPCore plugin;
    private final File file;
    private final Map<UUID, Entry> active = new ConcurrentHashMap<>();
    private final List<Entry> history = Collections.synchronizedList(new ArrayList<>());

    public TempBanManager(SMPCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bans.yml");
        load();
    }

    public synchronized void load() {
        active.clear();
        history.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection a = y.getConfigurationSection("active");
        if (a != null) {
            for (String k : a.getKeys(false)) {
                Entry e = read(k, a.getConfigurationSection(k));
                if (e != null) active.put(e.uuid(), e);
            }
        }
        ConfigurationSection h = y.getConfigurationSection("history");
        if (h != null) {
            for (String k : h.getKeys(false)) {
                ConfigurationSection per = h.getConfigurationSection(k);
                if (per == null) continue;
                for (String idx : per.getKeys(false)) {
                    Entry e = read(k, per.getConfigurationSection(idx));
                    if (e != null) history.add(e);
                }
            }
        }
    }

    private Entry read(String uuid, ConfigurationSection s) {
        if (s == null) return null;
        try {
            return new Entry(UUID.fromString(uuid), s.getString("name", "?"), s.getString("reason", ""),
                    s.getString("staff", "Console"), s.getLong("start"), s.getLong("expires", -1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void write(ConfigurationSection s, Entry e) {
        s.set("name", e.name());
        s.set("reason", e.reason());
        s.set("staff", e.staff());
        s.set("start", e.start());
        s.set("expires", e.expires());
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Entry e : active.values()) write(y.createSection("active." + e.uuid()), e);
        int i = 0;
        synchronized (history) {
            for (Entry e : history) write(y.createSection("history." + e.uuid() + "." + (i++)), e);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save bans.yml: " + ex.getMessage());
        }
    }

    /** The active ban, or null. Expired bans are removed. */
    public Entry get(UUID id) {
        Entry e = active.get(id);
        if (e == null) return null;
        if (e.expired()) {
            active.remove(id);
            save();
            return null;
        }
        return e;
    }

    public Entry find(String name) {
        for (Entry e : activeBans()) if (e.name().equalsIgnoreCase(name)) return e;
        return null;
    }

    public List<Entry> activeBans() {
        List<Entry> out = new ArrayList<>();
        for (UUID id : new ArrayList<>(active.keySet())) {
            Entry e = get(id);
            if (e != null) out.add(e);
        }
        out.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<Entry> historyOf(UUID id) {
        List<Entry> out = new ArrayList<>();
        synchronized (history) {
            for (Entry e : history) if (e.uuid().equals(id)) out.add(e);
        }
        out.sort(Comparator.comparingLong(Entry::start).reversed());
        return out;
    }

    public Collection<Entry> all() {
        return active.values();
    }

    /** @param durationMs milliseconds, or -1 for permanent. Call on the main thread. */
    public Entry ban(UUID id, String name, long durationMs, String reason, String staff) {
        long now = System.currentTimeMillis();
        Entry e = new Entry(id, name, reason, staff, now, durationMs < 0 ? -1 : now + durationMs);
        active.put(id, e);
        history.add(e);
        save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) Bukkit.getScheduler().runTask(plugin, () -> p.kick(screen(e)));
        if (plugin.getConfig().getBoolean("tempban.broadcast")) {
            Bukkit.broadcast(Theme.MM.deserialize(plugin.getConfig().getString("tempban.broadcast-message", ""),
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("duration", e.permanent() ? "Permanent" : TimeUtil.format(durationMs)),
                    Placeholder.unparsed("reason", reason),
                    Placeholder.unparsed("staff", staff)));
        }
        return e;
    }

    public boolean unban(UUID id) {
        boolean had = active.remove(id) != null;
        if (had) save();
        return had;
    }

    public Component screen(Entry e) {
        long now = System.currentTimeMillis();
        TagResolver r = TagResolver.resolver(
                Placeholder.unparsed("reason", e.reason()),
                Placeholder.unparsed("staff", e.staff()),
                Placeholder.unparsed("remaining", e.permanent() ? "Permanent" : TimeUtil.format(Math.max(0, e.expires() - now))),
                Placeholder.unparsed("duration", e.permanent() ? "Permanent" : TimeUtil.format(e.expires() - e.start())),
                Placeholder.unparsed("expires", e.permanent() ? "Never" : FMT.format(Instant.ofEpochMilli(e.expires())) + " UTC"));
        List<Component> lines = new ArrayList<>();
        for (String l : plugin.getConfig().getStringList("tempban.screen")) lines.add(Theme.MM.deserialize(l, r));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Entry b = get(e.getUniqueId());
        if (b != null) e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen(b));
    }
}
