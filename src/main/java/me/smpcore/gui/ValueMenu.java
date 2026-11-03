package me.smpcore.gui;

import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Lore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** One item per setting: left raises, right lowers, shift moves 5 steps, middle resets, Q toggles. */
public final class ValueMenu extends Gui {
    public record Row(String path, String valueKey, Material icon, String label, double def, double step, double max) {}

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final List<Row> list;
    private final DoubleFunction<String> fmt;
    private final Supplier<Gui> parent;

    /** masterPath and masterLabel are kept for older callers; the master switch lives in the main settings list. */
    public ValueMenu(SMPCore plugin, Player viewer, String title, String masterPath, String masterLabel,
                     List<Row> list, DoubleFunction<String> fmt, Supplier<Gui> parent) {
        super(plugin, viewer, list.size() > 7 ? 4 : 3, title);
        this.list = list;
        this.fmt = fmt;
        this.parent = parent;
    }

    @Override
    protected void build() {
        border();
        backButton(parent);
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            Row r = list.get(i);
            boolean on = cfg().getBoolean(r.path() + ".enabled");
            double v = cfg().getDouble(r.path() + "." + r.valueKey(), r.def());
            set(SLOTS[i], Items.of(r.icon()).name(r.label())
                    .lore(Lore.value("Value", fmt.apply(v)), Lore.inc(), Lore.dec(), Lore.reset(),
                            Lore.shift("Change by " + trim(r.step() * 5)), Lore.key("Q", on))
                    .glow(on).build(), e -> {
                double cur = cfg().getDouble(r.path() + "." + r.valueKey(), r.def());
                ClickType t = e.getClick();
                double step = r.step() * (e.isShiftClick() ? 5 : 1);
                if (t == ClickType.MIDDLE) {
                    cfg().set(r.path() + "." + r.valueKey(), r.def());
                } else if (t == ClickType.DROP || t == ClickType.CONTROL_DROP || t.isKeyboardClick()) {
                    cfg().set(r.path() + ".enabled", !on);
                } else if (t.isLeftClick()) {
                    cfg().set(r.path() + "." + r.valueKey(), Math.min(r.max(), cur + step));
                } else if (t.isRightClick()) {
                    cfg().set(r.path() + "." + r.valueKey(), Math.max(0, cur - step));
                }
                save();
                refresh();
            });
        }
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
