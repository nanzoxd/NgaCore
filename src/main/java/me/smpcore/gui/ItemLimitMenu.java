package me.smpcore.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.module.ItemLimitModule;
import me.smpcore.util.Lore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Item Limit: click the hopper with an item on your cursor to limit how many of it a player can carry (2 by
 * default). Click a limit to change it. Everything over the limit is dropped from the inventory.
 */
public final class ItemLimitMenu extends PagedGui<ItemLimitModule.Rule> {
    private final Supplier<Gui> parent;

    public ItemLimitMenu(SMPCore plugin, Player viewer, Supplier<Gui> parent) {
        super(plugin, viewer, "Item Limit");
        this.parent = parent;
    }

    @Override protected Supplier<Gui> parent() { return parent; }

    @Override protected List<ItemLimitModule.Rule> entries() { return plugin.itemLimit.rules(); }

    @Override protected ItemStack icon(ItemLimitModule.Rule r) {
        ItemStack it = r.item().clone();
        var meta = it.getItemMeta();
        List<net.kyori.adventure.text.Component> l = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        l.add(Theme.parse(" "));
        for (String pl : me.smpcore.module.ItemBanModule.potionLines(it, "is limited")) l.add(Theme.parse(pl));
        l.add(Theme.parse(Theme.c(Theme.DIM, "Limit: ") + Theme.yellow(r.limit()) + Theme.c(Theme.DIM, r.limit() == 0 ? "  (can't carry any)" : " per player")));
        l.add(Theme.parse(Theme.c(Theme.DIM, "Applies to: ") + (r.whole() ? Theme.blue("the whole item type") : Theme.blue("this exact item"))));
        l.add(Theme.parse(Theme.c(Theme.MID, "Over the limit it is dropped.")));
        l.add(Theme.parse(" "));
        l.add(Theme.parse(Lore.left() + " " + Lore.key(true) + " " + Theme.c(Theme.DIM, "Limit +1")));
        l.add(Theme.parse(Lore.right() + " " + Lore.key(false) + " " + Theme.c(Theme.DIM, "Limit -1")));
        l.add(Theme.parse(Theme.yellow("Shift") + " " + Theme.c(Theme.DIM, "Change by 8")));
        l.add(Theme.parse(Theme.yellow("Q") + " " + Theme.c(Theme.DIM, "Remove the limit")));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    @Override protected void onClick(ItemLimitModule.Rule r, InventoryClickEvent e) {
        if (e.getClick() == ClickType.DROP) {
            plugin.itemLimit.remove(r);
        } else {
            int step = e.isShiftClick() ? 8 : 1;
            if (e.isLeftClick()) plugin.itemLimit.setLimit(r, r.limit() + step);
            else if (e.isRightClick()) plugin.itemLimit.setLimit(r, r.limit() - step);
            else return;
        }
        refresh();
    }

    private void add(ItemStack it, boolean whole) {
        if (it == null || it.getType().isAir()) {
            Msg.send(viewer, "Put an item on your cursor first, then click the hopper.");
            return;
        }
        var r = plugin.itemLimit.add(it, whole, plugin.itemLimit.defaultLimit());
        Msg.send(viewer, "Limit for " + Theme.yellow(Theme.esc(r.label())) + " is " + Theme.yellow(r.limit()) + (r.whole() ? " (whole item type)" : " (this exact item)")
                + ". " + Theme.c(Theme.DIM, "Change it in this menu."));
        refresh();
    }

    @Override protected void extra() {
        set(4, Items.of(Material.HOPPER).name(Theme.green("Limit an item"))
                .lore(Theme.c(Theme.DIM, "Put an item on your cursor,"), Theme.c(Theme.DIM, "then click here. Or hold it."),
                        Theme.c(Theme.MID, "New limit: " + plugin.itemLimit.defaultLimit() + " per player."), " ",
                        Lore.left() + " " + Lore.key(true) + " " + Theme.c(Theme.DIM, "this exact item"),
                        Lore.right() + " " + Lore.key(false) + " " + Theme.c(Theme.DIM, "the whole item type"),
                        Theme.c(Theme.MID, "Plain items always limit the whole type.")).glow(true).build(), e -> {
            ItemStack it = e.getCursor();
            if (it == null || it.getType().isAir()) it = viewer.getInventory().getItemInMainHand();
            add(it, e.isRightClick() || e.isShiftClick());
        });
        toggleButton(46, Material.LEVER, "Item limit", "item-limit.enabled", "Master switch.");
        int def = plugin.itemLimit.defaultLimit();
        set(51, Items.of(Material.COMPARATOR).name("New limit")
                .lore(Theme.c(Theme.DIM, "Limit for newly added items: ") + Theme.yellow(def),
                        Lore.left() + " " + Lore.key(true) + " " + Theme.c(Theme.DIM, "+1"),
                        Lore.right() + " " + Lore.key(false) + " " + Theme.c(Theme.DIM, "-1")).build(), e -> {
            int now = plugin.itemLimit.defaultLimit() + (e.isLeftClick() ? 1 : e.isRightClick() ? -1 : 0);
            plugin.getConfig().set("item-limit.default-limit", Math.max(0, Math.min(64, now)));
            plugin.saveConfig();
            refresh();
        });
    }
}
