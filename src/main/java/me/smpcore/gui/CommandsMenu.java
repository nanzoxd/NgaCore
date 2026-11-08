package me.smpcore.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Lore;
import me.smpcore.util.Theme;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Opened with the command block in the top-right corner of the settings menu: every command of the plugin
 * (/vanish, /spawn, /warp ...) as one item with its usage, aliases, permission and a switch for the feature behind it
 * (left click = enable, right click = disable).
 * Commands that are in plugin.yml but not in the list below are added automatically with a paper icon.
 */
public final class CommandsMenu extends PagedGui<CommandsMenu.Cmd> {

    /** One command. {@code path} is the config switch of the feature behind it (or null), {@code aliases} may be null. */
    public record Cmd(Material icon, String usage, String desc, String perm, String path, String aliases) {
        /** "/smp grace <start|stop>" -> "/smp grace" */
        public String name() {
            StringBuilder b = new StringBuilder();
            for (String t : usage.split(" ")) {
                if (t.startsWith("<") || t.startsWith("[")) break;
                if (b.length() > 0) b.append(' ');
                b.append(t);
            }
            return b.toString();
        }
    }

    private static final List<Cmd> CURATED = List.of(
            // spawn, warps, messages
            new Cmd(Material.NETHER_STAR, "/spawn", "Teleport to spawn after a short wait.", "smpcore.spawn", "spawn.enabled", null),
            new Cmd(Material.RED_BED, "/setspawn", "Set the spawn where you stand.", "smpcore.admin", "spawn.enabled", null),
            new Cmd(Material.ENDER_PEARL, "/warp <name>", "Teleport to a warp.", "smpcore.warp", "warps.enabled", null),
            new Cmd(Material.FILLED_MAP, "/warps", "Open the warp menu.", "smpcore.warp", "warps.enabled", null),
            new Cmd(Material.NAME_TAG, "/setwarp <name>", "Create a warp where you stand.", "smpcore.admin", "warps.enabled", null),
            new Cmd(Material.BARRIER, "/delwarp <name>", "Delete a warp.", "smpcore.admin", "warps.enabled", null),
            new Cmd(Material.WRITABLE_BOOK, "/msg <player> <message>", "Send a private message.", null, "chat.private-messages.enabled", "/tell, /w, /whisper, /pm"),
            new Cmd(Material.BOOK, "/reply <message>", "Reply to your last private message.", null, "chat.private-messages.enabled", "/r"),
            new Cmd(Material.BELL, "/announce <message>", "Animated title, the message under it, a chat line and sounds for everyone.", "smpcore.announce", "announce.enabled", "/ann"),
            new Cmd(Material.NOTE_BLOCK, "/broadcast <message>", "Chat-only message for everyone: no title.", "smpcore.broadcast", "announce.enabled", "/bc, /bcast"),
            new Cmd(Material.IRON_SWORD, "/pvp", "Toggle your own PvP.", null, null, null),
            // staff tools
            new Cmd(Material.ENDER_EYE, "/vanish [player]", "Hide from everyone: tab list, chat completion, /list, server list.", "smpcore.vanish", "staff.vanish.enabled", "/v"),
            new Cmd(Material.SNOWBALL, "/freeze <player>", "Freeze or unfreeze a player in place.", "smpcore.freeze", "staff.freeze.enabled", null),
            new Cmd(Material.SPYGLASS, "/invsee <player>", "Look into a player's inventory.", "smpcore.invsee", "staff.invsee.enabled", null),
            new Cmd(Material.AMETHYST_SHARD, "/endersee <player>", "Look into a player's ender chest.", "smpcore.invsee", "staff.invsee.enabled", null),
            // punishments
            new Cmd(Material.MACE, "/ban <player> <duration> [reason]", "Ban a player (replaces the vanilla /ban). 1h, 3d12h or perm.", "smpcore.tempban", null, null),
            new Cmd(Material.LIME_DYE, "/unban <player>", "Unban a player.", "smpcore.tempban", null, null),
            new Cmd(Material.TOTEM_OF_UNDYING, "/pardon <player>", "Unban a player (replaces the vanilla /pardon).", "smpcore.tempban", null, null),
            new Cmd(Material.CLOCK, "/tempban <ban|unban|list|history|gui>", "Temporary bans with a GUI and custom messages.", "smpcore.tempban", null, "/tban"),
            new Cmd(Material.IRON_BARS, "/punish <player>", "Open the punish menu: ban, mute, kick, warn.", "smpcore.tempban", null, null),
            // settings
            new Cmd(Material.COMPARATOR, "/smp", "Open this settings menu.", "smpcore.admin", null, "/smpcore, /smpsettings"),
            new Cmd(Material.REDSTONE, "/settings", "Open this settings menu (same as /smp).", "smpcore.admin", null, null),
            new Cmd(Material.REPEATER, "/smp reload", "Re-read config.yml and the ban lists.", "smpcore.admin", null, null),
            new Cmd(Material.BARRIER, "/smp maintenance <on|off>", "Close the server: only ops and smpcore.maintenance.bypass can join. Works from the console.", "smpcore.admin", "tweaks.maintenance", null),
            new Cmd(Material.ENCHANTED_GOLDEN_APPLE, "/smp itemlimit <add|list|remove>", "Limit how many of an item a player can carry. Hold the item for add.", "smpcore.admin", "item-limit.enabled", null),
            new Cmd(Material.HOPPER, "/smp itemban <add|remove|list> [material]", "Ban or allow an item.", "smpcore.admin", "item-ban.enabled", null),
            new Cmd(Material.NAME_TAG, "/smp whitelist <add|remove|list>", "Custom weapon whitelist for banned base items.", "smpcore.admin", null, null),
            new Cmd(Material.CLOCK, "/smp grace <start|stop> [player|all]", "Start or stop the grace period.", "smpcore.admin", "grace.enabled", null),
            new Cmd(Material.LEATHER_BOOTS, "/smp spawn delay <seconds>", "Wait time before /spawn and /warp teleport.", "smpcore.admin", "spawn.enabled", null),
            new Cmd(Material.COMPASS, "/smp rtp [player]", "Send a player to a random spot.", "smpcore.admin", "random-tp.enabled", null));

    /** Every command: the curated list plus anything else registered in plugin.yml. */
    public static List<Cmd> all(SMPCore plugin) {
        List<Cmd> out = new ArrayList<>(CURATED);
        Set<String> known = new HashSet<>();
        for (Cmd c : out) known.add(c.name().toLowerCase());
        plugin.getDescription().getCommands().forEach((name, info) -> {
            if (known.contains("/" + name.toLowerCase())) return;
            Object d = info.get("description");
            Object p = info.get("permission");
            out.add(new Cmd(Material.PAPER, "/" + name, d == null ? "" : d.toString(), p == null ? null : p.toString(), null, null));
        });
        return out;
    }

    private final Supplier<Gui> parent;
    private final List<Cmd> all;

    public CommandsMenu(SMPCore plugin, Player viewer, Supplier<Gui> parent) {
        super(plugin, viewer, "Commands");
        this.parent = parent;
        this.all = all(plugin);
    }

    @Override protected Supplier<Gui> parent() { return parent; }

    @Override protected List<Cmd> entries() { return all; }

    /** Tooltip lines are never wrapped by the client, so long descriptions are cut into short lines here. */
    private static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + w.length() > max) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    @Override protected ItemStack icon(Cmd c) {
        List<String> lore = new ArrayList<>();
        if (!c.desc().isEmpty()) {
            for (String l : wrap(c.desc(), 34)) lore.add(Theme.c(Theme.MID, Theme.esc(l)));
            lore.add(" ");
        }
        if (c.usage().length() > 28) {
            lore.add(Theme.c(Theme.DIM, "Usage:"));
            lore.add(Theme.yellow(Theme.esc(c.usage())));
        } else {
            lore.add(Lore.value("Usage", Theme.esc(c.usage())));
        }
        if (c.aliases() != null) lore.add(Lore.value("Aliases", Theme.esc(c.aliases())));
        lore.add(Lore.value("Permission", c.perm() == null ? "everyone" : c.perm()));
        boolean on = c.path() != null && cfg().getBoolean(c.path());
        if (c.path() != null) {
            lore.add(Theme.c(Theme.DIM, "Feature:") + " " + Theme.state(on));
            lore.add(Lore.left() + " " + Lore.key(true) + " " + Theme.c(Theme.DIM, "to Enable"));
            lore.add(Lore.right() + " " + Lore.key(false) + " " + Theme.c(Theme.DIM, "to Disable"));
            if ("spawn.enabled".equals(c.path())) lore.add(Theme.yellow("Q") + " " + Theme.c(Theme.DIM, "Spawn settings"));
        }
        return Items.of(c.icon()).name(Theme.hue(all.indexOf(c) + 1, Theme.esc(c.name()))).lore(lore).glow(on).build();
    }

    /** Left click enables the feature behind the command, right click disables it (Q opens the spawn settings). */
    @Override protected void onClick(Cmd c, InventoryClickEvent e) {
        if (c.path() == null) return;
        if ("spawn.enabled".equals(c.path()) && e.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
            Menus.spawn(plugin, viewer, () -> this).open();
            return;
        }
        if (e.isLeftClick()) cfg().set(c.path(), true);
        else if (e.isRightClick()) cfg().set(c.path(), false);
        else return;
        save();
        refresh();
    }
}
