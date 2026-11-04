package me.smpcore.module;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Netherite ban, reference style (NoNetheritePlus): a banned netherite piece simply cannot be
 * made at the Smithing Table. Nothing else is touched: players keep, carry, wear and use any
 * netherite they already have. Only armour, tools and the spear can be banned.
 */
public final class NetheriteBanModule implements Listener {
    /** Armour first, then tools. The spear sits next to the sword so it is easy to find. */
    public static final List<Material> ARMOR = List.of(
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
    public static final List<Material> TOOLS = List.of(
            Material.NETHERITE_SWORD, Items.mat("NETHERITE_SPEAR", Material.NETHERITE_SWORD),
            Material.NETHERITE_AXE, Material.NETHERITE_PICKAXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE);

    private final SMPCore plugin;
    private final Set<Material> banned = EnumSet.noneOf(Material.class);

    public NetheriteBanModule(SMPCore plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        banned.clear();
        for (String s : plugin.getConfig().getStringList("netherite-ban.items")) {
            Material m = Material.matchMaterial(s);
            if (m != null && (ARMOR.contains(m) || TOOLS.contains(m))) banned.add(m);
        }
    }

    public Set<Material> banned() {
        return banned;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("netherite-ban.enabled", true);
    }

    public boolean isBanned(Material m) {
        return enabled() && banned.contains(m);
    }

    public void set(Material m, boolean ban) {
        setAll(List.of(m), ban);
    }

    public void setAll(Collection<Material> mats, boolean ban) {
        for (Material m : mats) {
            if (ban) banned.add(m);
            else banned.remove(m);
        }
        plugin.getConfig().set("netherite-ban.items", banned.stream().map(Enum::name).sorted().toList());
        plugin.saveConfig();
    }

    /** True when this smithing table is doing a netherite upgrade whose result is banned. */
    private boolean blocks(SmithingInventory inv, ItemStack result) {
        if (!enabled() || result == null || !banned.contains(result.getType())) return false;
        ItemStack template = inv.getInputTemplate();
        return template != null && template.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
    }

    private void warn(Player p) {
        Msg.bar(p, plugin.getConfig().getString("netherite-ban.message",
                "That netherite upgrade is <#FF5555>banned</#FF5555> on this server."));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareSmithingEvent e) {
        if (!blocks(e.getInventory(), e.getResult())) return;
        boolean any = false;
        for (HumanEntity h : e.getViewers()) {
            if (h instanceof Player p && !p.hasPermission("smpcore.bypass.itemban")) {
                any = true;
                warn(p);
            }
        }
        if (any) e.setResult(null);
    }

    /** Safety net for clicks on the result slot (shift-click, number keys, other plugins). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmith(SmithItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p.hasPermission("smpcore.bypass.itemban")) return;
        if (blocks(e.getInventory(), e.getInventory().getResult())) {
            e.setCancelled(true);
            warn(p);
        }
    }
}
