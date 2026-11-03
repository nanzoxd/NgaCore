package me.smpcore.util;

/**
 * Tooltip lines in the reference style: a two-bar mouse icon (left or right button lit green)
 * followed by a label and a coloured state, e.g. "▌▌ Disabled".
 */
public final class Lore {
    private static final String OFF = "#3a5a73";

    private Lore() {}

    public static String left() { return Theme.green("▌") + Theme.c(OFF, "▌"); }

    public static String right() { return Theme.c(OFF, "▌") + Theme.green("▌"); }

    public static String mouse(boolean left) { return left ? left() : right(); }

    private static String st(boolean on) { return on ? Theme.green("Enabled") : Theme.red("Disabled"); }

    /** Disabled shows the left button (left click enables); enabled shows the right button (right click disables). */
    public static String state(boolean on) { return mouse(!on) + " " + st(on); }

    public static String opt(String label, boolean on, boolean left) {
        return mouse(left) + " " + Theme.c(Theme.DIM, label + ":") + " " + st(on);
    }

    public static String inc() { return left() + " " + Theme.c(Theme.DIM, "Increase"); }

    public static String dec() { return right() + " " + Theme.c(Theme.DIM, "Decrease"); }

    /** Middle click (creative-mode inventory click) puts a value back to its default. */
    public static String reset() { return Theme.green("◉") + " " + Theme.c(Theme.DIM, "Back to the default"); }

    public static String open() { return left() + " " + Theme.c(Theme.DIM, "Open"); }

    public static String shift(String text) { return Theme.yellow("Shift") + " " + Theme.c(Theme.DIM, text); }

    public static String key(String key, boolean on) { return Theme.yellow(key) + " " + Theme.c(Theme.DIM, "Toggle:") + " " + st(on); }

    public static String value(String label, String v) { return Theme.c(Theme.DIM, label + ":") + " " + Theme.yellow(v); }
}
