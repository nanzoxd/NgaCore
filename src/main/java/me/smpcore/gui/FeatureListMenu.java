package me.smpcore.gui;

import java.util.List;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.gui.Feature.Category;
import me.smpcore.util.Items;
import me.smpcore.util.Lore;
import me.smpcore.util.Theme;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The one big settings menu behind /smp and /settings: every feature on a single screen.
 * Switches use left = enable, right = disable; menus open on left click.
 */
public final class FeatureListMenu extends PagedGui<Feature> {
    private final Supplier<List<Feature>> source;
    private final Supplier<Gui> parent;
    private String query = "";
    /** null = every feature. */
    private Category cat = null;

    public FeatureListMenu(SMPCore plugin, Player viewer, String title, Supplier<List<Feature>> source, Supplier<Gui> parent) {
        super(plugin, viewer, title);
        this.source = source;
        this.parent = parent;
    }

    @Override protected Supplier<Gui> parent() { return parent; }

    @Override protected List<Feature> entries() {
        String q = query.toLowerCase();
        return source.get().stream()
                .filter(f -> cat == null || f.cat() == cat)
                .filter(f -> q.isEmpty() || f.name().toLowerCase().contains(q)).toList();
    }

    @Override protected Material accent() {
        if (cat == null) return super.accent();
        return switch (cat) {
            case PVP -> Material.RED_STAINED_GLASS_PANE;
            case ADMIN -> Material.ORANGE_STAINED_GLASS_PANE;
            case WORLD -> Material.LIME_STAINED_GLASS_PANE;
            case UTIL -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        };
    }

    /** Order the nether star cycles through (null = every feature). */
    private static final Category[] CYCLE = {null, Category.PVP, Category.ADMIN, Category.WORLD, Category.UTIL};

    private static String label(Category c) {
        return c == null ? "All" : c.label;
    }

    private long count(Category c) {
        return source.get().stream().filter(f -> c == null || f.cat() == c).count();
    }

    /** Left click = next category, right click = previous one. */
    private void cycle(boolean forward) {
        int i = 0;
        for (int k = 0; k < CYCLE.length; k++) if (CYCLE[k] == cat) i = k;
        cat = CYCLE[Math.floorMod(i + (forward ? 1 : -1), CYCLE.length)];
        resetPage();
        refresh();
    }

    /** Slot 1 of the top row: one nether star that cycles through all the categories. */
    private void categoryButton() {
        List<String> lore = new java.util.ArrayList<>();
        lore.add(Theme.c(Theme.DIM, "Showing: ") + Theme.green(label(cat)));
        lore.add(" ");
        for (Category c : CYCLE) {
            String line = c == cat ? Theme.green("▸ " + label(c)) : Theme.c(Theme.MID, "  " + label(c));
            lore.add(line + " " + Theme.yellow(count(c)));
        }
        lore.add(" ");
        lore.add(Lore.left() + " " + Lore.key(true) + " " + Theme.c(Theme.DIM, "Next category"));
        lore.add(Lore.right() + " " + Lore.key(false) + " " + Theme.c(Theme.DIM, "Previous category"));
        set(0, Items.of(Material.NETHER_STAR).name("Categories").lore(lore).glow(cat != null).build(), e -> {
            if (e.isRightClick()) cycle(false);
            else if (e.isLeftClick()) cycle(true);
        });
    }

    private boolean on(Feature f) {
        return f.path() != null && cfg().getBoolean(f.path());
    }

    @Override protected ItemStack icon(Feature f) {
        List<String> lore;
        if (f.custom() != null) {
            lore = f.custom().lore(cfg());
        } else if (f.simple()) {
            lore = Lore.toggle(on(f));
        } else if (f.path() == null) {
            lore = List.of(Lore.open());
        } else {
            lore = List.of(Lore.open(), Lore.opt("Status", on(f), false));
        }
        if ("combat.enabled".equals(f.path())) {
            lore = new java.util.ArrayList<>(lore);
            lore.add(" ");
            lore.add(Theme.yellow("Q") + " " + Theme.c(Theme.DIM, "Rejoin time:") + " " + Theme.state(cfg().getBoolean("combat.rejoin.enabled")));
            lore.add(Theme.c(Theme.MID, "  Disconnect in combat? Log back in within " + plugin.combat.rejoinSeconds() + "s"));
            lore.add(Theme.c(Theme.MID, "  or be killed. /smp combat rejoin <seconds>"));
        }
        if (!f.hints().isEmpty()) {
            lore = new java.util.ArrayList<>(lore);
            lore.add(" ");
            lore.add(Theme.c(Theme.MID, "Commands"));
            for (String h : f.hints()) {
                String[] p = h.split("\\|", 2);
                lore.add(Theme.yellow(Theme.esc(p[0])) + (p.length > 1 ? " " + Theme.c(Theme.DIM, p[1]) : ""));
            }
        }
        if ("announce.enabled".equals(f.path())) {
            lore = new java.util.ArrayList<>(lore);
            lore.add(" ");
            String cur = plugin.announce.presetName();
            StringBuilder row = new StringBuilder(Theme.c(Theme.DIM, "Colour: "));
            boolean first = true;
            for (String n : plugin.announce.presetNames()) {
                if (!first) row.append(Theme.c(Theme.MID, " | "));
                first = false;
                row.append(n.equals(cur) ? Theme.c(plugin.announce.presetHex(n), Theme.pretty(n)) : Theme.c(Theme.MID, Theme.pretty(n)));
            }
            lore.add(row.toString());
            lore.add(Theme.yellow("Q") + " " + Theme.c(Theme.DIM, "next") + Theme.c(Theme.MID, " | ") + Theme.yellow("Ctrl+Q") + " " + Theme.c(Theme.DIM, "previous"));
        }
        int hue = 0;
        if (!f.name().equals("Random TP")) {
            List<Feature> all = source.get();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).name().equals(f.name())) {
                    hue = i + 1;
                    break;
                }
            }
        }
        return Items.of(f.icon()).name(Theme.hue(hue, Theme.esc(Theme.smallCaps(f.name())))).lore(lore).glow(on(f)).build();
    }

    @Override protected void onClick(Feature f, InventoryClickEvent e) {
        if ("combat.enabled".equals(f.path()) && e.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
            cfg().set("combat.rejoin.enabled", !cfg().getBoolean("combat.rejoin.enabled"));
            save();
            refresh();
            return;
        }
        if ("announce.enabled".equals(f.path())) {
            var click = e.getClick();
            if (click == org.bukkit.event.inventory.ClickType.DROP || click == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
                plugin.announce.cyclePreset(click == org.bukkit.event.inventory.ClickType.DROP ? 1 : -1);
                plugin.announce.preview(viewer);
                refresh();
                return;
            }
        }
        if (f.custom() != null) {
            if (!e.isLeftClick() && !e.isRightClick()) return;
            f.custom().click(cfg(), e.isLeftClick());
        } else if (f.simple()) {
            if (e.isRightClick()) cfg().set(f.path(), false);
            else if (e.isLeftClick()) cfg().set(f.path(), true);
            else return;
        } else if (e.isRightClick() && f.path() != null) {
            cfg().set(f.path(), !cfg().getBoolean(f.path()));
        } else {
            f.open().open(viewer, () -> this);
            return;
        }
        save();
        refresh();
    }

    @Override protected void extra() {
        // top row: nether star (first slot) cycles the categories, command block (ninth slot) lists every command
        categoryButton();
        set(8, Items.of(Material.COMMAND_BLOCK).name("Commands")
                .lore(Theme.c(Theme.MID, "Every command in one list,"),
                        Theme.c(Theme.MID, "like /vanish, /spawn and /warp."),
                        Theme.yellow(CommandsMenu.all(plugin).size()) + " " + Theme.c(Theme.DIM, "commands"),
                        Lore.open()).build(),
                e -> new CommandsMenu(plugin, viewer, () -> this).open());
        set(46, Items.of(Material.SPYGLASS).name("Search")
                .lore(query.isEmpty() ? "Find a feature by name." : "Showing " + Theme.blue(Theme.esc(query))).build(), e -> {
            viewer.closeInventory();
            plugin.prompts.ask(viewer, "Type what to search for.", text -> {
                query = text.trim();
                resetPage();
                open();
            });
        });
        if (!query.isEmpty()) {
            set(47, Items.of(Material.RED_DYE).name(Theme.red("Clear search")).build(), e -> {
                query = "";
                resetPage();
                refresh();
            });
        }
        set(52, Items.of(Material.REDSTONE).name("Reload config")
                .lore("Re-reads " + Theme.blue("config.yml") + " after manual edits.").build(), e -> {
            plugin.reload();
            refresh();
        });
    }
}
