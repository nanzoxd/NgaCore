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
            default -> {
                Msg.send(s, Theme.blue("/smp") + " open the settings menu");
                Msg.send(s, Theme.blue("/smp reload") + "   " + Theme.blue("/smp itemban") + " <add|remove|list> [material]");
            }
        }
        return true;
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
        if (a.length == 1) out.addAll(List.of("menu", "reload", "itemban"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("itemban")) out.addAll(List.of("add", "remove", "list"));
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
