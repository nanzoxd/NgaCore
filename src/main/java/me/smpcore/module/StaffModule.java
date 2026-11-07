package me.smpcore.module;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Staff tools: /vanish (hidden from players, tab list, chat completion, server list, /list, join and leave
 * messages), /freeze and /invsee. Vanish and freeze survive relogs.
 */
public final class StaffModule implements Listener {
    private final SMPCore plugin;
    private final NamespacedKey vanishKey;
    private final NamespacedKey freezeKey;
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private BukkitTask reminder;

    public StaffModule(SMPCore plugin) {
        this.plugin = plugin;
        this.vanishKey = new NamespacedKey(plugin, "vanished");
        this.freezeKey = new NamespacedKey(plugin, "frozen");
        reminder = Bukkit.getScheduler().runTaskTimer(plugin, this::remind, 40L, 40L);
    }

    private boolean on(String path) {
        return plugin.getConfig().getBoolean(path, true);
    }

    public boolean vanishEnabled() { return on("staff.vanish.enabled"); }
    public boolean freezeEnabled() { return on("staff.freeze.enabled"); }
    public boolean invseeEnabled() { return on("staff.invsee.enabled"); }

    public boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }
    public boolean isFrozen(Player p) { return frozen.contains(p.getUniqueId()); }

    /** True when this viewer may see vanished players. */
    public boolean canSeeVanished(Player viewer) {
        return viewer.hasPermission("smpcore.vanish.see");
    }

    // ------------------------------------------------------------------ vanish

    public void setVanish(Player p, boolean on, boolean announce) {
        if (on) vanished.add(p.getUniqueId());
        else vanished.remove(p.getUniqueId());
        if (on) p.getPersistentDataContainer().set(vanishKey, PersistentDataType.BYTE, (byte) 1);
        else p.getPersistentDataContainer().remove(vanishKey);
        p.setCollidable(!on);
        p.setCanPickupItems(!on);
        p.setSleepingIgnored(on);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            if (on && !canSeeVanished(o)) o.hidePlayer(plugin, p);
            else o.showPlayer(plugin, p);
        }
        if (announce) {
            // fake leave / join message so nobody can tell
            Component name = Component.text(p.getName());
            Bukkit.broadcast(Component.translatable(on ? "multiplayer.player.left" : "multiplayer.player.joined", NamedTextColor.YELLOW, name));
        }
        Msg.send(p, on ? "You are now " + Theme.green("vanished") + ". Nobody can see you." : "You are " + Theme.red("visible") + " again.");
    }

    private void remind() {
        for (UUID id : vanished) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.bar(p, Theme.c(Theme.DIM, "You are ") + Theme.green("vanished"));
        }
        for (UUID id : frozen) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.bar(p, Theme.red(plugin.getConfig().getString("staff.freeze.bar", "You are frozen. Don't log out.")));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean wasVanished = p.getPersistentDataContainer().has(vanishKey, PersistentDataType.BYTE);
        boolean wasFrozen = p.getPersistentDataContainer().has(freezeKey, PersistentDataType.BYTE);
        // hide everyone who is vanished from the new player
        if (!canSeeVanished(p)) {
            for (UUID id : vanished) {
                Player v = Bukkit.getPlayer(id);
                if (v != null) p.hidePlayer(plugin, v);
            }
        }
        if (wasFrozen) frozen.add(p.getUniqueId());
        if (wasVanished && vanishEnabled()) {
            e.joinMessage(null);
            setVanishQuiet(p);
        }
    }

    private void setVanishQuiet(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> setVanish(p, true, false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (vanished.remove(p.getUniqueId())) {
            e.quitMessage(null);
            p.getPersistentDataContainer().set(vanishKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (frozen.remove(p.getUniqueId())) {
            p.getPersistentDataContainer().set(freezeKey, PersistentDataType.BYTE, (byte) 1);
            Component msg = Msg.body(Theme.red(p.getName()) + " logged out " + Theme.red("while frozen") + ".");
            for (Player o : Bukkit.getOnlinePlayers()) if (o.hasPermission("smpcore.freeze")) o.sendMessage(msg);
            String cmd = plugin.getConfig().getString("staff.freeze.quit-command", "");
            if (cmd != null && !cmd.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<player>", p.getName()));
        }
    }

    /** Name completion never offers a vanished player to someone who can't see them. */
    @EventHandler
    public void onTab(AsyncTabCompleteEvent e) {
        if (!(e.getSender() instanceof Player viewer) || canSeeVanished(viewer)) return;
        Set<String> hidden = new HashSet<>();
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) hidden.add(v.getName().toLowerCase());
        }
        if (hidden.isEmpty()) return;
        e.completions(e.completions().stream().filter(c -> !hidden.contains(c.suggestion().toLowerCase())).toList());
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        int hidden = 0;
        for (UUID id : vanished) if (Bukkit.getPlayer(id) != null) hidden++;
        if (hidden == 0) return;
        e.getListedPlayers().removeIf(i -> vanished.contains(i.id()));
        e.setNumPlayers(Math.max(0, e.getNumPlayers() - hidden));
    }

    /** /list shows only players the sender can see. */
    @EventHandler
    public void onList(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase().trim();
        if (!(m.equals("/list") || m.equals("/minecraft:list") || m.startsWith("/list ")) || vanished.isEmpty()) return;
        Player viewer = e.getPlayer();
        if (canSeeVanished(viewer)) return;
        e.setCancelled(true);
        List<String> names = Bukkit.getOnlinePlayers().stream().filter(p -> !vanished.contains(p.getUniqueId())).map(Player::getName).sorted().toList();
        viewer.sendMessage(Component.translatable("commands.list.players", Component.text(names.size()),
                Component.text(Bukkit.getMaxPlayers()), Component.text(String.join(", ", names))));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (vanished.contains(e.getPlayer().getUniqueId())) e.deathMessage(null);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (vanished.contains(e.getPlayer().getUniqueId())) e.message(null);
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && vanished.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlate(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && vanished.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ freeze

    public void setFreeze(Player target, boolean on) {
        if (on) {
            frozen.add(target.getUniqueId());
            target.getPersistentDataContainer().set(freezeKey, PersistentDataType.BYTE, (byte) 1);
            target.showTitle(net.kyori.adventure.title.Title.title(Msg.body(Theme.red("You are frozen")),
                    Msg.body(Theme.c(Theme.DIM, plugin.getConfig().getString("staff.freeze.subtitle", "A staff member is checking you. Don't log out."))),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(4), java.time.Duration.ofSeconds(1))));
            Msg.send(target, Theme.red("You have been frozen") + " by staff. " + Theme.c(Theme.DIM, plugin.getConfig().getString("staff.freeze.message", "Don't log out.")));
        } else {
            frozen.remove(target.getUniqueId());
            target.getPersistentDataContainer().remove(freezeKey);
            Msg.send(target, "You are " + Theme.green("unfrozen") + ".");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!frozen.contains(e.getPlayer().getUniqueId()) || !e.hasChangedPosition()) return;
        var from = e.getFrom();
        var to = e.getTo();
        // keep turning the head, but not the body
        e.setTo(from.clone().setDirection(to.getDirection()));
    }

    @EventHandler
    public void onFrozenBreak(BlockBreakEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFrozenPlace(BlockPlaceEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFrozenInteract(PlayerInteractEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFrozenDrop(PlayerDropItemEvent e) {
        if (frozen.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFrozenHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && frozen.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFrozenHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && frozen.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFrozenCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!frozen.contains(p.getUniqueId()) || p.hasPermission("smpcore.freeze")) return;
        String cmd = e.getMessage().toLowerCase().split(" ")[0].replaceFirst("^/(minecraft:)?", "");
        if (!plugin.getConfig().getStringList("staff.freeze.allowed-commands").contains(cmd)) {
            e.setCancelled(true);
            Msg.send(p, Theme.red("You can't use commands while frozen."));
        }
    }

    // ------------------------------------------------------------------ invsee

    /** Without smpcore.invsee.modify staff can only look, not take or move items. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player viewer && foreign(viewer) && !viewer.hasPermission("smpcore.invsee.modify")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInvDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player viewer && foreign(viewer) && !viewer.hasPermission("smpcore.invsee.modify")) e.setCancelled(true);
    }

    private boolean foreign(Player viewer) {
        var holder = viewer.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof Player owner && !owner.equals(viewer);
    }
}
