package me.smpcore.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Item limits: a player can carry only N of an item (for example 2 enchanted golden apples). Anything over the
 * limit is dropped on the ground and can't be picked back up while they are at the limit. A rule is a whole item
 * type or one exact item (same matching as the item ban: an enchanted book, a potion, a named item).
 */
public final class ItemLimitModule implements Listener {
    /** @param sig exact-item signature, used when {@code whole} is false */
    public record Rule(ItemStack item, boolean whole, int limit, String sig) {
        public Material material() {
            return item.getType();
        }

        public Rule withLimit(int l) {
            return new Rule(item, whole, Math.max(0, Math.min(2304, l)), sig);
        }

        /** The name shown to players and in the menu. */
        public String label() {
            if (item.hasItemMeta()) {
                ItemMeta m = item.getItemMeta();
                if (m.hasDisplayName() && m.displayName() != null) return PlainTextComponentSerializer.plainText().serialize(m.displayName());
                if (m.hasItemName()) return PlainTextComponentSerializer.plainText().serialize(m.itemName());
            }
            return Theme.pretty(item.getType().name());
        }
    }

    private final SMPCore plugin;
    private final List<Rule> rules = new ArrayList<>();
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public ItemLimitModule(SMPCore plugin) {
        this.plugin = plugin;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepAll, 40L, 40L);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("item-limit.enabled", false);
    }

    public int defaultLimit() {
        return Math.max(0, plugin.getConfig().getInt("item-limit.default-limit", 2));
    }

    public List<Rule> rules() {
        return rules;
    }

    // ------------------------------------------------------------------ rules

    private static Rule make(ItemStack it, boolean whole, int limit) {
        ItemStack one = it.clone();
        one.setAmount(1);
        return new Rule(one, whole, limit, ItemBanModule.sig(it));
    }

    public void load() {
        rules.clear();
        for (Object o : plugin.getConfig().getList("item-limit.rules", List.of())) {
            if (o instanceof Map<?, ?> m && m.get("item") instanceof ItemStack it && !it.getType().isAir()) {
                int limit = m.get("limit") instanceof Number n ? Math.max(0, n.intValue()) : 2;
                rules.add(make(it, Boolean.TRUE.equals(m.get("whole")), limit));
            }
        }
    }

    private void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Rule r : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", r.item());
            m.put("whole", r.whole());
            m.put("limit", r.limit());
            out.add(m);
        }
        plugin.getConfig().set("item-limit.rules", out);
        plugin.saveConfig();
    }

    /**
     * Adds a limit for the item. Plain items and {@code wholeType} limit the whole item type, special items (enchanted
     * book, potion, named item) are limited exactly. @return the rule (an existing one when it was already there)
     */
    public Rule add(ItemStack it, boolean wholeType, int limit) {
        boolean whole = wholeType || !ItemBanModule.special(it);
        Rule r = make(it, whole, limit);
        for (Rule e : rules) {
            if (e.whole() == r.whole() && e.material() == r.material() && (r.whole() || e.sig().equals(r.sig()))) return e;
        }
        rules.add(r);
        save();
        Bukkit.getScheduler().runTask(plugin, this::sweepAll);
        return r;
    }

    public void setLimit(Rule r, int limit) {
        int i = rules.indexOf(r);
        if (i < 0) return;
        rules.set(i, r.withLimit(limit));
        save();
        Bukkit.getScheduler().runTask(plugin, this::sweepAll);
    }

    public void remove(Rule r) {
        rules.remove(r);
        save();
    }

    // ------------------------------------------------------------------ counting and enforcing

    private boolean matches(Rule r, ItemStack s) {
        if (s == null || s.getType() != r.material()) return false;
        return r.whole() || r.sig().equals(ItemBanModule.sig(s));
    }

    public int count(Player p, Rule r) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (matches(r, s)) n += s.getAmount();
        }
        return n;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("smpcore.bypass.itemlimit");
    }

    private void notice(Player p, Rule r, boolean dropped) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 2500) return;
        lastNotice.put(p.getUniqueId(), now);
        String text = plugin.getConfig().getString("item-limit.message",
                "You can only carry <limit> <item>.").replace("<limit>", String.valueOf(r.limit())).replace("<item>", Theme.esc(r.label()));
        Msg.bar(p, text + (dropped ? " " + Theme.c(Theme.DIM, "The rest was dropped.") : ""));
        Msg.sound(p, "entity.item.pickup", 0.6f, 0.6f);
    }

    /** Drops everything over the limit: from the end of the inventory first, so the hotbar stays. */
    public void enforce(Player p) {
        if (!enabled() || rules.isEmpty() || bypass(p)) return;
        PlayerInventory inv = p.getInventory();
        for (Rule r : rules) {
            int excess = count(p, r) - r.limit();
            if (excess <= 0) continue;
            List<Integer> order = new ArrayList<>();
            for (int i = 35; i >= 0; i--) order.add(i);
            order.add(40);   // off hand
            for (int slot : order) {
                if (excess <= 0) break;
                ItemStack s = inv.getItem(slot);
                if (!matches(r, s)) continue;
                int take = Math.min(excess, s.getAmount());
                ItemStack drop = s.clone();
                drop.setAmount(take);
                if (take >= s.getAmount()) {
                    inv.setItem(slot, null);
                } else {
                    s.setAmount(s.getAmount() - take);
                    inv.setItem(slot, s);
                }
                Item dropped = p.getWorld().dropItem(p.getLocation(), drop);
                dropped.setPickupDelay(80);
                excess -= take;
            }
            notice(p, r, true);
        }
    }

    private void sweepAll() {
        if (!enabled()) return;
        for (Player p : Bukkit.getOnlinePlayers()) enforce(p);
    }

    private void later(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) enforce(p);
        });
    }

    /** Picking up fills the free room only; what doesn't fit stays on the ground. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !enabled() || bypass(p)) return;
        ItemStack st = e.getItem().getItemStack();
        for (Rule r : rules) {
            if (!matches(r, st)) continue;
            int room = r.limit() - count(p, r);
            if (room <= 0) {
                e.setCancelled(true);
                notice(p, r, false);
                return;
            }
            if (st.getAmount() > room) {
                e.setCancelled(true);
                ItemStack part = st.clone();
                part.setAmount(room);
                int left = p.getInventory().addItem(part).values().stream().mapToInt(ItemStack::getAmount).sum();
                ItemStack rest = st.clone();
                rest.setAmount(st.getAmount() - (room - left));
                e.getItem().setItemStack(rest);
                notice(p, r, false);
                return;
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && enabled()) later(p);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && enabled()) later(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (enabled()) later(e.getPlayer());
    }
}
