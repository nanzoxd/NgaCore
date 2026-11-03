package me.smpcore.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Lore;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Base class for every menu. Clicks are routed to per-slot actions. */
public abstract class Gui implements InventoryHolder {
    protected final SMPCore plugin;
    protected final Player viewer;
    protected final int rows;
    protected final String titleText;
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Gui(SMPCore plugin, Player viewer, int rows, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.rows = rows;
        this.titleText = title;
        this.inv = Bukkit.createInventory(this, rows * 9, Theme.guiTitle(title));
    }

    protected abstract void build();

    public String permission() {
        return "smpcore.admin";
    }

    public void open() {
        refresh();
        viewer.openInventory(inv);
    }

    public void refresh() {
        actions.clear();
        inv.clear();
        build();
    }

    public void handleClick(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    protected FileConfiguration cfg() {
        return plugin.getConfig();
    }

    protected void save() {
        plugin.saveConfig();
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** Accent colour of the glass border (corners and the middle of the top and bottom rows). */
    protected Material accent() {
        if (titleText.startsWith("Punish") || titleText.startsWith("TempBan")) return Material.CYAN_STAINED_GLASS_PANE;
        return switch (titleText) {
            case "Cooldowns" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "Damage Limiters" -> Material.RED_STAINED_GLASS_PANE;
            case "Netherite Ban" -> Material.BLACK_STAINED_GLASS_PANE;
            case "Spear Ban" -> Material.ORANGE_STAINED_GLASS_PANE;
            case "Item Ban", "Item Ban Options" -> Material.MAGENTA_STAINED_GLASS_PANE;
            case "Chat Moderation" -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            default -> Material.PURPLE_STAINED_GLASS_PANE;
        };
    }

    /** SMPUtils-style layout: gray glass ring, coloured corners and centres. */
    protected void border() {
        ItemStack side = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        ItemStack acc = Items.of(accent()).name(" ").build();
        int n = rows * 9;
        for (int i = 0; i < n; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) inv.setItem(i, side);
        }
        for (int i : new int[] {0, 4, 8, n - 9, n - 5, n - 1}) inv.setItem(i, acc);
    }

    /** Red barrier bottom-left: "Back" to the parent, or "Close" when there is none. */
    protected void backButton(Supplier<Gui> parent) {
        int slot = rows * 9 - 9;
        if (parent == null) {
            set(slot, Items.of(Material.BARRIER).name(Theme.red("Close")).build(), e -> viewer.closeInventory());
        } else {
            set(slot, Items.of(Material.BARRIER).name(Theme.red("Back")).build(), e -> parent.get().open());
        }
    }

    /** A switch: left click enables, right click disables. The tooltip shows only the state. */
    protected void toggleButton(int slot, Material icon, String label, String path, String... desc) {
        boolean on = cfg().getBoolean(path);
        List<String> lore = new ArrayList<>();
        for (String d : desc) if (d != null && !d.isEmpty()) lore.add(Theme.c(Theme.DIM, d));
        lore.add(Lore.state(on));
        set(slot, Items.of(icon).name(label).lore(lore).glow(on).build(), e -> {
            if (e.isRightClick()) cfg().set(path, false);
            else if (e.isLeftClick()) cfg().set(path, true);
            else return;
            save();
            refresh();
        });
    }
}
