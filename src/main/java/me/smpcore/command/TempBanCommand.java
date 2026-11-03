package me.smpcore.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.gui.TempBanGuis;
import me.smpcore.module.TempBanManager;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import me.smpcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** /tempban ban|unban|list|history|gui */
public final class TempBanCommand implements TabExecutor {
    private final SMPCore plugin;

    public TempBanCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    private OfflinePlayer target(String name) {
        Player on = Bukkit.getPlayerExact(name);
        return on != null ? on : Bukkit.getOfflinePlayerIfCached(name);
    }

    private void usage(CommandSender s) {
        Msg.send(s, Theme.blue("/tempban ban") + " <player> <time> [message]  " + Theme.c(Theme.DIM, "(1h, 3d12h, perm)"));
        Msg.send(s, Theme.blue("/tempban unban") + " <player>   " + Theme.blue("/tempban list") + "   "
                + Theme.blue("/tempban history") + " <player>   " + Theme.blue("/tempban gui"));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("gui")) {
            if (s instanceof Player p) new TempBanGuis.PlayerPicker(plugin, p, null).open();
            else usage(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "ban" -> {
                if (a.length < 3) { usage(s); return true; }
                OfflinePlayer t = target(a[1]);
                if (t == null) {
                    Msg.send(s, Theme.red("Unknown player") + " (they must have joined before).");
                    return true;
                }
                Long ms = TimeUtil.parse(a[2]);
                if (ms == null) {
                    Msg.send(s, Theme.red("Invalid time.") + " Try " + Theme.yellow("30m") + ", " + Theme.yellow("2d") + " or " + Theme.yellow("perm") + ".");
                    return true;
                }
                String reason = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length))
                        : plugin.getConfig().getString("tempban.default-reason", "No reason given");
                ConfigurationSection presets = plugin.getConfig().getConfigurationSection("tempban.presets");
                if (presets != null) {
                    for (String k : presets.getKeys(false)) {
                        if (k.equalsIgnoreCase(reason)) reason = presets.getString(k, reason);
                    }
                }
                String name = t.getName() == null ? a[1] : t.getName();
                plugin.tempBans.ban(t.getUniqueId(), name, ms, reason, s.getName());
                Msg.send(s, "Banned " + Theme.blue(name) + " for " + Theme.orange(TimeUtil.format(ms)) + ".");
            }
            case "unban" -> {
                if (a.length < 2) { usage(s); return true; }
                TempBanManager.Entry e = plugin.tempBans.find(a[1]);
                if (e == null) {
                    Msg.send(s, Theme.red("No active ban") + " for that player.");
                    return true;
                }
                plugin.tempBans.unban(e.uuid());
                Msg.send(s, "Unbanned " + Theme.blue(e.name()) + ".");
            }
            case "list" -> {
                List<TempBanManager.Entry> l = plugin.tempBans.activeBans();
                Msg.send(s, "Active bans " + Theme.yellow(l.size()));
                for (TempBanManager.Entry e : l) {
                    long left = e.permanent() ? -1 : Math.max(0, e.expires() - System.currentTimeMillis());
                    Msg.send(s, Theme.red(e.name()) + " " + Theme.orange(TimeUtil.format(left)) + " " + Theme.c(Theme.DIM, Theme.esc(e.reason())));
                }
            }
            case "history" -> {
                if (a.length < 2) { usage(s); return true; }
                OfflinePlayer t = target(a[1]);
                if (t == null) {
                    Msg.send(s, Theme.red("Unknown player") + ".");
                    return true;
                }
                List<TempBanManager.Entry> l = plugin.tempBans.historyOf(t.getUniqueId());
                Msg.send(s, "History for " + Theme.blue(a[1]) + " " + Theme.yellow(l.size()));
                for (TempBanManager.Entry e : l) {
                    Msg.send(s, Theme.orange(e.permanent() ? "Permanent" : TimeUtil.format(e.expires() - e.start()))
                            + " " + Theme.c(Theme.DIM, Theme.esc(e.reason())) + " by " + Theme.blue(Theme.esc(e.staff())));
                }
            }
            default -> usage(s);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("ban", "unban", "list", "history", "gui"));
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("ban") || a[0].equalsIgnoreCase("history"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("unban")) {
            for (TempBanManager.Entry e : plugin.tempBans.activeBans()) out.add(e.name());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("ban")) {
            out.addAll(plugin.getConfig().getStringList("tempban.durations"));
        } else if (a.length == 4 && a[0].equalsIgnoreCase("ban")) {
            ConfigurationSection p = plugin.getConfig().getConfigurationSection("tempban.presets");
            if (p != null) out.addAll(p.getKeys(false));
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
