package me.smpcore.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Small fluent item builder. Names default to light gray, lore to mid gray. */
public final class Items {
    private final ItemStack stack;
    private final ItemMeta meta;

    private Items(ItemStack stack) {
        this.stack = stack;
        this.meta = stack.getItemMeta();
    }

    public static Items of(Material m) {
        return new Items(new ItemStack(m));
    }

    public Items name(String mm) {
        meta.displayName(Theme.parse("<#f2ede4>" + mm));
        return this;
    }

    /** Gradient name for headline items. */
    public Items title(String text) {
        meta.displayName(Theme.title(text));
        return this;
    }

    public Items lore(String... lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(Theme.parse("<" + Theme.DIM + ">" + l));
        meta.lore(out);
        return this;
    }

    public Items lore(List<String> lines) {
        return lore(lines.toArray(new String[0]));
    }

    public Items glow(boolean on) {
        if (on) meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public Items head(OfflinePlayer p) {
        if (meta instanceof SkullMeta sm) sm.setOwningPlayer(p);
        return this;
    }

    public Items potionColor(Color c) {
        if (meta instanceof PotionMeta pm && c != null) pm.setColor(c);
        return this;
    }

    public ItemStack build() {
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    // ---- material helpers (spears only exist on newer versions, so look them up by name) ----

    public static Material mat(String name, Material fallback) {
        Material m = Material.matchMaterial(name);
        return m == null ? fallback : m;
    }

    public static boolean isSpear(Material m) {
        return m.name().endsWith("_SPEAR");
    }

    private static final Material[] SPEARS =
            Arrays.stream(Material.values()).filter(Items::isSpear).toArray(Material[]::new);

    public static Material[] spears() {
        return SPEARS;
    }
}
