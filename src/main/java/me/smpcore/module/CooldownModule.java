package me.smpcore.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.smpcore.SMPCore;
import me.smpcore.util.Items;
import me.smpcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Item cooldowns. They use the vanilla item cooldown overlay, so players see the timer on the item. */
public final class CooldownModule implements Listener {
    public record Cd(String key, Material icon, String label) {}

    public static final List<Cd> ITEMS = List.of(
            new Cd("mace", Material.MACE, "Mace"),
            new Cd("spear", Items.mat("IRON_SPEAR", Material.TRIDENT), "Spear Lunge"),
            new Cd("wind_charge", Material.WIND_CHARGE, "Wind Charge"),
            new Cd("ender_pearl", Material.ENDER_PEARL, "Ender Pearl"),
            new Cd("golden_apple", Material.GOLDEN_APPLE, "Golden Apple"),
            new Cd("enchanted_golden_apple", Material.ENCHANTED_GOLDEN_APPLE, "Enchanted Golden Apple"),
            new Cd("chorus_fruit", Material.CHORUS_FRUIT, "Chorus Fruit"),
            new Cd("firework_rocket", Material.FIREWORK_ROCKET, "Elytra Rocket"),
            new Cd("trident", Material.TRIDENT, "Trident"));

    private final SMPCore plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public CooldownModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    private boolean active(String key) {
        FileConfiguration c = plugin.getConfig();
        return c.getBoolean("cooldowns.enabled") && c.getBoolean("cooldowns.items." + key + ".enabled");
    }

    private void start(Player p, String key, Material... mats) {
        if (!active(key) || p.hasPermission("smpcore.bypass.cooldown")) return;
        int ticks = (int) Math.round(plugin.getConfig().getDouble("cooldowns.items." + key + ".seconds") * 20);
        if (ticks <= 0) return;
        for (Material m : mats) p.setCooldown(m, ticks);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private String keyFor(Material m) {
        if (Items.isSpear(m)) return "spear";
        return switch (m) {
            case MACE -> "mace";
            case ENDER_PEARL -> "ender_pearl";
            case WIND_CHARGE -> "wind_charge";
            case GOLDEN_APPLE -> "golden_apple";
            case ENCHANTED_GOLDEN_APPLE -> "enchanted_golden_apple";
            case CHORUS_FRUIT -> "chorus_fruit";
            case FIREWORK_ROCKET -> "firework_rocket";
            case TRIDENT -> "trident";
            default -> null;
        };
    }

    private void notice(Player p, Material m, String key) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 700) return;
        lastNotice.put(p.getUniqueId(), now);
        double secs = p.getCooldown(m) / 20.0;
        String label = ITEMS.stream().filter(c -> c.key().equals(key)).map(Cd::label).findFirst().orElse(key);
        String msg = plugin.getConfig().getString("cooldowns.message", "<item> <time>s")
                .replace("<item>", label)
                .replace("<time>", String.format("%.1f", secs));
        Msg.bar(p, msg);
        Msg.sound(p, plugin.getConfig().getString("cooldowns.sound", "entity.villager.no"), 0.6f, 1.0f);
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (e.getEntity() instanceof EnderPearl) start(p, "ender_pearl", Material.ENDER_PEARL);
        else if (e.getEntity() instanceof WindCharge) start(p, "wind_charge", Material.WIND_CHARGE);
        else if (e.getEntity() instanceof Trident) start(p, "trident", Material.TRIDENT);
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent e) {
        start(e.getPlayer(), "trident", Material.TRIDENT);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Material m = e.getItem().getType();
        if (m == Material.GOLDEN_APPLE) start(e.getPlayer(), "golden_apple", m);
        else if (m == Material.ENCHANTED_GOLDEN_APPLE) start(e.getPlayer(), "enchanted_golden_apple", m);
        else if (m == Material.CHORUS_FRUIT) start(e.getPlayer(), "chorus_fruit", m);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        Player p = e.getPlayer();
        Material m = it.getType();
        String key = keyFor(m);
        if (key == null || !active(key)) return;
        if (p.hasCooldown(m)) {
            notice(p, m, key);
            return;
        }
        // set one tick later so the use that triggered it is not blocked by its own cooldown
        if (m == Material.FIREWORK_ROCKET && p.isGliding()) {
            later(() -> start(p, "firework_rocket", m));
        } else if (Items.isSpear(m)) {
            later(() -> start(p, "spear", Items.spears()));
        }
    }

    /** Mace: a hit on cooldown is cancelled. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceBlock(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getInventory().getItemInMainHand().getType() != Material.MACE || !active("mace")) return;
        if (p.hasCooldown(Material.MACE)) {
            e.setCancelled(true);
            notice(p, Material.MACE, "mace");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getInventory().getItemInMainHand().getType() == Material.MACE) start(p, "mace", Material.MACE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastNotice.remove(e.getPlayer().getUniqueId());
    }

}
