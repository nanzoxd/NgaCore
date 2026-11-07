package me.smpcore.util;

import java.util.Arrays;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Blue palette theme: #052e74 #36578f #6981ab #9babc7 #cdd5e3. Blue text is drawn as gradients between
 * neighbouring palette colours; vibrant accents (red, orange, yellow, green, blue) mark key words and states.
 */
public final class Theme {
    private Theme() {}

    public static final MiniMessage MM = MiniMessage.miniMessage();
    // palette
    public static final String DEEP = "#052e74";
    public static final String STEEL = "#36578f";
    public static final String MID = "#6981ab";
    public static final String SOFT = "#9babc7";
    public static final String SAND = "#cdd5e3";
    public static final String BRIGHT = "#cdd5e3";

    /** Light gradient for dark backgrounds (tooltips, chat, dialogs, kick screens). */
    public static final String GRADIENT = "<gradient:#cdd5e3:#9babc7:#6981ab>";
    /** Dark gradient for the light-gray inventory title bar. */
    public static final String GUI_GRADIENT = "<bold><gradient:#052e74:#36578f:#6981ab>";

    public static final String TEXT = SAND;
    public static final String DIM = SOFT;
    public static final String RED = "#FF5555";
    public static final String ORANGE = "#FFAA33";
    public static final String YELLOW = "#FFE45C";
    public static final String GREEN = "#55FF7F";
    public static final String BLUE = "#55B8FF";

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
        // palette blues are drawn as gradients between neighbouring palette colours
        if (hex.equalsIgnoreCase(DIM)) return "<gradient:" + BRIGHT + ":" + SOFT + ">" + text + "</gradient>";
        if (hex.equalsIgnoreCase(MID)) return "<gradient:" + SOFT + ":" + MID + ">" + text + "</gradient>";
        return "<color:" + hex + ">" + text + "</color>";
    }

    /** Dark - light - dark gradients, one hue each, used for the feature names in the main menu. */
    private static final String[][] HUES = {
            {"#00D0FF", "#C4EEFF"}, {"#00D060", "#C4FFDE"}, {"#FF3B5C", "#FFC4D0"}, {"#FF8A00", "#FFE0B8"}, {"#FFD000", "#FFF3B8"},
            {"#A855FF", "#E8D0FF"}, {"#FF4FA3", "#FFC4E3"}, {"#3B6BFF", "#C4D4FF"}, {"#00D0B0", "#C4FFF4"}, {"#8CFF00", "#E6FFC4"}};

    private static final String SC_FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String SC_TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    /** "Random TP" -> "ʀᴀɴᴅᴏᴍ ᴛᴘ" (Unicode has no small-cap x, so x stays as it is). */
    public static String smallCaps(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase().toCharArray()) {
            int i = SC_FROM.indexOf(c);
            b.append(i >= 0 ? SC_TO.charAt(i) : c);
        }
        return b.toString();
    }

    public static String hue(int i, Object text) {
        String[] h = HUES[Math.floorMod(i, HUES.length)];
        return "<gradient:" + h[0] + ":" + h[1] + ":" + h[0] + ">" + text + "</gradient>";
    }

    /** Palette gradient for any piece of text (names, headings). */
    public static String grad(Object text) { return "<gradient:" + BRIGHT + ":" + SOFT + ">" + text + "</gradient>"; }

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
