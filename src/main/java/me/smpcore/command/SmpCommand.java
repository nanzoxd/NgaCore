package me.smpcore.command;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /smp, /smp reload, /smp itemban add|remove|list [material] */
public final class SmpCommand implements TabExecutor {
    private final SMPCore plugin;

    public SmpCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("menu") || a[0].equalsIgnoreCase("gui")) {
            if (s instanceof Player p) plugin.openMain(p);
            else Msg.send(s, "Run this in game, or use " + Theme.blue("/smp reload") + ".");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                Msg.send(s, Theme.green("Reloaded") + " config and bans.");
            }
            case "itemban" -> itemBan(s, a);
            case "grace" -> grace(s, a);
            case "whitelist" -> whitelist(s, a);
            default -> {
                Msg.send(s, Theme.blue("/smp") + " open the settings menu");
                Msg.send(s, Theme.blue("/smp reload") + "   " + Theme.blue("/smp itemban") + " <add|remove|list> [material]");
            }
        }
        return true;
    }

    private void grace(CommandSender s, String[] a) {
        if (a.length < 2 || (!a[1].equalsIgnoreCase("start") && !a[1].equalsIgnoreCase("stop"))) {
            Msg.send(s, "Usage " + Theme.blue("/smp grace <start|stop> [player|all]"));
            return;
        }
        boolean start = a[1].equalsIgnoreCase("start");
        String who = a.length > 2 ? a[2] : (s instanceof Player ? ((Player) s).getName() : "all");
        if (who.equalsIgnoreCase("all")) {
            if (start) plugin.grace.startAll(); else plugin.grace.stopAll();
            Msg.send(s, "Grace " + (start ? Theme.green("started") : Theme.red("stopped")) + " for everyone online.");
            return;
        }
        Player t = org.bukkit.Bukkit.getPlayerExact(who);
        if (t == null) {
            Msg.send(s, Theme.red("That player isn't online."));
            return;
        }
        if (start) plugin.grace.start(t); else plugin.grace.stop(t);
        Msg.send(s, "Grace " + (start ? Theme.green("started") : Theme.red("stopped")) + " for " + Theme.yellow(t.getName()) + ".");
    }

    /** /smp whitelist add (item in hand) | remove <number> | list */
    private void whitelist(CommandSender s, String[] a) {
        var wl = plugin.itemBan.whitelist;
        String sub = a.length > 1 ? a[1].toLowerCase() : "list";
        switch (sub) {
            case "add" -> {
                if (!(s instanceof Player p)) {
                    Msg.send(s, Theme.red("Hold the item in game."));
                    return;
                }
                var r = wl.add(p.getInventory().getItemInMainHand());
                if (r == null) Msg.send(s, Theme.red("That is a plain vanilla item.") + " Give it a custom name, model or tag first.");
                else Msg.send(s, "Whitelisted " + Theme.yellow(Theme.esc(r.label())) + ".");
            }
            case "remove" -> {
                int i = a.length > 2 ? parse(a[2]) : -1;
                if (i < 1 || i > wl.rules().size()) {
                    Msg.send(s, "Usage " + Theme.blue("/smp whitelist remove <number>") + " (see " + Theme.blue("/smp whitelist list") + ")");
                    return;
                }
                wl.remove(i - 1);
                Msg.send(s, "Removed entry " + Theme.yellow(i) + ".");
            }
            default -> {
                Msg.send(s, "Whitelisted custom weapons " + Theme.yellow(wl.rules().size()));
                int n = 1;
                for (var r : wl.rules()) Msg.send(s, Theme.yellow(n++) + " " + Theme.green(Theme.esc(r.label())) + " " + Theme.c(Theme.DIM, Theme.pretty(r.material().name())));
            }
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void itemBan(CommandSender s, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "Usage " + Theme.blue("/smp itemban <add|remove|list> [material]"));
            return;
        }
        if (a[1].equalsIgnoreCase("list")) {
            Msg.send(s, "Banned items " + Theme.yellow(plugin.itemBan.banned().size()));
            for (Material m : plugin.itemBan.banned()) Msg.send(s, Theme.red(m.name()));
            return;
        }
        boolean add = a[1].equalsIgnoreCase("add");
        if (!add && !a[1].equalsIgnoreCase("remove")) {
            Msg.send(s, "Usage " + Theme.blue("/smp itemban <add|remove|list> [material]"));
            return;
        }
        Material m = null;
        if (a.length > 2) m = Material.matchMaterial(a[2]);
        else if (s instanceof Player p) m = p.getInventory().getItemInMainHand().getType();
        if (m == null || m.isAir()) {
            Msg.send(s, Theme.red("Unknown or empty item.") + " Hold an item or give a material name.");
            return;
        }
        plugin.itemBan.set(m, add);
        Msg.send(s, (add ? Theme.red("Banned ") : Theme.green("Allowed ")) + Theme.pretty(m.name()) + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("menu", "reload", "itemban", "grace", "whitelist"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("grace")) out.addAll(List.of("start", "stop"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("whitelist")) out.addAll(List.of("add", "remove", "list"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("grace")) {
            out.add("all");
            org.bukkit.Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("itemban")) out.addAll(List.of("add", "remove", "list"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("itemban")) {
            if (a[1].equalsIgnoreCase("remove")) plugin.itemBan.banned().forEach(m -> out.add(m.name().toLowerCase()));
            else if (a[1].equalsIgnoreCase("add")) {
                for (Material m : Material.values()) {
                    if (m.isItem() && !m.name().startsWith("LEGACY_")) out.add(m.name().toLowerCase());
                }
            }
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.startsWith(last));
        if (out.size() > 60) return out.subList(0, 60);
        return out;
    }

}
