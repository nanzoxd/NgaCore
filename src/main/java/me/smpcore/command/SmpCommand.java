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
            case "spawn" -> spawnCfg(s, a);
            case "rtp" -> rtp(s, a);
            case "combat" -> combat(s, a);
            case "safezone" -> safezone(s, a);
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

    /** /smp rtp [player]: random teleport (test). */
    private void rtp(CommandSender s, String[] a) {
        Player t = a.length > 1 ? org.bukkit.Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
        if (t == null) {
            Msg.send(s, "Usage " + Theme.blue("/smp rtp [player]"));
            return;
        }
        plugin.randomTp.teleport(t, true);
        if (t != s) Msg.send(s, "Sending " + Theme.yellow(t.getName()) + " to a random spot.");
    }

    /** /smp combat [seconds <n> | clear <player|all>] */
    private void combat(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "";
        if (sub.equals("seconds") && a.length > 2 && parse(a[2]) >= 1) {
            plugin.getConfig().set("combat.seconds", parse(a[2]));
            plugin.saveConfig();
            Msg.send(s, "Combat time set to " + Theme.yellow(parse(a[2]) + "s") + ".");
        } else if (sub.equals("rejoin") && a.length > 2 && parse(a[2]) >= 1) {
            plugin.getConfig().set("combat.rejoin.seconds", parse(a[2]));
            plugin.saveConfig();
            Msg.send(s, "Rejoin time set to " + Theme.yellow(parse(a[2]) + "s") + ". " + Theme.c(Theme.DIM, plugin.combat.rejoinEnabled() ? "" : "(the feature is off, switch it on in /smp)"));
        } else if (sub.equals("clear") && a.length > 2) {
            if (a[2].equalsIgnoreCase("all")) {
                plugin.combat.clearAll();
                Msg.send(s, "Combat cleared for " + Theme.yellow("everyone") + ".");
                return;
            }
            Player t = org.bukkit.Bukkit.getPlayerExact(a[2]);
            if (t == null) {
                Msg.send(s, Theme.red("That player isn't online."));
                return;
            }
            plugin.combat.clear(t, true);
            Msg.send(s, "Combat cleared for " + Theme.yellow(t.getName()) + ".");
        } else {
            Msg.send(s, "Combat time " + Theme.yellow(plugin.combat.millis() / 1000 + "s") + ". "
                    + Theme.blue("/smp combat seconds <n>") + "  " + Theme.blue("/smp combat rejoin <n>") + "  " + Theme.blue("/smp combat clear <player|all>"));
        }
    }

    /** /smp safezone add <name> <radius> | remove <name> | list | spawn <radius> */
    private void safezone(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "list";
        switch (sub) {
            case "add" -> {
                if (!(s instanceof Player p) || a.length < 4 || parse(a[3]) < 1 || !me.smpcore.module.SpawnModule.validName(a[2])) {
                    Msg.send(s, "Usage " + Theme.blue("/smp safezone add <name> <radius>") + " (stand in the middle, in game)");
                    return;
                }
                plugin.combat.addZone(a[2], p.getLocation(), parse(a[3]));
                Msg.send(s, "Safe zone " + Theme.yellow(a[2]) + " added, radius " + Theme.yellow(parse(a[3])) + ".");
            }
            case "remove" -> {
                if (a.length < 3 || !plugin.combat.removeZone(a[2])) {
                    Msg.send(s, "Usage " + Theme.blue("/smp safezone remove <name>") + " (see " + Theme.blue("/smp safezone list") + ")");
                    return;
                }
                Msg.send(s, "Safe zone " + Theme.yellow(a[2]) + " removed.");
            }
            case "spawn" -> {
                if (a.length < 3 || parse(a[2]) < 0) {
                    Msg.send(s, "Spawn safe radius " + Theme.yellow(Theme.PLAIN_NUM(plugin.getConfig().getDouble("combat.safe-zone.spawn-radius", 25)))
                            + ". Change it with " + Theme.blue("/smp safezone spawn <radius>") + " (0 = off).");
                    return;
                }
                plugin.getConfig().set("combat.safe-zone.spawn-radius", parse(a[2]));
                plugin.saveConfig();
                Msg.send(s, "Spawn safe radius set to " + Theme.yellow(parse(a[2])) + ".");
            }
            default -> {
                var names = plugin.combat.zoneNames();
                Msg.send(s, "Safe zones " + Theme.yellow(names.size()) + " " + Theme.c(Theme.DIM, "(plus the spawn area and the automatic ones)"));
                Msg.send(s, Theme.c(Theme.DIM, "WorldGuard: ") + Theme.yellow(plugin.combat.wgStatus()));
                for (String n : names) Msg.send(s, Theme.green(Theme.esc(n)) + " " + Theme.c(Theme.DIM, Theme.esc(plugin.combat.describe(n))));
            }
        }
    }

    /** /smp spawn delay <seconds> */
    private void spawnCfg(CommandSender s, String[] a) {
        if (a.length >= 3 && a[1].equalsIgnoreCase("delay") && parse(a[2]) >= 0) {
            plugin.getConfig().set("spawn.delay-seconds", parse(a[2]));
            plugin.saveConfig();
            Msg.send(s, "Teleport wait set to " + Theme.yellow(parse(a[2]) + "s") + ".");
        } else {
            Msg.send(s, "Wait time " + Theme.yellow(plugin.spawn.delay() + "s") + ". Change it with " + Theme.blue("/smp spawn delay <seconds>") + ".");
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
            Msg.send(s, "Banned items " + Theme.yellow(plugin.itemBan.entries().size()));
            for (var en : plugin.itemBan.entries()) {
                Msg.send(s, Theme.red(en.material().name()) + (en.item() == null ? "" : " " + Theme.c(Theme.DIM, "(exact item: " + Theme.esc(me.smpcore.module.ItemBanModule.sig(en.item())) + ")")));
            }
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
        if (a.length == 1) out.addAll(List.of("menu", "reload", "itemban", "grace", "whitelist", "spawn", "rtp", "combat", "safezone"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("grace")) out.addAll(List.of("start", "stop"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("spawn")) out.add("delay");
        else if (a.length == 2 && a[0].equalsIgnoreCase("combat")) out.addAll(List.of("seconds", "rejoin", "clear"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("combat") && a[1].equalsIgnoreCase("clear")) {
            out.add("all");
            org.bukkit.Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("safezone")) out.addAll(List.of("add", "remove", "list", "spawn"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("safezone") && a[1].equalsIgnoreCase("remove")) out.addAll(plugin.combat.zoneNames());
        else if (a.length == 2 && a[0].equalsIgnoreCase("rtp")) org.bukkit.Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
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
