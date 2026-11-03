package me.smpcore.util;

import me.smpcore.SMPCore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class Msg {
    private Msg() {}

    private static SMPCore plugin;
    private static Component prefix = Component.empty();

    public static void init(SMPCore p) {
        plugin = p;
        prefix = Theme.MM.deserialize(p.getConfig().getString("prefix", ""));
    }

    public static Component body(String mm) {
        return Theme.MM.deserialize("<" + Theme.TEXT + ">" + mm);
    }

    public static void send(CommandSender to, String mm) {
        to.sendMessage(prefix.append(body(mm)));
    }

    public static void bar(Player p, String mm) {
        p.sendActionBar(body(mm));
    }

    public static void sound(Player p, String key, float volume, float pitch) {
        try {
            p.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch));
        } catch (Exception ignored) {
            // invalid key in config
        }
    }

    /** Plays the sound described at config path {sound, volume, pitch}. Call on the main thread. */
    public static void cfgSound(Player p, String path) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection(path);
        if (s == null) return;
        sound(p, s.getString("sound", ""), (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0));
    }
}
