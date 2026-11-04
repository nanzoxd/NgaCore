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

    /** One extra glass layer under the category tabs, so the tabs and the items are clearly apart. */
    @Override protected int gridTop() { return 2; }

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

    private void tab(int slot, Category c, Material icon, String label) {
        boolean sel = cat == c;
        long n = source.get().stream().filter(f -> c == null || f.cat() == c).count();
        set(slot, Items.of(icon).name(sel ? Theme.green(label) : label)
                .lore(Theme.yellow(n) + " features", sel ? Theme.c(Theme.DIM, "Showing now") : Lore.open())
                .glow(sel).build(), e -> {
            cat = c;
            resetPage();
            refresh();
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
        return Items.of(f.icon()).name(f.name()).lore(lore).glow(on(f)).build();
    }

    @Override protected void onClick(Feature f, InventoryClickEvent e) {
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
        // second glass layer (row 1) separates the tabs from the item grid
        ItemStack glass = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 10; i <= 16; i++) set(i, glass);
        set(13, Items.of(accent()).name(" ").build());
        // category tabs replace the glass in the top row
        tab(1, null, Material.NETHER_STAR, "All");
        tab(2, Category.PVP, Category.PVP.icon, Category.PVP.label);
        tab(3, Category.ADMIN, Category.ADMIN.icon, Category.ADMIN.label);
        tab(5, Category.WORLD, Category.WORLD.icon, Category.WORLD.label);
        tab(6, Category.UTIL, Category.UTIL.icon, Category.UTIL.label);
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
