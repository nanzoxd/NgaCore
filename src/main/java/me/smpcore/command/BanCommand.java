package me.smpcore.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.module.TempBanManager;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import me.smpcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Replaces the vanilla /ban and /pardon (plugin commands win over vanilla ones of the same name).
 * /ban <player> <duration> [reason...]  e.g. /ban MrNanzo 7d Griefing.  /ban <player> [reason] is permanent.
 */
public final class BanCommand implements TabExecutor {
    private final SMPCore plugin;
    private final TempBanCommand bans;

    public BanCommand(SMPCore plugin, TempBanCommand bans) {
        this.plugin = plugin;
        this.bans = bans;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String l = cmd.getName().toLowerCase();
        if (l.equals("unban") || l.equals("pardon")) {
            if (a.length < 1) {
                Msg.send(s, Theme.blue("/" + label) + " <player>");
                return true;
            }
            bans.unban(s, a[0]);
            return true;
        }
        if (a.length < 1) {
            Msg.send(s, Theme.blue("/ban") + " <player> <duration> [reason]   " + Theme.c(Theme.DIM, "(1h, 3d12h, perm)"));
            return true;
        }
        if (a.length == 1) {
            bans.ban(s, a[0], "perm", null);
            return true;
        }
        // /ban <player> <duration> [reason]  or, without a duration, /ban <player> <reason...> (permanent)
        if (TimeUtil.parse(a[1]) != null) {
            bans.ban(s, a[0], a[1], a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : null);
        } else {
            bans.ban(s, a[0], "perm", String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        boolean un = cmd.getName().equalsIgnoreCase("unban") || cmd.getName().equalsIgnoreCase("pardon");
        if (a.length == 1) {
            if (un) for (TempBanManager.Entry e : plugin.tempBans.activeBans()) out.add(e.name());
            else for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (!un && a.length == 2) {
            out.addAll(plugin.getConfig().getStringList("tempban.durations"));
        } else if (!un && a.length == 3) {
            ConfigurationSection p = plugin.getConfig().getConfigurationSection("tempban.presets");
            if (p != null) out.addAll(p.getKeys(false));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
