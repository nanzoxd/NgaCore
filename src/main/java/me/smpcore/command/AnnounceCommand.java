package me.smpcore.command;

import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/** /announce <message> (title + chat + sounds) and /broadcast <message> (chat only). Console works too. */
public final class AnnounceCommand implements TabExecutor {
    private static final int MAX_LENGTH = 256;
    private final SMPCore plugin;

    public AnnounceCommand(SMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        boolean announce = cmd.getName().equalsIgnoreCase("announce");
        if (!plugin.announce.enabled()) {
            Msg.send(s, Theme.red("Announcements are turned off.") + " Switch them on in " + Theme.blue("/smp") + ".");
            return true;
        }
        if (a.length == 0) {
            Msg.send(s, "Usage " + Theme.blue(announce ? "/announce <message>" : "/broadcast <message>"));
            return true;
        }
        String message = String.join(" ", a).strip();
        if (message.length() > MAX_LENGTH) {
            Msg.send(s, Theme.red("Too long.") + " The message can be at most " + Theme.yellow(MAX_LENGTH) + " characters.");
            return true;
        }
        if (announce) plugin.announce.announce(message);
        else plugin.announce.broadcast(message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        return List.of();
    }
}
