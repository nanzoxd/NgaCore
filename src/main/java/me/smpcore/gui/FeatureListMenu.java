package me.smpcore.gui;

import java.util.List;
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
 * The one big settings menu behind /smp and /settings: every feature on a single screen.
 * Switches use left = enable, right = disable; menus open on left click.
 */
public final class FeatureListMenu extends PagedGui<Feature> {
    private final Supplier<List<Feature>> source;
    private final Supplier<Gui> parent;
    private String query = "";

    public FeatureListMenu(SMPCore plugin, Player viewer, String title, Supplier<List<Feature>> source, Supplier<Gui> parent) {
        super(plugin, viewer, title);
        this.source = source;
        this.parent = parent;
    }

    @Override protected Supplier<Gui> parent() { return parent; }

    @Override protected List<Feature> entries() {
        String q = query.toLowerCase();
        return source.get().stream().filter(f -> q.isEmpty() || f.name().toLowerCase().contains(q)).toList();
    }

    private boolean on(Feature f) {
        return f.path() != null && cfg().getBoolean(f.path());
    }

    @Override protected ItemStack icon(Feature f) {
        List<String> lore;
        if (f.custom() != null) {
            lore = f.custom().lore(cfg());
        } else if (f.simple()) {
            lore = List.of(Lore.state(on(f)));
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
