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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/** Enchantment level limits for the anvil and the enchanting table, globally or per item. */
public final class EnchantModule implements Listener {
    private static final String BYPASS = "smpcore.bypass.enchant";
    private final SMPCore plugin;

    public EnchantModule(SMPCore plugin) {
        this.plugin = plugin;
        // items that already have too-strong enchantments (chests, /enchant, loot, trades) get cleaned up as well
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepAll, 100L, 100L);
    }

    private boolean clean() {
        return enabled() && plugin.getConfig().getBoolean("enchant-limiter.clean-existing", true);
    }

    /**
     * Lowers enchantments over the limit to the limit and removes banned ones (limit 0), on the item itself
     * (enchanted books: their stored enchantments). @return true when something changed
     */
    public boolean sanitize(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        boolean changed = false;
        if (item.getItemMeta() instanceof EnchantmentStorageMeta esm) {
            for (Map.Entry<Enchantment, Integer> en : new ArrayList<>(esm.getStoredEnchants().entrySet())) {
                int lim = limit(item, en.getKey());
                if (lim == 0) {
                    esm.removeStoredEnchant(en.getKey());
                    changed = true;
                } else if (lim > 0 && en.getValue() > lim) {
                    esm.addStoredEnchant(en.getKey(), lim, true);
                    changed = true;
                }
            }
            if (changed) item.setItemMeta(esm);
            return changed;
        }
        for (Map.Entry<Enchantment, Integer> en : new ArrayList<>(item.getEnchantments().entrySet())) {
            int lim = limit(item, en.getKey());
            if (lim == 0) {
                item.removeEnchantment(en.getKey());
                changed = true;
            } else if (lim > 0 && en.getValue() > lim) {
                item.addUnsafeEnchantment(en.getKey(), lim);
                changed = true;
            }
        }
        return changed;
    }

    /** @return true when any item in the inventory was changed */
    public boolean sanitize(Inventory inv) {
        boolean any = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (sanitize(it)) {
                inv.setItem(i, it);
                any = true;
            }
        }
        return any;
    }

    private void sanitizePlayer(Player p) {
        if (!clean() || p.hasPermission(BYPASS)) return;
        boolean a = sanitize(p.getInventory());
        boolean b = sanitize(p.getEnderChest());
        if (a || b) Msg.bar(p, plugin.getConfig().getString("enchant-limiter.clean-message", "Enchantments over the limit were lowered."));
    }

    private void sweepAll() {
        if (!clean()) return;
        for (Player p : Bukkit.getOnlinePlayers()) sanitizePlayer(p);
    }

    /** Chests, barrels, shulker boxes, hoppers, loot chests: cleaned the moment someone opens them. */
    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!clean() || e.getPlayer().hasPermission(BYPASS)) return;
        Inventory top = e.getInventory();
        if (top.getHolder() instanceof Player) return;   // a player's own inventory is handled by the sweep
        sanitize(top);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!clean() || !(e.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) sanitizePlayer(p);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (clean() && e.getPlayer() instanceof Player p) sanitizePlayer(p);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!clean() || !(e.getEntity() instanceof Player p) || p.hasPermission(BYPASS)) return;
        ItemStack st = e.getItem().getItemStack();
        if (sanitize(st)) e.getItem().setItemStack(st);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (clean()) Bukkit.getScheduler().runTask(plugin, () -> sanitizePlayer(e.getPlayer()));
    }

    /** /enchant (by a player or the console) is followed by a clean-up of everyone's items a moment later. */
    @EventHandler
    public void onEnchantCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase();
        if (clean() && (m.startsWith("/enchant ") || m.startsWith("/minecraft:enchant "))) Bukkit.getScheduler().runTaskLater(plugin, this::sweepAll, 2L);
    }

    @EventHandler
    public void onEnchantConsole(ServerCommandEvent e) {
        String m = e.getCommand().toLowerCase();
        if (clean() && (m.startsWith("enchant ") || m.startsWith("minecraft:enchant "))) Bukkit.getScheduler().runTaskLater(plugin, this::sweepAll, 2L);
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
