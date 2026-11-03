package me.smpcore.module;

import java.util.EnumSet;
import java.util.Set;
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

/** Bans items: crafting, smithing, pickup, use, placing and an inventory sweep. Also drives the Netherite menu. */
public final class ItemBanModule implements Listener {
    private final SMPCore plugin;
    private final Set<Material> banned = EnumSet.noneOf(Material.class);

    public ItemBanModule(SMPCore plugin) {
        this.plugin = plugin;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepAll, 100L, 100L);
    }

    public void load() {
        banned.clear();
        for (String s : plugin.getConfig().getStringList("item-ban.banned")) {
            Material m = Material.matchMaterial(s);
            if (m != null) banned.add(m);
        }
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

    public void set(Material m, boolean ban) {
        if (ban) banned.add(m);
        else banned.remove(m);
        save();
    }

    private void save() {
        plugin.getConfig().set("item-ban.banned", banned.stream().map(Enum::name).sorted().toList());
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
            if (it != null && banned.contains(it.getType())) {
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
        if (r != null && isBanned(r.getType())) e.setResult(null);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack r = e.getInventory().getResult();
        if (r != null && isBanned(r.getType())) e.getInventory().setResult(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && !bypass(p) && isBanned(e.getItem().getItemStack().getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (!opt("block-use") || e.getAction() == Action.PHYSICAL) return;
        ItemStack it = e.getItem();
        if (it != null && isBanned(it.getType()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            warn(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (opt("block-placing") && isBanned(e.getItemInHand().getType()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            warn(e.getPlayer());
        }
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
