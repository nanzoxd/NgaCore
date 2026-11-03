package me.smpcore.util;

import java.util.Arrays;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Palette theme: #3a5a73 #6d93ab #abc4d1 #e3dccf #f2ede4.
 * The palette carries the sentence; accent colours (red, orange, yellow, green, blue) only mark key words.
 */
public final class Theme {
    private Theme() {}

    public static final MiniMessage MM = MiniMessage.miniMessage();
    // palette
    public static final String DEEP = "#3a5a73";
    public static final String MID = "#6d93ab";
    public static final String SOFT = "#abc4d1";
    public static final String SAND = "#e3dccf";
    public static final String BRIGHT = "#f2ede4";

    /** Light gradient for dark backgrounds (tooltips, chat, dialogs, kick screens). */
    public static final String GRADIENT = "<gradient:#f2ede4:#abc4d1:#6d93ab>";
    /** Dark gradient for the light-gray inventory title bar. */
    public static final String GUI_GRADIENT = "<bold><gradient:#3a5a73:#6d93ab>";

    public static final String TEXT = SAND;
    public static final String DIM = SOFT;
    public static final String RED = "#E8847C";
    public static final String ORANGE = "#EBB071";
    public static final String YELLOW = "#F2DC84";
    public static final String GREEN = "#97D69A";
    public static final String BLUE = "#8FC3E0";

    /** MiniMessage -> component, never italic (for item names and lore). */
    public static Component parse(String mm) {
        return MM.deserialize(mm).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Inventory title: dark palette gradient so it reads on the light-gray GUI frame. */
    public static Component guiTitle(String text) {
        return parse(GUI_GRADIENT + text + "</gradient></bold>");
    }

    public static Component title(String text) {
        return parse(GRADIENT + text + "</gradient>");
    }

    /** Wrap text in an accent colour. */
    public static String c(String hex, Object text) {
        return "<color:" + hex + ">" + text + "</color>";
    }

    public static String red(Object t) { return c(RED, t); }
    public static String orange(Object t) { return c(ORANGE, t); }
    public static String yellow(Object t) { return c(YELLOW, t); }
    public static String green(Object t) { return c(GREEN, t); }
    public static String blue(Object t) { return c(BLUE, t); }

    public static String state(boolean on) {
        return on ? green("Enabled") : red("Disabled");
    }

    /** 20.0 -> "20", 7.5 -> "7.5" */
    public static String PLAIN_NUM(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 10) / 10.0);
    }

    public static String esc(String s) {
        return MM.escapeTags(s);
    }

    /** instant_health -> Instant Health */
    public static String pretty(String key) {
        return Arrays.stream(key.split("_"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
