package me.smpcore.gui;

import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * One entry in the feature list. A simple feature (open == null) is just a switch:
 * left click enables, right click disables. A complex one opens its settings on left
 * click and flips its master switch (if it has one) on right click.
 */
public record Feature(Category cat, Material icon, String name, String desc, String path, Opener open, Custom custom) {
    public Feature(Category cat, Material icon, String name, String desc, String path, Opener open) {
        this(cat, icon, name, desc, path, open, null);
    }

    public enum Category {
        UTIL("Util", Material.REDSTONE),
        ADMIN("Administrator", Material.NETHERITE_SWORD),
        PVP("PVP", Material.IRON_SWORD),
        WORLD("World", Material.GRASS_BLOCK);

        public final String label;
        public final Material icon;

        Category(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    @FunctionalInterface
    public interface Opener {
        void open(Player viewer, Supplier<Gui> back);
    }

    /** A feature with several options in its own tooltip: left and right click each flip one of them. */
    public interface Custom {
        java.util.List<String> lore(org.bukkit.configuration.file.FileConfiguration c);

        void click(org.bukkit.configuration.file.FileConfiguration c, boolean left);
    }

    public boolean simple() {
        return open == null && custom == null;
    }
}
