package me.smpcore.command;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.gui.WarpMenu;
import me.smpcore.module.SpawnModule;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /spawn /setspawn /warp /warps /setwarp /delwarp */
public final class SpawnCommand implements TabExecutor {
    private final SMPCore plugin;

    public SpawnCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    private String fmt(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        SpawnModule sp = plugin.spawn;
        if (!(s instanceof Player p)) {
            Msg.send(s, Theme.red("Players only."));
            return true;
        }
        boolean warpCmd = cmd.getName().toLowerCase().contains("warp");
        if (!(warpCmd ? sp.warpsEnabled() : sp.enabled()) && !p.hasPermission("smpcore.admin")) {
            Msg.send(p, Theme.red(warpCmd ? "Warps are disabled." : "Spawn is disabled."));
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "spawn" -> sp.teleport(p, sp.spawn(), "spawn");
            case "setspawn" -> {
                sp.setSpawn(p.getLocation());
                p.getWorld().setSpawnLocation(p.getLocation());
                Msg.send(p, "Spawn set at " + Theme.yellow(fmt(p.getLocation())) + ".");
            }
            case "warps" -> new WarpMenu(plugin, p).open();
            case "warp" -> {
                if (a.length < 1) {
                    new WarpMenu(plugin, p).open();
                    return true;
                }
                Location l = sp.warp(a[0]);
                if (l == null) {
                    Msg.send(p, Theme.red("No warp called ") + Theme.yellow(a[0]) + Theme.red(".") + " Try " + Theme.blue("/warps") + ".");
                    return true;
                }
                sp.teleport(p, l, "warp " + a[0].toLowerCase());
            }
            case "setwarp" -> {
                if (a.length < 1 || !SpawnModule.validName(a[0])) {
                    Msg.send(p, "Usage " + Theme.blue("/setwarp <name>") + " " + Theme.c(Theme.DIM, "(letters, numbers, _ and -)"));
                    return true;
                }
                sp.setWarp(a[0], p.getLocation());
                Msg.send(p, "Warp " + Theme.blue(a[0].toLowerCase()) + " set at " + Theme.yellow(fmt(p.getLocation())) + ".");
            }
            case "delwarp" -> {
                if (a.length < 1) {
                    Msg.send(p, "Usage " + Theme.blue("/delwarp <name>"));
                    return true;
                }
                if (sp.delWarp(a[0])) Msg.send(p, "Deleted warp " + Theme.blue(a[0].toLowerCase()) + ".");
                else Msg.send(p, Theme.red("No warp called ") + Theme.yellow(a[0]) + Theme.red("."));
            }
            default -> { }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String n = cmd.getName().toLowerCase();
        if (a.length == 1 && (n.equals("warp") || n.equals("delwarp"))) out.addAll(plugin.spawn.warps());
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
