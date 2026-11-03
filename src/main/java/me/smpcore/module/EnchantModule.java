package me.smpcore.module;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/** Enchantment level limits for the anvil and the enchanting table, globally or per item. */
public final class EnchantModule implements Listener {
    private static final String BYPASS = "smpcore.bypass.enchant";
    private final SMPCore plugin;

    public EnchantModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("enchant-limiter.enabled");
    }

    /** -1 = unlimited, 0 = banned, n = max level. */
    public int limit(ItemStack item, Enchantment en) {
        String ek = en.getKey().getKey();
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("enchant-limiter.item-limits");
        if (items != null && item != null) {
            for (String pat : items.getKeys(false)) {
                if (matches(pat, item.getType().name())) {
                    ConfigurationSection s = items.getConfigurationSection(pat);
                    if (s != null && s.contains(ek)) return s.getInt(ek);
                }
            }
        }
        return plugin.getConfig().getInt("enchant-limiter.limits." + ek, -1);
    }

    private static boolean matches(String pattern, String name) {
        String regex = "\\Q" + pattern.toUpperCase().replace("*", "\\E.*\\Q") + "\\E";
        return Pattern.matches(regex, name);
    }

    private Map<Enchantment, Integer> enchantsOf(ItemStack item) {
        if (item.getItemMeta() instanceof EnchantmentStorageMeta esm) return esm.getStoredEnchants();
        return item.getEnchantments();
    }

    private boolean over(ItemStack item, Enchantment en, int level) {
        int lim = limit(item, en);
        return lim == 0 || (lim > 0 && level > lim);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (!enabled()) return;
        ItemStack r = e.getResult();
        if (r == null) return;
        for (HumanEntity v : e.getViewers()) if (v.hasPermission(BYPASS)) return;
        for (Map.Entry<Enchantment, Integer> en : enchantsOf(r).entrySet()) {
            if (over(r, en.getKey(), en.getValue())) {
                e.setResult(null);
                for (HumanEntity v : e.getViewers()) {
                    if (v instanceof org.bukkit.entity.Player p) {
                        Msg.bar(p, plugin.getConfig().getString("enchant-limiter.message", "Over the limit."));
                    }
                }
                return;
            }
        }
    }

    /** Enchanting table: offending enchantments are removed or lowered instead of blocking the whole roll. */
    @EventHandler
    public void onTable(EnchantItemEvent e) {
        if (!enabled() || e.getEnchanter().hasPermission(BYPASS)) return;
        Map<Enchantment, Integer> add = e.getEnchantsToAdd();
        for (Enchantment en : new ArrayList<>(add.keySet())) {
            int lim = limit(e.getItem(), en);
            if (lim == 0) add.remove(en);
            else if (lim > 0 && add.get(en) > lim) add.put(en, lim);
        }
        if (add.isEmpty()) e.setCancelled(true);
    }
}
