package me.smpcore.module;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** More switches ported from the SMPUtils feature list. Config keys live under "tweaks." */
public final class MoreTweaksModule implements Listener {
    private final SMPCore plugin;

    public MoreTweaksModule(SMPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickVoid, 20L, 10L);
    }

    private boolean on(String key) {
        return plugin.getConfig().getBoolean("tweaks." + key);
    }

    private static boolean bypass(Player p) {
        return p.hasPermission("smpcore.bypass.tweaks");
    }

    private void tickVoid() {
        if (!on("void-protection")) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            Location l = p.getLocation();
            if (l.getY() >= w.getMinHeight() - 16) continue;
            Location to = w.getEnvironment() == World.Environment.NORMAL
                    ? w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5) : w.getSpawnLocation();
            p.setFallDistance(0f);
            p.teleport(to);
        }
    }

    // ---------------------------------------------------------------- combat rules

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (on("ban-crystal-pvp") && e.getEntity() instanceof Player && e.getDamager() instanceof EnderCrystal) {
            e.setCancelled(true);
        } else if (on("ban-villager-killing") && e.getEntity() instanceof Villager && e.getDamager() instanceof Player p
                && !bypass(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerInteractEvent e) {
        if (!on("ban-bed-bombing") || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType().name().endsWith("_BED")
                && e.getClickedBlock().getWorld().getEnvironment() != World.Environment.NORMAL) {
            e.setCancelled(true);
            Msg.bar(e.getPlayer(), "Bed bombing is " + Theme.red("banned") + ".");
        }
    }

    /** Ender Pearl Ban: the pearl can't be thrown, but players may still carry it. The item is never touched. */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onPearlUse(PlayerInteractEvent e) {
        if (!on("ban-pearl")) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.ENDER_PEARL || bypass(e.getPlayer())) return;
        // only deny the item; opening chests, doors etc. still works
        e.setUseItemInHand(Event.Result.DENY);
        Msg.bar(e.getPlayer(), pearlMsg());
    }

    /** Backup for pearls launched some other way (dispensers aside): refuse the launch. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (on("ban-pearl") && e.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p
                && !bypass(p)) {
            e.setCancelled(true);
            Msg.bar(p, pearlMsg());
        }
    }

    private static String pearlMsg() {
        return "Ender pearls are " + Theme.red("banned") + ". " + Theme.c(Theme.DIM, "You can still keep them.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (on("ban-draining") && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (on("ban-spawner-mining") && e.getBlock().getType() == Material.SPAWNER && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (on("ban-explosives")) {
            e.setCancelled(true);
        } else if (on("no-mob-griefing") && (e.getEntity() instanceof Creeper || e.getEntity() instanceof Fireball)) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (on("ban-explosives")) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- spawns and drops

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        if (on("ban-minecart") && e.getEntityType() == EntityType.TNT_MINECART) {
            e.setCancelled(true);
            return;
        }
        if (on("xp-clumps") && e.getEntity() instanceof ExperienceOrb orb) {
            for (ExperienceOrb o : orb.getWorld().getNearbyEntitiesByType(ExperienceOrb.class, orb.getLocation(), 2)) {
                if (o != orb && !o.isDead()) {
                    o.setExperience(o.getExperience() + orb.getExperience());
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreature(CreatureSpawnEvent e) {
        if (on("disable-phantoms") && e.getEntityType() == EntityType.PHANTOM) e.setCancelled(true);
        else if (on("no-mob-spawning") && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) {
        if (on("no-mob-griefing") && !(e.getEntity() instanceof Player)
                && e.getEntityType() != EntityType.FALLING_BLOCK) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent e) {
        if (on("apple-drops") && e.getBlock().getType() == Material.OAK_LEAVES
                && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("tweaks.apple-chance", 0.005)) {
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(Material.APPLE));
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        // Evoker Totem Drop: on (default) = vanilla, off = evokers drop no totem of undying
        if (e.getEntity() instanceof Evoker && !plugin.getConfig().getBoolean("tweaks.evoker-totem-drop", true)) {
            e.getDrops().removeIf(i -> i != null && i.getType() == Material.TOTEM_OF_UNDYING);
        }
        if (on("heavy-core-drop") && e.getEntity() instanceof Ravager
                && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("tweaks.heavy-core-chance", 0.02)) {
            e.getDrops().add(new ItemStack(Material.HEAVY_CORE));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!on("player-head-drop")
                || ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("tweaks.head-chance", 1.0)) return;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(e.getEntity());
            head.setItemMeta(sm);
        }
        e.getDrops().add(head);
    }

    // ---------------------------------------------------------------- misc

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!on("cobweb-decay") || e.getBlockPlaced().getType() != Material.COBWEB) return;
        var b = e.getBlockPlaced();
        long ticks = (long) (plugin.getConfig().getDouble("tweaks.cobweb-seconds", 10) * 20);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
        }, ticks);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent e) {
        if (!on("book-limit")) return;
        BookMeta m = e.getNewBookMeta();
        int maxPages = plugin.getConfig().getInt("tweaks.max-book-pages", 50);
        int maxLen = plugin.getConfig().getInt("tweaks.max-page-length", 256);
        List<String> pages = m.getPages();
        boolean tooLong = pages.stream().anyMatch(s -> s.length() > maxLen);
        if (pages.size() > maxPages || tooLong) {
            e.setCancelled(true);
            Msg.send(e.getPlayer(), "That book is " + Theme.red("too big") + ". Max " + Theme.yellow(maxPages) + " pages of "
                    + Theme.yellow(maxLen) + " characters.");
        }
    }
}
