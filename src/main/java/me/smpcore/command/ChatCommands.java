package me.smpcore.command;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.module.ChatModule;
import me.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** /msg /tell /w /whisper /pm and /reply /r */
public final class ChatCommands implements TabExecutor {
    private final SMPCore plugin;
    private final ChatModule chat;

    public ChatCommands(SMPCore plugin, ChatModule chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "Players only.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("reply")) {
            if (args.length < 1) {
                Msg.send(p, chat.message("pm-usage").replace("/msg <player>", "/reply"));
                return true;
            }
            Player t = chat.replyTarget(p);
            if (t == null) {
                Msg.send(p, chat.message("pm-nobody"));
                return true;
            }
            chat.sendPrivate(p, t, String.join(" ", args));
            return true;
        }
        if (args.length < 2) {
            Msg.send(p, chat.message("pm-usage"));
            return true;
        }
        Player t = Bukkit.getPlayer(args[0]);
        if (t != null && plugin.staff.isVanished(t) && !plugin.staff.canSeeVanished(p)) t = null;
        if (t == null) {
            Msg.send(p, chat.message("pm-offline"));
            return true;
        }
        chat.sendPrivate(p, t, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && cmd.getName().equalsIgnoreCase("msg")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
