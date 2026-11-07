package me.smpcore.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Bans items: crafting, smithing, pickup, use, placing, chests and an inventory sweep.
 * A ban is either a whole item type (every diamond) or one exact item (a Sharpness V book, a Strength II potion,
 * an item with a custom name or model). Players can't pick it up, store it, take it out of a chest or use it.
 */
public final class ItemBanModule implements Listener {
    private final SMPCore plugin;
    private final Set<Material> banned = EnumSet.noneOf(Material.class);
    /** Custom weapons that stay allowed even when their base item is banned. */
    public final WeaponWhitelist whitelist;
    /** Exact items: signature -> a copy of the item (amount 1), in the order they were added. */
    private final Map<String, ItemStack> exact = new LinkedHashMap<>();

    /** One line of the Item Ban menu: a whole type ({@code item == null}) or one exact item. */
    public record Entry(Material material, ItemStack item) {}

    public ItemBanModule(SMPCore plugin) {
        this.plugin = plugin;
        this.whitelist = new WeaponWhitelist(plugin);
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepAll, 100L, 100L);
    }

    public void load() {
        whitelist.load();
        banned.clear();
        for (String s : plugin.getConfig().getStringList("item-ban.banned")) {
            Material m = Material.matchMaterial(s);
            if (m != null) banned.add(m);
        }
        exact.clear();
        for (Object o : plugin.getConfig().getList("item-ban.exact", List.of())) {
            if (o instanceof ItemStack it && !it.getType().isAir()) exact.put(sig(it), one(it));
        }
    }

    private static ItemStack one(ItemStack it) {
        ItemStack c = it.clone();
        c.setAmount(1);
        return c;
    }

    // ------------------------------------------------------------------ item signatures

    private static String ench(Map<Enchantment, Integer> m) {
        return new TreeMap<>(m.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getKey().getKey(), Map.Entry::getValue)))
                .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
    }

    /**
     * What makes an item "this exact item": material, enchantments, stored enchantments (books), potion type and
     * effects, custom name, item model, custom model data and text tags. Damage, amount and repair cost don't count.
     */
    public static String sig(ItemStack it) {
        StringBuilder b = new StringBuilder(it.getType().name());
        if (!it.hasItemMeta()) return b.toString();
        ItemMeta meta = it.getItemMeta();
        if (!meta.getEnchants().isEmpty()) b.append("|ench:").append(ench(meta.getEnchants()));
        if (meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants()) b.append("|stored:").append(ench(esm.getStoredEnchants()));
        if (meta instanceof PotionMeta pm) {
            if (pm.getBasePotionType() != null) b.append("|potion:").append(pm.getBasePotionType().getKey().getKey());
            if (pm.hasCustomEffects()) {
                b.append("|effects:").append(pm.getCustomEffects().stream()
                        .map((PotionEffect e) -> e.getType().getKey().getKey() + "=" + e.getAmplifier() + "/" + e.getDuration())
                        .sorted().collect(Collectors.joining(",")));
            }
        }
        String name = null;
        if (meta.hasDisplayName() && meta.displayName() != null) name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        else if (meta.hasItemName()) name = PlainTextComponentSerializer.plainText().serialize(meta.itemName());
        if (name != null) b.append("|name:").append(name);
        if (meta.hasItemModel()) b.append("|model:").append(meta.getItemModel());
        if (meta.hasCustomModelDataComponent() && !meta.getCustomModelDataComponent().getFloats().isEmpty()) {
            b.append("|cmd:").append(meta.getCustomModelDataComponent().getFloats().get(0));
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<String, String> tags = new TreeMap<>();
        for (NamespacedKey k : pdc.getKeys()) {
            String v = pdc.get(k, PersistentDataType.STRING);
            if (v != null) tags.put(k.toString(), v);
        }
        if (!tags.isEmpty()) b.append("|tags:").append(tags);
        return b.toString();
    }

    /** True when the item has details beyond its type (enchants, potion, name ...), so it can be banned exactly. */
    public static boolean special(ItemStack it) {
        return sig(it).indexOf('|') >= 0;
    }

    /**
     * Bans an item. Plain items and {@code wholeType} ban the whole item type, special items (an enchanted book, a
     * potion, a named item) are banned exactly. @return true when the exact item was banned, false for the whole type.
     */
    public boolean ban(ItemStack it, boolean wholeType) {
        if (wholeType || !special(it)) {
            banned.add(it.getType());
            save();
            return false;
        }
        exact.put(sig(it), one(it));
        save();
        return true;
    }

    public void remove(Entry e) {
        if (e.item() == null) banned.remove(e.material());
        else exact.remove(sig(e.item()));
        save();
    }

    /** Whole types first (A-Z), then the exact items in the order they were banned. */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        banned.stream().sorted(Comparator.comparing(Enum::name)).forEach(m -> out.add(new Entry(m, null)));
        exact.values().forEach(it -> out.add(new Entry(it.getType(), it)));
        return out;
    }

    public Set<Material> banned() {
        return banned;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("item-ban.enabled");
    }

    public boolean isBanned(Material m) {
        return enabled() && banned.contains(m);
    }

    /** Banned unless the item matches a custom-weapon whitelist rule. */
    public boolean isBanned(ItemStack it) {
        if (it == null || it.getType().isAir() || !enabled()) return false;
        if (banned.contains(it.getType()) && !whitelist.matches(it)) return true;
        return !exact.isEmpty() && exact.containsKey(sig(it));
    }

    public void set(Material m, boolean ban) {
        if (ban) banned.add(m);
        else banned.remove(m);
        save();
    }

    private void save() {
        plugin.getConfig().set("item-ban.banned", banned.stream().map(Enum::name).sorted().toList());
        plugin.getConfig().set("item-ban.exact", new ArrayList<>(exact.values()));
        plugin.saveConfig();
    }

    private boolean opt(String key) {
        FileConfiguration c = plugin.getConfig();
        return c.getBoolean("item-ban." + key);
    }

    private boolean bypass(Player p) {
        return p.hasPermission("smpcore.bypass.itemban");
    }

    private void warn(Player p) {
        Msg.bar(p, plugin.getConfig().getString("item-ban.message", "That item is banned."));
    }

    private void sweep(Player p) {
        if (bypass(p)) return;
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean removed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && isBanned(it)) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        if (removed) warn(p);
    }

    private void sweepAll() {
        if (!enabled() || !opt("remove-from-inventories")) return;
        for (Player p : Bukkit.getOnlinePlayers()) sweep(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (enabled() && opt("remove-from-inventories")) sweep(e.getPlayer());
    }

    @EventHandler
    public void onSmith(PrepareSmithingEvent e) {
        ItemStack r = e.getResult();
        if (r != null && isBanned(r)) e.setResult(null);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack r = e.getInventory().getResult();
        if (r != null && isBanned(r)) e.getInventory().setResult(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && !bypass(p) && isBanned(e.getItem().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (!opt("block-use") || e.getAction() == Action.PHYSICAL) return;
        ItemStack it = e.getItem();
        if (it != null && isBanned(it) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            warn(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (opt("block-placing") && isBanned(e.getItemInHand()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            warn(e.getPlayer());
        }
    }

    // ------------------------------------------------------------------ chests, hoppers, moving items around

    /** A banned item can't be put into a chest, taken out of one, or moved by clicking, shift-clicking or number keys. */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || bypass(p) || !opt("block-containers")) return;
        boolean hit = isBanned(e.getCurrentItem()) || isBanned(e.getCursor());
        if (!hit && e.getHotbarButton() >= 0) hit = isBanned(p.getInventory().getItem(e.getHotbarButton()));
        if (!hit && e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) hit = isBanned(p.getInventory().getItemInOffHand());
        if (hit) {
            e.setCancelled(true);
            warn(p);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && !bypass(p) && opt("block-containers") && isBanned(e.getOldCursor())) {
            e.setCancelled(true);
            warn(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (opt("block-containers") && isBanned(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (opt("block-containers") && isBanned(e.getItem().getItemStack())) e.setCancelled(true);
    }

    /** Ancient debris can't be mined while it is banned (used by the Netherite menu). */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.ANCIENT_DEBRIS && isBanned(Material.ANCIENT_DEBRIS)
                && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            warn(e.getPlayer());
        }
    }
}
