package me.smpcore.module;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Grace period: Resistance IV and Saturation for 30 minutes (configurable) plus a sidebar clock
 * "🕛 Grace / 30:00". Time only runs while the player is online and is saved on the player.
 */
public final class GraceModule implements Listener {
    private final SMPCore plugin;
    private final NamespacedKey leftKey;   // remaining milliseconds
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public GraceModule(SMPCore plugin) {
        this.plugin = plugin;
        this.leftKey = new NamespacedKey(plugin, "grace_left");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    public boolean enabled() {
        return c().getBoolean("grace.enabled");
    }

    private long duration() {
        return Math.max(1, (long) (c().getDouble("grace.minutes", 30) * 60_000));
    }

    public long left(Player p) {
        return p.getPersistentDataContainer().getOrDefault(leftKey, PersistentDataType.LONG, 0L);
    }

    private void setLeft(Player p, long ms) {
        p.getPersistentDataContainer().set(leftKey, PersistentDataType.LONG, Math.max(0, ms));
    }

    /** Starts (or restarts) grace for one player. */
    public void start(Player p) {
        setLeft(p, duration());
        Msg.send(p, "Grace started " + Theme.yellow(fmt(duration())) + ". " + Theme.blue("Resistance " + roman(level()))
                + " and " + Theme.blue("Saturation") + " are on you.");
        apply(p, duration());
        showBoard(p, duration());
    }

    public void stop(Player p) {
        setLeft(p, 0);
        end(p, false);
    }

    public void startAll() {
        for (Player p : Bukkit.getOnlinePlayers()) start(p);
    }

    public void stopAll() {
        for (Player p : Bukkit.getOnlinePlayers()) stop(p);
    }

    private int level() {
        return Math.max(1, Math.min(255, c().getInt("grace.resistance.level", 4)));
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    /** 1800000 -> "30:00", 3723000 -> "1:02:03" */
    public static String fmt(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    private void apply(Player p, long ms) {
        int ticks = (int) Math.min(Integer.MAX_VALUE / 2, ms / 50 + 40);
        if (c().getBoolean("grace.resistance.enabled", true))
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, level() - 1, false, false, true));
        if (c().getBoolean("grace.saturation.enabled", true))
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, ticks, 0, false, false, true));
    }

    private void showBoard(Player p, long ms) {
        if (!c().getBoolean("grace.scoreboard.enabled", true)) return;
        Scoreboard b = boards.get(p.getUniqueId());
        if (b == null) {
            b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = b.registerNewObjective("grace", Criteria.DUMMY,
                    Theme.title(c().getString("grace.scoreboard.icon", "🕛") + " " + c().getString("grace.scoreboard.title", "ɢʀᴀᴄᴇ")));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
            boards.put(p.getUniqueId(), b);
            p.setScoreboard(b);
        }
        Objective o = b.getObjective("grace");
        if (o == null) return;
        var score = o.getScore("grace_time");
        score.setScore(1);
        score.numberFormat(NumberFormat.blank());
        score.customName(Theme.parse("<" + color(ms) + ">" + " ".repeat(Math.max(0, c().getInt("grace.scoreboard.pad", 3))) + fmt(ms)));
    }

    /** Clock colour by time left: green, then yellow, orange and red for the last stretch. */
    public String color(long ms) {
        double f = (double) ms / duration();
        if (f > 0.5) return Theme.GREEN;
        if (f > 0.25) return Theme.YELLOW;
        if (f > 0.1) return Theme.ORANGE;
        return Theme.RED;
    }

    private void end(Player p, boolean message) {
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.SATURATION);
        if (boards.remove(p.getUniqueId()) != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (message) Msg.send(p, Theme.green("Grace is over.") + " Good luck out there.");
    }

    private void tick() {
        Collection<? extends Player> all = Bukkit.getOnlinePlayers();
        for (Player p : all) {
            long left = left(p);
            if (left <= 0) continue;
            if (!enabled()) {
                if (boards.containsKey(p.getUniqueId())) end(p, false);
                continue;
            }
            left -= 1000;
            setLeft(p, left);
            if (left <= 0) {
                end(p, true);
                continue;
            }
            apply(p, left);
            showBoard(p, left);
        }
    }

    /** Every join gives a fresh grace period (grace.on-join). With it off, an unfinished grace just resumes. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!enabled()) return;
        if (c().getBoolean("grace.on-join", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> start(p));
            return;
        }
        long left = left(p);
        if (left > 0) Bukkit.getScheduler().runTask(plugin, () -> {
            apply(p, left);
            showBoard(p, left);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!enabled() || !c().getBoolean("grace.on-respawn", false)) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> start(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }
}
