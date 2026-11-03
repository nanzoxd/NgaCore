package me.smpcore.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Theme;
import me.smpcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** /punish menu: ban, mute, kick or warn. Duration and message pickers are dialogs. */
public final class PunishMenu extends Gui {
    private final UUID target;
    private final String name;
    private final Supplier<Gui> parent;

    public PunishMenu(SMPCore plugin, Player viewer, UUID target, String name, Supplier<Gui> parent) {
        super(plugin, viewer, 5, "Punish: " + name);
        this.target = target;
        this.name = name;
        this.parent = parent;
    }

    @Override public String permission() { return "smpcore.tempban"; }

    private static String yn(boolean b) {
        return b ? Theme.red("YES") : Theme.green("NO");
    }

    @Override protected void build() {
        border();
        backButton(parent);
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        boolean banned = plugin.tempBans.get(target) != null;
        boolean muted = plugin.punish.muted(target);
        set(13, Items.of(Material.PLAYER_HEAD).head(op).title(name).lore(
                "UUID " + Theme.c(Theme.DIM, target.toString().substring(0, 8) + "..."),
                "Status " + (op.isOnline() ? Theme.green("ONLINE") : Theme.red("OFFLINE")),
                "Banned " + yn(banned), "Muted " + yn(muted),
                "Warnings " + Theme.yellow(plugin.punish.warnings(target)),
                "History " + Theme.yellow(plugin.tempBans.historyOf(target).size()) + " entries").build());

        set(29, Items.of(Material.BARRIER).name(Theme.red(banned ? "Unban" : "Ban"))
                .lore(banned ? "Lift this player's ban." : "Ban this player from the server.", "",
                        Theme.yellow("Click") + (banned ? " to unban" : " to choose a duration")).glow(banned).build(), e -> {
            if (banned) {
                plugin.tempBans.unban(target);
                refresh();
            } else {
                TempBanGuis.Draft d = new TempBanGuis.Draft();
                d.target = target;
                d.name = name;
                new TempBanGuis.DurationMenu(plugin, viewer, d, () -> this).open();
            }
        });
        set(30, Items.of(Material.NOTE_BLOCK).name(Theme.orange(muted ? "Unmute" : "Mute"))
                .lore(muted ? "Let this player chat again." : "Stop this player from chatting.", "",
                        Theme.yellow("Click") + (muted ? " to unmute" : " to choose a duration")).glow(muted).build(), e -> {
            if (muted) {
                plugin.punish.unmute(target);
                refresh();
            } else {
                durations(ms -> plugin.punish.mute(target, ms));
            }
        });
        set(32, Items.of(Material.IRON_BOOTS).name(Theme.yellow("Kick"))
                .lore("Remove this player from the server.", "", Theme.yellow("Click") + " to choose a message").build(), e -> {
            Player online = Bukkit.getPlayer(target);
            if (online == null) return;
            reasons("Kick: " + name, r -> plugin.punish.kick(online, r));
        });
        set(33, Items.of(Material.OAK_SIGN).name(Theme.blue("Warn"))
                .lore("Warnings escalate to a temp-ban.", "", Theme.yellow("Click") + " to choose a message").build(), e ->
                reasons("Warn: " + name, r -> {
                    plugin.punish.warn(target, name, r, viewer.getName());
                    new PunishMenu(plugin, viewer, target, name, parent).open();
                }));
    }

    private void durations(Consumer<Long> done) {
        List<Dialogs.Opt> opts = new ArrayList<>();
        for (String d : cfg().getStringList("tempban.durations")) {
            Long ms = TimeUtil.parse(d);
            if (ms == null) continue;
            opts.add(new Dialogs.Opt(Theme.orange(ms < 0 ? "Permanent" : TimeUtil.format(ms)), null, () -> {
                done.accept(ms);
                new PunishMenu(plugin, viewer, target, name, parent).open();
            }));
        }
        if (opts.isEmpty()) return;
        Dialogs.list(viewer, "Mute: " + name, List.of("How long?"), opts, 3, () -> new PunishMenu(plugin, viewer, target, name, parent).open());
    }

    private void reasons(String title, Consumer<String> done) {
        List<Dialogs.Opt> opts = new ArrayList<>();
        ConfigurationSection presets = cfg().getConfigurationSection("tempban.presets");
        if (presets != null) {
            for (String k : presets.getKeys(false)) {
                String msg = presets.getString(k, k);
                opts.add(new Dialogs.Opt(Theme.orange(k), Theme.c(Theme.DIM, Theme.esc(msg)), () -> done.accept(msg)));
            }
        }
        opts.add(new Dialogs.Opt("Custom message", "Type your own message in chat", () ->
                plugin.prompts.ask(viewer, "Type the message.", done)));
        Dialogs.list(viewer, title, List.of("Pick a message."), opts, 2, () -> new PunishMenu(plugin, viewer, target, name, parent).open());
    }
}
