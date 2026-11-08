package me.smpcore.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import me.smpcore.SMPCore;
import me.smpcore.module.EnchantModule;
import me.smpcore.module.PotionModule;
import me.smpcore.util.Icons;
import me.smpcore.util.Theme;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Potion and enchant limiters as dialogs: a searchable, paged list of every name,
 * then a slider for the limit. Search and page survive the trip to the slider and back.
 */
public final class LimiterDialogs {
    private LimiterDialogs() {}

    private static final int PER_PAGE = 12;

    private record Row(String key, String label, Runnable click) {}

    /** Icon + space in front of a name, or nothing when {@code dialog-icons} is off (no resource pack). */
    private static String icon(SMPCore plugin, String glyph) {
        return plugin.getConfig().getBoolean("dialog-icons", true) && !glyph.isEmpty() ? glyph + " " : "";
    }

    private static String state(int lim) {
        return lim < 0 ? Theme.green("Unlimited") : lim == 0 ? Theme.red("Banned") : "Max " + Theme.yellow(lim);
    }

    private static void pick(Player p, String title, String query, int page, List<Row> rows, List<Dialogs.Opt> extra,
                             BiConsumer<String, Integer> reopen, Runnable back) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Row> hit = rows.stream().filter(r -> q.isEmpty() || r.key().contains(q)).toList();
        int pages = Math.max(1, (hit.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<Dialogs.Opt> opts = new ArrayList<>();
        for (int i = pg * PER_PAGE; i < Math.min(hit.size(), (pg + 1) * PER_PAGE); i++) {
            Row r = hit.get(i);
            opts.add(new Dialogs.Opt(r.label(), "Click to set the limit", r.click()));
        }
        if (pg > 0) opts.add(new Dialogs.Opt(Dialogs.prev(), null, () -> reopen.accept(q, pg - 1)));
        if (pg < pages - 1) opts.add(new Dialogs.Opt(Dialogs.next(), null, () -> reopen.accept(q, pg + 1)));
        opts.addAll(extra);
        if (!q.isEmpty()) opts.add(new Dialogs.Opt(Theme.red("Clear search"), null, () -> reopen.accept("", 0)));
        List<String> body = List.of(Theme.yellow(hit.size()) + " found, page " + Theme.yellow(pg + 1) + "/" + pages
                + (q.isEmpty() ? "" : "  search " + Theme.blue(Theme.esc(q))));
        Dialogs.search(p, title, body, q, text -> reopen.accept(text == null ? "" : text, 0), opts, 2, back);
    }

    private static void save(SMPCore plugin, String path, int value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    private static Dialogs.Opt toggle(SMPCore plugin, String label, String path, Runnable reopen) {
        return new Dialogs.Opt(label + " " + Theme.state(plugin.getConfig().getBoolean(path)), "Click to toggle", () -> {
            plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
            plugin.saveConfig();
            reopen.run();
        });
    }

    // ---------------------------------------------------------------- potions

    public static void potions(SMPCore plugin, Player p, PotionModule module, Runnable back) {
        potions(plugin, p, module, back, "", 0);
    }

    private static void potions(SMPCore plugin, Player p, PotionModule module, Runnable back, String query, int page) {
        List<PotionEffectType> types = new ArrayList<>();
        for (PotionEffectType t : Registry.EFFECT) types.add(t);
        types.sort(Comparator.comparing(t -> t.getKey().getKey()));
        List<Row> rows = new ArrayList<>();
        for (PotionEffectType t : types) {
            String key = t.getKey().getKey();
            rows.add(new Row(key + " " + Theme.pretty(key).toLowerCase(), icon(plugin, Icons.effect(t)) + Theme.pretty(key) + " " + state(module.limit(t)),
                    () -> potionSlider(plugin, p, module, t, back, query, page)));
        }
        pick(p, "Potion Limiter", query, page, rows,
                List.of(new Dialogs.Opt("Options", "Brewing, use, command effects, master switch",
                        () -> potionOptions(plugin, p, module, back, query, page))),
                (q, pg) -> potions(plugin, p, module, back, q, pg), back);
    }

    private static void potionOptions(SMPCore plugin, Player p, PotionModule module, Runnable back, String query, int page) {
        Runnable again = () -> potionOptions(plugin, p, module, back, query, page);
        Dialogs.list(p, "Potion Options", List.of("Click to toggle."), List.of(
                toggle(plugin, "Block brewing", "potion-limiter.block-brewing", again),
                toggle(plugin, "Block use", "potion-limiter.block-use", again),
                toggle(plugin, "Allow command effects", "potion-limiter.allow-command-effects", again),
                toggle(plugin, "Potion limiter", "potion-limiter.enabled", again)), 2,
                () -> potions(plugin, p, module, back, query, page));
    }

    private static void potionSlider(SMPCore plugin, Player p, PotionModule module, PotionEffectType t,
                                     Runnable back, String query, int page) {
        String key = t.getKey().getKey();
        String path = "potion-limiter.effects." + key;
        int cur = module.limit(t);
        Runnable list = () -> potions(plugin, p, module, back, query, page);
        Dialogs.slider(p, icon(plugin, Icons.effect(t)) + Theme.pretty(key),
                List.of("Now " + state(cur), "0 = banned, 1 = level I, 2 = level II ..."),
                "Max level", 0, 10, 1, cur < 0 ? 10 : cur, v -> {
                    save(plugin, path, Math.round(v));
                    list.run();
                },
                List.of(new Dialogs.Opt(Theme.green("Unlimited"), "Remove the limit", () -> {
                    save(plugin, path, -1);
                    list.run();
                })), list);
    }

    // ---------------------------------------------------------------- enchantments

    public static void enchants(SMPCore plugin, Player p, EnchantModule module, Runnable back) {
        enchants(plugin, p, module, back, "", 0);
    }

    private static void enchants(SMPCore plugin, Player p, EnchantModule module, Runnable back, String query, int page) {
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) all.add(e);
        all.sort(Comparator.comparing(e -> e.getKey().getKey()));
        List<Row> rows = new ArrayList<>();
        for (Enchantment en : all) {
            String key = en.getKey().getKey();
            rows.add(new Row(key + " " + Theme.pretty(key).toLowerCase(), icon(plugin, Icons.enchant(en)) + Theme.pretty(key) + " " + state(module.limit(null, en)),
                    () -> enchantSlider(plugin, p, module, en, back, query, page)));
        }
        Runnable again = () -> enchants(plugin, p, module, back, query, page);
        pick(p, "Enchant Limiter", query, page, rows,
                List.of(toggle(plugin, "Enchant limiter", "enchant-limiter.enabled", again)),
                (q, pg) -> enchants(plugin, p, module, back, q, pg), back);
    }

    private static void enchantSlider(SMPCore plugin, Player p, EnchantModule module, Enchantment en,
                                      Runnable back, String query, int page) {
        String key = en.getKey().getKey();
        String path = "enchant-limiter.limits." + key;
        int cur = module.limit(null, en);
        Runnable list = () -> enchants(plugin, p, module, back, query, page);
        Dialogs.slider(p, icon(plugin, Icons.enchant(en)) + Theme.pretty(key),
                List.of("Now " + state(cur), "Vanilla max " + Theme.yellow(en.getMaxLevel()), "0 = banned"),
                "Max level", 0, 10, 1, cur < 0 ? Math.min(10, en.getMaxLevel()) : Math.min(10, cur), v -> {
                    save(plugin, path, Math.round(v));
                    list.run();
                },
                List.of(new Dialogs.Opt(Theme.green("Unlimited"), "Remove the limit", () -> {
                    save(plugin, path, -1);
                    list.run();
                })), list);
    }
}
