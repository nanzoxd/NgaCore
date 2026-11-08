package me.smpcore.module;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import me.smpcore.SMPCore;
import me.smpcore.util.Msg;
import me.smpcore.util.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * /announce: animated title (gradient with a moving glint) + the message under it + a chat line + title sounds.
 * /broadcast: chat line only, with its own sound.
 *
 * A Minecraft subtitle is always one line, so a long message is word-wrapped and the lines are shown one after
 * another under the title (the title keeps glinting). The full text is always in chat.
 */
public final class AnnounceModule {
    /** Frames are sent every 2 ticks (10 per second): smooth glint, almost no traffic. */
    private static final int FRAME = 2;
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private final SMPCore plugin;
    private BukkitTask running;
    private BukkitTask previewTask;

    public AnnounceModule(SMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("announce.enabled", true);
    }

    /** A colour preset: gradient stops for the title and label, plus the colour of the message text. */
    private record Preset(String name, TextColor[] stops, TextColor text) {}

    private Preset preset() {
        var c = plugin.getConfig();
        String name = c.getString("announce.preset", "ice");
        ConfigurationSection s = c.getConfigurationSection("announce.presets." + name);
        if (s == null) return new Preset(name, gradient(List.of()), TextColor.color(0xD5FBFF));
        return new Preset(name, gradient(s.getStringList("gradient")), hex(s.getString("text"), TextColor.color(0xD5FBFF)));
    }

    /** Names of all presets in config.yml (announce.presets), in file order. */
    public List<String> presetNames() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("announce.presets");
        return s == null ? List.of("ice") : new ArrayList<>(s.getKeys(false));
    }

    public String presetName() {
        return preset().name();
    }

    /** First colour of a preset as #RRGGBB, for tooltips. */
    public String presetHex(String name) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("announce.presets." + name);
        TextColor c = gradient(s == null ? List.of() : s.getStringList("gradient"))[0];
        return c.asHexString();
    }

    /** Q in the settings menu: next preset (dir 1) or previous (dir -1). Saved to config.yml. */
    public void cyclePreset(int dir) {
        List<String> names = presetNames();
        int i = Math.max(0, names.indexOf(presetName()));
        plugin.getConfig().set("announce.preset", names.get(Math.floorMod(i + dir, names.size())));
        plugin.saveConfig();
    }

    /** Shows the new colours to one player for a moment (no chat, no sounds). */
    public void preview(Player p) {
        if (previewTask != null) previewTask.cancel();
        Preset pr = preset();
        previewTask = show(() -> {
            if (p.isOnline()) return Collections.singletonList(p);
            return Collections.<Player>emptyList();
        }, List.of("Colour preset: " + Theme.pretty(pr.name())), 50, pr);
    }

    /** Chat line + animated title + title sounds for every online player. */
    public void announce(String message) {
        var c = plugin.getConfig();
        String text = message.strip();
        Preset pr = preset();
        Bukkit.getServer().sendMessage(line("announce.chat-format", c.getString("announce.title", Theme.smallCaps("announcement")), pr, text));

        List<String> pages = wrap(text, Math.max(10, c.getInt("announce.wrap-chars", 32)));
        int pageTicks = Math.max(20, (int) Math.round(c.getDouble("announce.page-seconds", 4.0) * 20));
        if (running != null) running.cancel();
        playTitleSounds();
        running = show(Bukkit::getOnlinePlayers, pages, pageTicks, pr);
    }

    /** "label → message": the label uses the preset gradient, the message its text colour. */
    private Component line(String formatKey, String labelText, Preset pr, String text) {
        return Theme.MM.deserialize(plugin.getConfig().getString(formatKey, "<label> <#9babc7>→ <message>"),
                Placeholder.component("label", headline(labelText, pr.stops(), -10, 0.22)),
                Placeholder.component("message", Component.text(text, pr.text())));
    }

    /** Sends the glinting title with the pages as subtitle, one page every pageTicks, to whoever is online at each frame. */
    private BukkitTask show(Supplier<Collection<? extends Player>> to, List<String> pages, int pageTicks, Preset pr) {
        var c = plugin.getConfig();
        int total = pageTicks * pages.size();
        String headline = Theme.smallCaps(c.getString("announce.title", "announcement"));
        boolean glint = c.getBoolean("announce.glint.enabled", true);
        int period = Math.max(10, c.getInt("announce.glint.period-ticks", 50));
        double width = Math.max(0.05, c.getDouble("announce.glint.width", 0.22));
        TextColor[] stops = pr.stops();
        TextColor subColor = pr.text();

        return new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= total) {
                    cancel();
                    return;
                }
                int page = Math.min(t / pageTicks, pages.size() - 1);
                double center = glint ? -width + ((t % period) / (double) period) * (1.0 + 2 * width) : -10;
                Title.Times times = Title.Times.times(
                        t == 0 ? Duration.ofMillis(400) : Duration.ZERO, // fade in once, never flash on updates
                        Duration.ofMillis(400),
                        Duration.ofMillis(800));                          // fades out by itself after the last frame
                Title title = Title.title(headline(headline, stops, center, width),
                        Component.text(Theme.smallCaps(pages.get(page)), subColor), times);
                for (Player p : to.get()) p.showTitle(title);
                t += FRAME;
            }
        }.runTaskTimer(plugin, 0L, FRAME);
    }

    /** Chat line only, no title. Plays the broadcast sound. */
    public void broadcast(String message) {
        Bukkit.getServer().sendMessage(line("announce.broadcast-format", Theme.smallCaps("broadcast"), preset(), message.strip()));
        for (Player p : Bukkit.getOnlinePlayers()) Msg.cfgSound(p, "announce.broadcast-sound");
    }

    /** Every entry of announce.title-sounds: {sound, volume, pitch, delay in ticks}. A later delay is the "second" sound. */
    private void playTitleSounds() {
        for (Map<?, ?> m : plugin.getConfig().getMapList("announce.title-sounds")) {
            Object s = m.get("sound");
            if (s == null) continue;
            String key = String.valueOf(s);
            float volume = (float) num(m.get("volume"), 1.0);
            float pitch = (float) num(m.get("pitch"), 1.0);
            int delay = (int) num(m.get("delay"), 0);
            Runnable play = () -> {
                for (Player p : Bukkit.getOnlinePlayers()) Msg.sound(p, key, volume, pitch);
            };
            if (delay <= 0) play.run();
            else Bukkit.getScheduler().runTaskLater(plugin, play, delay);
        }
    }

    /** The headline: gradient per letter, with a white band (the glint) around position {@code center} (0 = first letter, 1 = last). */
    private static Component headline(String text, TextColor[] stops, double center, double width) {
        TextComponent.Builder b = Component.text();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            double x = n == 1 ? 0 : i / (double) (n - 1);
            double d = Math.abs(x - center);
            double k = d >= width ? 0 : 1 - d / width;
            k = k * k * (3 - 2 * k); // smoothstep: soft edges
            TextColor col = lerp(sample(stops, x), WHITE, k * 0.95);
            b.append(Component.text(String.valueOf(text.charAt(i)), col));
        }
        return b.build().decorate(TextDecoration.BOLD);
    }

    private static TextColor[] gradient(List<String> list) {
        List<TextColor> out = new ArrayList<>();
        for (String s : list) {
            TextColor c = TextColor.fromHexString(s);
            if (c != null) out.add(c);
        }
        if (out.size() < 2) return new TextColor[] {TextColor.color(0x00EEFF), TextColor.color(0xD5FBFF), TextColor.color(0xD5FBFF)};
        return out.toArray(new TextColor[0]);
    }

    private static TextColor sample(TextColor[] stops, double x) {
        double pos = Math.max(0, Math.min(1, x)) * (stops.length - 1);
        int i = Math.min((int) pos, stops.length - 2);
        return lerp(stops[i], stops[i + 1], pos - i);
    }

    private static TextColor lerp(TextColor a, TextColor b, double f) {
        return TextColor.color(
                (int) Math.round(a.red() + (b.red() - a.red()) * f),
                (int) Math.round(a.green() + (b.green() - a.green()) * f),
                (int) Math.round(a.blue() + (b.blue() - a.blue()) * f));
    }

    private static TextColor hex(String s, TextColor fallback) {
        TextColor c = s == null ? null : TextColor.fromHexString(s);
        return c == null ? fallback : c;
    }

    private static double num(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** Word-wrap to lines of at most {@code max} characters; a word longer than a line is cut. */
    static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.trim().split("\\s+")) {
            while (w.length() > max) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.add(w.substring(0, max));
                w = w.substring(max);
            }
            if (cur.length() > 0 && cur.length() + 1 + w.length() > max) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }
}
