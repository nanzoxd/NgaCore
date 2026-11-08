package me.smpcore.command;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.gui.PunishMenu;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /punish <player> */
public final class PunishCommand implements TabExecutor {
    private final SMPCore plugin;

    public PunishCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "Run this in game.");
            return true;
        }
        if (a.length < 1) {
            Msg.send(p, "Usage " + Theme.blue("/punish <player>"));
            return true;
        }
        Player on = Bukkit.getPlayerExact(a[0]);
        OfflinePlayer t = on != null ? on : Bukkit.getOfflinePlayerIfCached(a[0]);
        if (t == null) {
            Msg.send(p, Theme.red("Unknown player") + " (they must have joined before).");
            return true;
        }
        new PunishMenu(plugin, p, t.getUniqueId(), t.getName() == null ? a[0] : t.getName(), null).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a[0].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
