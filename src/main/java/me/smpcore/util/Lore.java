package me.smpcore.util;

/**
 * Tooltip lines in the reference style: a two-bar mouse icon (left click lit green, right click lit red, the other bar gray)
 * followed by a label and a coloured state, e.g. "▆▆ Disabled". Middle click / reset uses a green ◉.
 */
public final class Lore {
    /** One bar of the mouse icon. */
    private static final String BAR = "▆";
    /** Unlit button colour (light gray, like the reference). */
    private static final String OFF = "#8b8f94";

    private Lore() {}

    public static String left() { return Theme.green(BAR) + Theme.c(OFF, BAR); }

    public static String right() { return Theme.c(OFF, BAR) + Theme.red(BAR); }

    public static String mouse(boolean left) { return left ? left() : right(); }

    private static String st(boolean on) { return on ? Theme.green("Enabled") : Theme.red("Disabled"); }

    /** Disabled shows the left button (left click enables); enabled shows the right button (right click disables). */
    public static String state(boolean on) { return mouse(!on) + " " + st(on); }

    /** LMB is green, RMB is red. */
    public static String key(boolean left) { return left ? Theme.green("LMB") : Theme.red("RMB"); }

    /** Two-option row: the clicked button, its key name, the label and the state. */
    public static String opt(String label, boolean on, boolean left) {
        return mouse(left) + " " + key(left) + " " + Theme.c(Theme.DIM, label + ":") + " " + st(on);
    }

    /** Single-option switch: the state, then which click enables and which disables. */
    public static java.util.List<String> toggle(boolean on) {
        return java.util.List.of(Theme.c(Theme.DIM, "Status:") + " " + st(on),
                left() + " " + key(true) + " " + Theme.c(Theme.DIM, "to Enable"),
                right() + " " + key(false) + " " + Theme.c(Theme.DIM, "to Disable"));
    }

    public static String inc() { return left() + " " + key(true) + " " + Theme.c(Theme.DIM, "Increase"); }

    public static String dec() { return right() + " " + key(false) + " " + Theme.c(Theme.DIM, "Decrease"); }

    /** Middle click (creative-mode inventory click) puts a value back to its default. */
    public static String reset() { return middle("Back to the default"); }

    /** Middle-click line: green ◉ and a label. */
    public static String middle(String label) { return Theme.green("◉") + " " + Theme.yellow("MMB") + " " + Theme.c(Theme.DIM, label); }

    /** Middle-click option with a state, e.g. "◉ Stuns shield: Disabled". */
    public static String middleOpt(String label, boolean on) { return Theme.green("◉") + " " + Theme.yellow("MMB") + " " + Theme.c(Theme.DIM, label + ":") + " " + st(on); }

    public static String open() { return left() + " " + key(true) + " " + Theme.c(Theme.DIM, "to Open"); }

    public static String shift(String text) { return Theme.yellow("Shift") + " " + Theme.c(Theme.DIM, text); }

    public static String key(String key, boolean on) { return Theme.yellow(key) + " " + Theme.c(Theme.DIM, "Toggle:") + " " + st(on); }

    public static String value(String label, String v) { return Theme.c(Theme.DIM, label + ":") + " " + Theme.yellow(v); }
}
