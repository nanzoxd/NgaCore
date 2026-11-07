package me.smpcore.gui;

import java.util.ArrayList;
import java.util.List;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Lore;
import me.smpcore.util.Theme;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** /warps: click a warp to teleport (after the wait). Admins can right click to delete. */
public final class WarpMenu extends Gui {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final java.util.function.Supplier<Gui> parent;

    public WarpMenu(SMPCore plugin, Player viewer) {
        this(plugin, viewer, null);
    }

    public WarpMenu(SMPCore plugin, Player viewer, java.util.function.Supplier<Gui> parent) {
        super(plugin, viewer, 6, "Warps");
        this.parent = parent;
    }

    @Override protected void build() {
        border();
        backButton(parent);
        List<String> warps = plugin.spawn.warps();
        set(4, Items.of(Material.COMPASS).name("Warps")
                .lore(Theme.yellow(warps.size()) + " " + Theme.c(Theme.DIM, "available"),
                        Theme.c(Theme.MID, "Wait " + plugin.spawn.delay() + "s, don't move.")).build());
        set(49, Items.of(Material.NETHER_STAR).name(Theme.grad("Spawn"))
                .lore(Lore.left() + " " + Lore.key(true) + " " + Theme.c(Theme.DIM, "to Teleport")).build(), e -> {
            viewer.closeInventory();
            plugin.spawn.teleport(viewer, plugin.spawn.spawn(), "spawn");
        });
        if (warps.isEmpty()) {
            set(22, Items.of(Material.BARRIER).name("No warps yet")
                    .lore(Theme.c(Theme.DIM, "An admin can add one with " + "/setwarp <name>")).build());
        }
        boolean admin = viewer.hasPermission("smpcore.admin");
        for (int i = 0; i < warps.size() && i < SLOTS.length; i++) {
            String name = warps.get(i);
            Location l = plugin.spawn.warp(name);
            if (l == null) continue;
            List<String> lore = new ArrayList<>();
            lore.add(Theme.c(Theme.DIM, "World: ") + Theme.yellow(l.getWorld().getName()));
            lore.add(Theme.c(Theme.DIM, "At: ") + Theme.blue(l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ()));
            lore.add(Lore.left() + " " + Lore.key(true) + " " + Theme.c(Theme.DIM, "to Teleport"));
            if (admin) lore.add(Lore.right() + " " + Lore.key(false) + " " + Theme.c(Theme.DIM, "to Delete"));
            set(SLOTS[i], Items.of(Material.ENDER_PEARL).name(Theme.esc(name)).lore(lore).build(), e -> {
                if (e.isRightClick() && admin) {
                    plugin.spawn.delWarp(name);
                    refresh();
                } else if (e.isLeftClick()) {
                    viewer.closeInventory();
                    plugin.spawn.teleport(viewer, l, "warp " + name);
                }
            });
        }
    }
}
