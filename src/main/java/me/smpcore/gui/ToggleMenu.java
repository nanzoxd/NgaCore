package me.smpcore.gui;

import java.util.List;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** A small menu of switches inside the glass border. */
public final class ToggleMenu extends Gui {
    public record Toggle(Material icon, String label, String path, String desc) {}

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final List<Toggle> toggles;
    private final Supplier<Gui> parent;

    public ToggleMenu(SMPCore plugin, Player viewer, String title, List<Toggle> toggles, Supplier<Gui> parent) {
        super(plugin, viewer, toggles.size() > 14 ? 5 : toggles.size() > 7 ? 4 : 3, title);
        this.toggles = toggles;
        this.parent = parent;
    }

    @Override
    protected void build() {
        border();
        backButton(parent);
        for (int i = 0; i < toggles.size() && i < SLOTS.length; i++) {
            Toggle t = toggles.get(i);
            toggleButton(SLOTS[i], t.icon(), t.label(), t.path(), t.desc());
        }
    }
}
