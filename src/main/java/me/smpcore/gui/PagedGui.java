package me.smpcore.gui;

import java.util.List;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Theme;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** 6-row menu with a 7x4 item grid inside the glass border and page buttons. Each page gets its own accent colour. */
public abstract class PagedGui<T> extends Gui {
    private static final Material[] ACCENTS = {
            Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE};
    private int page = 0;

    protected PagedGui(SMPCore plugin, Player viewer, String title) {
        super(plugin, viewer, 6, title);
    }

    protected abstract List<T> entries();

    protected abstract ItemStack icon(T t);

    protected abstract void onClick(T t, InventoryClickEvent e);

    protected Supplier<Gui> parent() {
        return null;
    }

    protected void resetPage() {
        page = 0;
    }

    @Override protected Material accent() {
        return ACCENTS[page % ACCENTS.length];
    }

    /** Extra buttons on the bottom row (slots 46, 47, 51, 52 are free). */
    protected void extra() {}

    @Override
    protected void build() {
        List<T> list = entries();
        int per = 28;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        border();
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= list.size()) break;
            T t = list.get(idx);
            set(10 + (i / 7) * 9 + (i % 7), icon(t), e -> onClick(t, e));
        }
        backButton(parent());
        if (page > 0) set(48, Items.of(Material.ARROW).name("Previous page").build(), e -> { page--; refresh(); });
        set(49, Items.of(Material.PAPER).name("Page " + Theme.yellow(page + 1) + "/" + pages)
                .lore(Theme.yellow(list.size()) + " entries").build());
        if (page < pages - 1) set(50, Items.of(Material.ARROW).name("Next page").build(), e -> { page++; refresh(); });
        extra();
    }
}
