package me.smpcore.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Chat moderation: cooldown, duplicates, filters, mentions, private messages, join/leave and death messages. */
public final class ChatModule implements Listener {
    private record Hit(UUID attacker, String name, long time) {}

    private static final Pattern MENTION = Pattern.compile("@(\\w{1,16})");
    private static final Pattern LINK = Pattern.compile(
            "(?i)(https?://\\S+|www\\.\\S+|\\b[a-z0-9-]+\\.(com|net|org|gg|io|me|xyz|tk|ru|co)\\b)");

    private final SMPCore plugin;
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMsg = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastPm = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mentioned = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> replyTo = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> combat = new ConcurrentHashMap<>();
    private final Map<UUID, Hit> lastHit = new ConcurrentHashMap<>();

    public ChatModule(SMPCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 6000L, 6000L);
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    private boolean on(String path) {
        return c().getBoolean("chat.enabled") && c().getBoolean(path);
    }

    private String text(String key) {
        return c().getString("chat.messages." + key, "");
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        long window = (long) (c().getDouble("chat.mentions.window-seconds", 60) * 1000);
        mentioned.values().removeIf(t -> now - t > window);
        lastSent.values().removeIf(t -> now - t > 60_000);
    }

    /** Message + blocked sound, safe from any thread. */
    private void reject(Player p, String mm) {
        Msg.send(p, mm);
        Bukkit.getScheduler().runTask(plugin, () -> Msg.cfgSound(p, "chat.sounds.blocked"));
    }

    // ---------------------------------------------------------------- public chat

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!c().getBoolean("chat.enabled")) return;
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        Component msg = e.message();
        long now = System.currentTimeMillis();

        if (!p.hasPermission("smpcore.chat.bypass")) {
            if (c().getBoolean("chat.cooldown.enabled")) {
                long cd = (long) (c().getDouble("chat.cooldown.seconds", 2) * 1000);
                Long last = lastSent.get(id);
                if (last != null && now - last < cd) {
                    e.setCancelled(true);
                    reject(p, text("cooldown").replace("<time>", String.format("%.1f", (cd - (now - last)) / 1000.0)));
                    return;
                }
            }
            if (c().getBoolean("chat.duplicate.enabled")) {
                String prev = lastMsg.get(id);
                if (prev != null && prev.equalsIgnoreCase(plain.trim())) {
                    e.setCancelled(true);
                    reject(p, text("duplicate"));
                    return;
                }
            }
            if (c().getBoolean("chat.links.enabled") && LINK.matcher(plain).find()) {
                e.setCancelled(true);
                reject(p, text("links"));
                return;
            }
            if (c().getBoolean("chat.blocked-words.enabled")) {
                String censored = plain;
                boolean hit = false;
                for (String w : c().getStringList("chat.blocked-words.words")) {
                    if (w.isBlank()) continue;
                    Pattern pat = Pattern.compile(Pattern.quote(w), Pattern.CASE_INSENSITIVE);
                    if (pat.matcher(censored).find()) {
                        hit = true;
                        censored = pat.matcher(censored).replaceAll("*".repeat(w.length()));
                    }
                }
                if (hit) {
                    if ("CANCEL".equalsIgnoreCase(c().getString("chat.blocked-words.action", "CENSOR"))) {
                        e.setCancelled(true);
                        reject(p, text("blocked-word"));
                        return;
                    }
                    plain = censored;
                    msg = Component.text(plain);
                }
            }
            if (c().getBoolean("chat.caps.enabled")) {
                int letters = 0, upper = 0;
                for (char ch : plain.toCharArray()) {
                    if (Character.isLetter(ch)) {
                        letters++;
                        if (Character.isUpperCase(ch)) upper++;
                    }
                }
                if (letters >= c().getInt("chat.caps.min-length", 8)
                        && upper * 100 / letters > c().getInt("chat.caps.max-percent", 70)) {
                    plain = plain.toLowerCase();
                    msg = Component.text(plain);
                }
            }
        }

        lastSent.put(id, now);
        lastMsg.put(id, plain.trim());

        if (c().getBoolean("chat.mentions.enabled")) {
            Set<Player> notify = new LinkedHashSet<>();
            long window = (long) (c().getDouble("chat.mentions.window-seconds", 60) * 1000);
            TextColor blue = TextColor.fromHexString(Theme.BLUE);
            msg = msg.replaceText(TextReplacementConfig.builder().match(MENTION).replacement((mr, b) -> {
                Player t = null;
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (o.getName().equalsIgnoreCase(mr.group(1))) t = o;
                }
                if (t == null) return b;
                Long last = mentioned.get(t.getUniqueId());
                if (!t.equals(p) && (last == null || System.currentTimeMillis() - last > window)) {
                    notify.add(t);
                    mentioned.put(t.getUniqueId(), System.currentTimeMillis());
                }
                return b.content("@" + t.getName()).color(blue).decorate(TextDecoration.BOLD);
            }).build());
            if (!notify.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player t : notify) if (t.isOnline()) Msg.cfgSound(t, "chat.sounds.mention");
                });
            }
        }
        e.message(msg);

        if (c().getBoolean("chat.format.enabled", true)) {
            String fmt = c().getString("chat.format.text", "<player> › <message>");
            e.renderer((source, name, message, viewer) -> Theme.MM.deserialize(fmt,
                    TagResolver.resolver(Placeholder.component("player", name), Placeholder.component("message", message))));
        }
    }

    // ---------------------------------------------------------------- private messages

    public void sendPrivate(Player from, Player to, String message) {
        if (!c().getBoolean("chat.private-messages.enabled", true)) return;
        String prev = lastPm.get(from.getUniqueId());
        if (prev != null && prev.equalsIgnoreCase(message) && !from.hasPermission("smpcore.chat.bypass")) {
            Msg.send(from, text("duplicate"));
            Msg.cfgSound(from, "chat.sounds.blocked");
            return;
        }
        lastPm.put(from.getUniqueId(), message);
        from.sendMessage(Msg.body(text("pm-sent")
                .replace("<target>", to.getName()).replace("<message>", Theme.esc(message))));
        to.sendMessage(Msg.body(text("pm-received")
                .replace("<sender>", from.getName()).replace("<message>", Theme.esc(message))));
        Msg.cfgSound(to, "chat.sounds.private-message");
        Msg.cfgSound(from, "chat.sounds.pm-sent");
        replyTo.put(from.getUniqueId(), to.getUniqueId());
        replyTo.put(to.getUniqueId(), from.getUniqueId());
    }

    public Player replyTarget(Player p) {
        UUID id = replyTo.get(p.getUniqueId());
        return id == null ? null : Bukkit.getPlayer(id);
    }

    public String message(String key) {
        return text(key);
    }

    // ---------------------------------------------------------------- join / leave / advancements

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        combat.remove(e.getPlayer().getUniqueId());
        if (on("chat.join-leave.enabled")) {
            e.joinMessage(Theme.MM.deserialize(text("join"), Placeholder.unparsed("player", e.getPlayer().getName())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastSent.remove(id);
        lastMsg.remove(id);
        lastPm.remove(id);
        mentioned.remove(id);
        replyTo.remove(id);
        combat.remove(id);
        lastHit.remove(id);
        if (on("chat.join-leave.enabled")) {
            e.quitMessage(Theme.MM.deserialize(text("leave"), Placeholder.unparsed("player", e.getPlayer().getName())));
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (on("chat.hide-advancements")) e.message(null);
    }

    // ---------------------------------------------------------------- combat stats + death messages

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        double dmg = e.getFinalDamage();
        if (e.getEntity() instanceof Player victim) {
            combat.computeIfAbsent(victim.getUniqueId(), k -> new double[2])[1] += dmg;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
            if (d instanceof Player attacker && !attacker.equals(e.getEntity())) {
                combat.computeIfAbsent(attacker.getUniqueId(), k -> new double[2])[0] += dmg;
                if (e.getEntity() instanceof Player victim) {
                    lastHit.put(victim.getUniqueId(), new Hit(attacker.getUniqueId(), attacker.getName(), System.currentTimeMillis()));
                }
            }
        }
    }

    private Component stats(Player p) {
        double[] a = combat.getOrDefault(p.getUniqueId(), new double[2]);
        return Theme.MM.deserialize("<#cdd5e3>Combat stats\n"
                + "<#9babc7>Ping <" + Theme.BLUE + ">" + p.getPing() + "ms\n"
                + "<#9babc7>Damage dealt <" + Theme.GREEN + ">" + hearts(a[0]) + " ❤\n"
                + "<#9babc7>Damage taken <" + Theme.RED + ">" + hearts(a[1]) + " ❤");
    }

    private static String hearts(double hp) {
        return String.valueOf(Math.round(hp / 2 * 10) / 10.0);
    }

    private Component name(Player p, String hex) {
        return Component.text(p.getName(), TextColor.fromHexString(hex)).hoverEvent(HoverEvent.showText(stats(p)));
    }

    private static Component gray(String s) {
        return Component.text(s, TextColor.fromHexString(Theme.TEXT));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        if (!on("chat.death-messages.enabled")) {
            combat.remove(v.getUniqueId());
            return;
        }
        long window = (long) (c().getDouble("chat.death-messages.combat-window-seconds", 10) * 1000);
        EntityDamageEvent last = v.getLastDamageCause();
        String cause = last == null ? "" : last.getCause().name();
        Player killer = v.getKiller();
        String mob = null;
        boolean recent = false;

        if (killer == null && last instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
            if (d instanceof Player pl && !pl.equals(v)) killer = pl;
            else if (!(d instanceof Player)) mob = Theme.pretty(d.getType().getKey().getKey()).toLowerCase();
        }
        String killerName = killer == null ? null : killer.getName();
        if (killer == null) {
            Hit h = lastHit.get(v.getUniqueId());
            if (h != null && System.currentTimeMillis() - h.time() <= window) {
                recent = true;
                killer = Bukkit.getPlayer(h.attacker());
                killerName = h.name();
                mob = null;
            }
        }

        Component icon = Component.text(c().getString("chat.death-messages.kill-icon", "⚔") + " ",
                TextColor.fromHexString(Theme.RED));
        Component result;
        if (killerName != null) {
            Component kn = killer != null ? name(killer, Theme.ORANGE)
                    : Component.text(killerName, TextColor.fromHexString(Theme.ORANGE));
            boolean doomed = recent && (cause.equals("FALL") || cause.equals("VOID"));
            result = Component.text().append(icon).append(name(v, Theme.BLUE))
                    .append(gray(doomed ? " was doomed to fall by " : " was slain by ")).append(kn).build();
        } else if (mob != null) {
            result = Component.text().append(name(v, Theme.RED)).append(gray(" was killed by " + mob)).build();
        } else {
            result = Component.text().append(name(v, Theme.RED)).append(gray(" " + causeText(cause))).build();
        }
        e.deathMessage(result);
        combat.remove(v.getUniqueId());
        lastHit.remove(v.getUniqueId());
    }

    private static String causeText(String cause) {
        return switch (cause) {
            case "FALL" -> "fell from a high place";
            case "FIRE", "FIRE_TICK", "LAVA", "HOT_FLOOR", "MELTING" -> "burned to death";
            case "DROWNING" -> "drowned";
            case "VOID" -> "fell into the void";
            case "SUFFOCATION" -> "suffocated in a wall";
            case "STARVATION" -> "starved to death";
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> "was blown up";
            case "LIGHTNING" -> "was struck by lightning";
            case "POISON" -> "was poisoned";
            case "WITHER" -> "withered away";
            case "MAGIC" -> "was killed by magic";
            case "CONTACT" -> "was pricked to death";
            case "FLY_INTO_WALL" -> "flew into a wall";
            case "FREEZE" -> "froze to death";
            case "CRAMMING" -> "was squished";
            default -> "died";
        };
    }
}
