package me.smpcore.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** "Type your answer in chat" prompts used by the GUIs (custom ban message, duration, names). */
public final class PromptManager implements Listener {
    private final SMPCore plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public PromptManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String instruction, Consumer<String> callback) {
        pending.put(p.getUniqueId(), callback);
        p.closeInventory();
        Msg.send(p, instruction + " " + Theme.c(Theme.DIM, "(type ") + Theme.red("cancel") + Theme.c(Theme.DIM, " to abort)"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = pending.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                Msg.send(p, "Cancelled.");
                return;
            }
            cb.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
