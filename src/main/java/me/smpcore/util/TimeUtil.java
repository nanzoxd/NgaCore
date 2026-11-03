package me.smpcore.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private TimeUtil() {}

    private static final Pattern PART = Pattern.compile("(\\d+)([smhdwy])");

    /** @return milliseconds, -1 for permanent, null when the text is invalid. */
    public static Long parse(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever")) return -1L;
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        try {
            while (m.find()) {
                if (m.start() != end) return null;
                total = Math.addExact(total, Math.multiplyExact(Long.parseLong(m.group(1)), unit(m.group(2).charAt(0))));
                end = m.end();
            }
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
        if (end != s.length() || total <= 0) return null;
        return total;
    }

    private static long unit(char c) {
        return switch (c) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            case 'w' -> 604_800_000L;
            default -> 31_536_000_000L;
        };
    }

    public static String format(long ms) {
        if (ms < 0) return "Permanent";
        long s = ms / 1000;
        if (s <= 0) return "0s";
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        int parts = 0;
        if (d > 0) { sb.append(d).append("d "); parts++; }
        if (h > 0) { sb.append(h).append("h "); parts++; }
        if (m > 0 && parts < 2) { sb.append(m).append("m "); parts++; }
        if (sec > 0 && parts < 2) sb.append(sec).append("s ");
        return sb.toString().trim();
    }
}
