package me.smpcore.command;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.module.StaffModule;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /vanish [player]  /freeze <player>  /invsee <player>  /endersee <player> */
public final class StaffCommand implements TabExecutor {
    private final SMPCore plugin;

    public StaffCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    private Player find(CommandSender s, String name) {
        Player t = Bukkit.getPlayerExact(name);
        StaffModule st = plugin.staff;
        boolean hiddenFromSender = t != null && s instanceof Player p && st.isVanished(t) && !st.canSeeVanished(p);
        if (t == null || hiddenFromSender) {
            Msg.send(s, Theme.red("That player isn't online."));
            return null;
        }
        return t;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        StaffModule st = plugin.staff;
        switch (cmd.getName().toLowerCase()) {
            case "vanish" -> {
                if (!st.vanishEnabled()) { Msg.send(s, Theme.red("Vanish is disabled.")); return true; }
                Player t = a.length > 0 ? find(s, a[0]) : (s instanceof Player p ? p : null);
                if (t == null) {
                    if (a.length == 0) Msg.send(s, "Usage " + Theme.blue("/vanish [player]"));
                    return true;
                }
                boolean now = !st.isVanished(t);
                st.setVanish(t, now, true);
                if (t != s) Msg.send(s, Theme.yellow(t.getName()) + " is now " + (now ? Theme.green("vanished") : Theme.red("visible")) + ".");
            }
            case "freeze" -> {
                if (!st.freezeEnabled()) { Msg.send(s, Theme.red("Freeze is disabled.")); return true; }
                if (a.length < 1) { Msg.send(s, "Usage " + Theme.blue("/freeze <player>")); return true; }
                Player t = find(s, a[0]);
                if (t == null) return true;
                if (!st.isFrozen(t) && t.hasPermission("smpcore.freeze.bypass")) {
                    Msg.send(s, Theme.red("You can't freeze that player."));
                    return true;
                }
                boolean now = !st.isFrozen(t);
                st.setFreeze(t, now);
                Msg.send(s, Theme.yellow(t.getName()) + " is now " + (now ? Theme.red("frozen") : Theme.green("unfrozen")) + ".");
            }
            case "invsee", "endersee" -> {
                if (!st.invseeEnabled()) { Msg.send(s, Theme.red("Inventory see is disabled.")); return true; }
                if (!(s instanceof Player p)) { Msg.send(s, Theme.red("Players only.")); return true; }
                if (a.length < 1) { Msg.send(p, "Usage " + Theme.blue("/" + label + " <player>")); return true; }
                Player t = find(s, a[0]);
                if (t == null) return true;
                if (t.equals(p)) { Msg.send(p, Theme.red("That's you.")); return true; }
                p.openInventory(cmd.getName().equalsIgnoreCase("endersee") ? t.getEnderChest() : t.getInventory());
                boolean edit = p.hasPermission("smpcore.invsee.modify");
                Msg.send(p, (cmd.getName().equalsIgnoreCase("endersee") ? "Ender chest of " : "Inventory of ") + Theme.blue(t.getName())
                        + " " + Theme.c(Theme.DIM, edit ? "(you can edit)" : "(view only)"));
            }
            default -> { }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.staff.isVanished(p) && s instanceof Player v && !plugin.staff.canSeeVanished(v)) continue;
                out.add(p.getName());
            }
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
