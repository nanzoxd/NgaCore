package me.smpcore.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Small switchable features. Every switch lives under "tweaks." in config.yml and in the /smp menus. */
public final class TweaksModule implements Listener, CommandExecutor {
    private final SMPCore plugin;
    private final Map<UUID, Long> lastMove = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();
    private final Set<UUID> pvpOff = ConcurrentHashMap.newKeySet();

    public TweaksModule(SMPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean hide = on("hide-locator-bar");
            for (World w : Bukkit.getWorlds()) {
                if (!Boolean.valueOf(!hide).equals(w.getGameRuleValue(GameRule.LOCATOR_BAR))) {
                    w.setGameRule(GameRule.LOCATOR_BAR, !hide);
                }
            }
        }, 40L, 100L);
    }

    private boolean on(String key) {
        return plugin.getConfig().getBoolean("tweaks." + key);
    }

    private boolean bypass(Player p) {
        return p.hasPermission("smpcore.bypass.tweaks");
    }

    // ---------------------------------------------------------------- /pvp

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        boolean nowOff = pvpOff.add(p.getUniqueId());
        if (!nowOff) pvpOff.remove(p.getUniqueId());
        Msg.send(p, "pvp " + (nowOff ? Theme.red("disabled") : Theme.green("enabled")));
        return true;
    }

    // ---------------------------------------------------------------- gameplay

    @EventHandler
    public void onBed(PlayerBedEnterEvent e) {
        if (!on("one-player-sleep") || e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !p.isSleeping()) return;
            World w = p.getWorld();
            w.setTime(0L);
            w.setStorm(false);
            w.setThundering(false);
        }, 101L);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (on("anvil-no-limit")) e.getView().setMaximumRepairCost(32767);
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent e) {
        if (on("armor-stand-arms") && e.getEntity() instanceof ArmorStand as) as.setArms(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        if (on("immortal-death-drops")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Entity en : v.getWorld().getNearbyEntities(v.getLocation(), 3, 3, 3)) {
                    if (en instanceof Item it) {
                        it.setUnlimitedLifetime(true);
                        it.setInvulnerable(true);
                    }
                }
            });
        }
        Player k = v.getKiller();
        if (on("kill-sound") && k != null && !k.equals(v)) {
            Msg.sound(k, plugin.getConfig().getString("tweaks.kill-sound-key", "entity.player.levelup"), 1f, 1.2f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (on("no-falling-blocks") && e.getBlock().getType().hasGravity()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (on("no-fire-spread") && e.getSource().getType() == Material.FIRE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (on("no-fire-spread")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (on("no-fire-spread") && e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (on("no-explosion-grief")) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (on("no-explosion-grief")) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent e) {
        if (!on("totem-keep-effects") || !(e.getEntity() instanceof Player p)) return;
        Collection<PotionEffect> saved = new ArrayList<>(p.getActivePotionEffects());
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (PotionEffect pe : saved) p.addPotionEffect(pe);
        });
    }

    // ---------------------------------------------------------------- protection

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.hasChangedPosition()) lastMove.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        lastMove.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    private boolean afk(Player p) {
        long limit = (long) (plugin.getConfig().getDouble("tweaks.afk-seconds", 120) * 1000);
        return System.currentTimeMillis() - lastMove.getOrDefault(p.getUniqueId(), System.currentTimeMillis()) > limit;
    }

    private static boolean naked(Player p) {
        for (ItemStack a : p.getInventory().getArmorContents()) if (a != null && !a.getType().isAir()) return false;
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (on("afk-protection") && e.getEntity() instanceof Player p && afk(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        if (!(d instanceof Player a) || a.equals(v)) return;
        if (on("pvp-off") || pvpOff.contains(a.getUniqueId()) || pvpOff.contains(v.getUniqueId())) {
            e.setCancelled(true);
            Msg.bar(a, "pvp is " + Theme.red("disabled"));
            return;
        }
        if (on("naked-protection") && naked(v)) {
            e.setCancelled(true);
            Msg.bar(a, "That player has " + Theme.yellow("no armor") + " and is " + Theme.red("protected") + ".");
            return;
        }
        long now = System.currentTimeMillis();
        lastCombat.put(a.getUniqueId(), now);
        lastCombat.put(v.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Long t = lastCombat.remove(p.getUniqueId());
        lastMove.remove(p.getUniqueId());
        long window = (long) (plugin.getConfig().getDouble("tweaks.combat-seconds", 15) * 1000);
        if (on("combat-log") && t != null && System.currentTimeMillis() - t < window && !bypass(p)) {
            p.setHealth(0.0);
            Bukkit.broadcast(Msg.body(Theme.red(p.getName()) + " logged out in combat."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        ItemStack c = e.getConsumable();
        if (on("ban-tipped-arrows") && c != null && c.getType() == Material.TIPPED_ARROW) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player p) Msg.bar(p, "Tipped arrows are " + Theme.red("banned") + ".");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (on("disable-vaults") && e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.VAULT && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (on("block-placing") && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (on("block-breaking") && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent e) {
        if (!on("maintenance")) return;
        Player p = e.getPlayer();
        if (p.isOp() || p.hasPermission("smpcore.maintenance.bypass")) return;
        e.disallow(PlayerLoginEvent.Result.KICK_OTHER, Theme.parse(plugin.getConfig().getString("tweaks.maintenance-message",
                Theme.GRADIENT + "Maintenance</gradient>\n<#abc4d1>The server is closed right now. Come back soon.")));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreature(CreatureSpawnEvent e) {
        if (!on("mob-disabler")) return;
        var r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.COMMAND || r == CreatureSpawnEvent.SpawnReason.CUSTOM
                || r == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        List<String> types = plugin.getConfig().getStringList("tweaks.mob-disabler-types");
        for (String t : types) if (t.equalsIgnoreCase(e.getEntityType().name())) e.setCancelled(true);
    }
}
