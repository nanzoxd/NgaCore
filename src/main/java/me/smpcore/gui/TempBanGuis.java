package me.smpcore.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.module.TempBanManager;
import me.smpcore.util.Items;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import me.smpcore.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Ban flow: pick player -> pick duration -> pick or type a message -> confirm. */
public final class TempBanGuis {
    private TempBanGuis() {}

    public static final class Draft {
        public UUID target;
        public String name;
        public long duration = 86_400_000L;
        public String reason;
    }

    abstract static class BanGui extends Gui {
        BanGui(SMPCore p, Player v, int rows, String title) { super(p, v, rows, title); }
        @Override public String permission() { return "smpcore.tempban"; }
    }

    abstract static class BanPaged<T> extends PagedGui<T> {
        BanPaged(SMPCore p, Player v, String title) { super(p, v, title); }
        @Override public String permission() { return "smpcore.tempban"; }
    }

    // ------------------------------------------------------------------ step 1

    public static final class PlayerPicker extends BanPaged<Player> {
        private final Supplier<Gui> parent;

        public PlayerPicker(SMPCore plugin, Player viewer, Supplier<Gui> parent) {
            super(plugin, viewer, "TempBan: Select Player");
            this.parent = parent;
        }

        @Override protected Supplier<Gui> parent() { return parent; }

        @Override protected List<Player> entries() {
            List<Player> l = new ArrayList<>(Bukkit.getOnlinePlayers());
            l.remove(viewer);
            l.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            return l;
        }

        @Override protected ItemStack icon(Player p) {
            return Items.of(Material.PLAYER_HEAD).head(p).name(Theme.blue(p.getName()))
                    .lore(Theme.blue("Click") + " to select").build();
        }

        @Override protected void onClick(Player p, InventoryClickEvent e) {
            new PunishMenu(plugin, viewer, p.getUniqueId(), p.getName(), () -> this).open();
        }

        @Override protected void extra() {
            set(51, Items.of(Material.NAME_TAG).name("Type a name")
                    .lore("Ban an offline player", "who has joined before.").build(), e ->
                    plugin.prompts.ask(viewer, "Type the player name.", text -> {
                        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(text);
                        if (op == null) {
                            Msg.send(viewer, Theme.red("Unknown player") + " (they must have joined before).");
                            open();
                            return;
                        }
                        Draft d = new Draft();
                        d.target = op.getUniqueId();
                        d.name = op.getName() == null ? text : op.getName();
                        new DurationMenu(plugin, viewer, d, () -> this).open();
                    }));
            set(52, Items.of(Material.IRON_BARS).name("Active bans")
                    .lore("Click a head to unban.").build(), e -> new ActiveBans(plugin, viewer, () -> this).open());
        }
    }

    // ------------------------------------------------------------------ step 2

    public static final class DurationMenu extends BanGui {
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        private final Draft d;
        private final Supplier<Gui> parent;

        public DurationMenu(SMPCore plugin, Player viewer, Draft d, Supplier<Gui> parent) {
            super(plugin, viewer, 4, "TempBan: Duration");
            this.d = d;
            this.parent = parent;
        }

        @Override protected void build() {
            border();
            backButton(parent);
            set(4, Items.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(d.target))
                    .name(Theme.blue(d.name)).lore("Choose how long.").build());
            List<String> durations = cfg().getStringList("tempban.durations");
            for (int i = 0; i < durations.size() && i < SLOTS.length; i++) {
                Long ms = TimeUtil.parse(durations.get(i));
                if (ms == null) continue;
                set(SLOTS[i], Items.of(ms < 0 ? Material.BARRIER : Material.CLOCK)
                        .name(Theme.orange(ms < 0 ? "Permanent" : TimeUtil.format(ms))).build(), e -> {
                    d.duration = ms;
                    new ReasonMenu(plugin, viewer, d, () -> this).open();
                });
            }
            set(31, Items.of(Material.NAME_TAG).name("Custom duration")
                    .lore("Example " + Theme.yellow("3d12h") + " or " + Theme.yellow("perm")).build(), e ->
                    plugin.prompts.ask(viewer, "Type a duration like " + Theme.yellow("3d12h") + " or " + Theme.yellow("perm") + ".", text -> {
                        Long ms = TimeUtil.parse(text);
                        if (ms == null) {
                            Msg.send(viewer, Theme.red("Invalid duration."));
                            open();
                            return;
                        }
                        d.duration = ms;
                        new ReasonMenu(plugin, viewer, d, () -> this).open();
                    }));
        }
    }

    // ------------------------------------------------------------------ step 3

    public static final class ReasonMenu extends BanGui {
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        private final Draft d;
        private final Supplier<Gui> parent;

        public ReasonMenu(SMPCore plugin, Player viewer, Draft d, Supplier<Gui> parent) {
            super(plugin, viewer, 4, "TempBan: Message");
            this.d = d;
            this.parent = parent;
        }

        @Override protected void build() {
            border();
            backButton(parent);
            ConfigurationSection presets = cfg().getConfigurationSection("tempban.presets");
            int i = 0;
            if (presets != null) {
                for (String key : presets.getKeys(false)) {
                    if (i >= SLOTS.length) break;
                    String msg = presets.getString(key, key);
                    set(SLOTS[i++], Items.of(Material.PAPER).name(Theme.orange(key))
                            .lore(Theme.c(Theme.DIM, msg), "", Theme.blue("Click") + " to use").build(), e -> {
                        d.reason = msg;
                        new Confirm(plugin, viewer, d, () -> this).open();
                    });
                }
            }
            set(31, Items.of(Material.WRITABLE_BOOK).name("Custom message")
                    .lore("Type any message the player", "will see on the ban screen.").build(), e ->
                    plugin.prompts.ask(viewer, "Type the ban message.", text -> {
                        d.reason = text;
                        new Confirm(plugin, viewer, d, () -> this).open();
                    }));
        }
    }

    // ------------------------------------------------------------------ step 4

    public static final class Confirm extends BanGui {
        private final Draft d;
        private final Supplier<Gui> parent;

        public Confirm(SMPCore plugin, Player viewer, Draft d, Supplier<Gui> parent) {
            super(plugin, viewer, 3, "TempBan: Confirm");
            this.d = d;
            this.parent = parent;
        }

        @Override protected void build() {
            border();
            String reason = d.reason == null ? cfg().getString("tempban.default-reason", "") : d.reason;
            set(13, Items.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(d.target)).name(Theme.blue(d.name))
                    .lore("Duration " + Theme.orange(d.duration < 0 ? "Permanent" : TimeUtil.format(d.duration)),
                            "Message " + Theme.red(Theme.esc(reason))).build());
            set(11, Items.of(Material.LIME_CONCRETE).name(Theme.green("Ban")).build(), e -> {
                plugin.tempBans.ban(d.target, d.name, d.duration, reason, viewer.getName());
                Msg.send(viewer, "Banned " + Theme.blue(d.name) + ".");
                viewer.closeInventory();
            });
            set(15, Items.of(Material.RED_CONCRETE).name(Theme.red("Cancel")).build(), e -> viewer.closeInventory());
            backButton(parent);
        }
    }

    // ------------------------------------------------------------------ active bans

    public static final class ActiveBans extends BanPaged<TempBanManager.Entry> {
        private final Supplier<Gui> parent;

        public ActiveBans(SMPCore plugin, Player viewer, Supplier<Gui> parent) {
            super(plugin, viewer, "TempBan: Active Bans");
            this.parent = parent;
        }

        @Override protected Supplier<Gui> parent() { return parent; }

        @Override protected List<TempBanManager.Entry> entries() {
            return plugin.tempBans.activeBans();
        }

        @Override protected ItemStack icon(TempBanManager.Entry e) {
            long left = e.permanent() ? -1 : Math.max(0, e.expires() - System.currentTimeMillis());
            return Items.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(e.uuid())).name(Theme.red(e.name()))
                    .lore("Reason " + Theme.orange(Theme.esc(e.reason())),
                            "Remaining " + Theme.yellow(TimeUtil.format(left)),
                            "By " + Theme.blue(Theme.esc(e.staff())), "",
                            Theme.blue("Click") + " to unban").build();
        }

        @Override protected void onClick(TempBanManager.Entry e, InventoryClickEvent ev) {
            plugin.tempBans.unban(e.uuid());
            Msg.send(viewer, "Unbanned " + Theme.blue(e.name()) + ".");
            refresh();
        }
    }
}
