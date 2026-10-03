package at.rolinek.cda.usage;

import java.util.regex.Pattern;

/**
 * Shortens a client IP before it is stored: IPv4 to /24, IPv6 to /48. Parsing is purely
 * textual (no DNS lookups); anything that isn't an IP literal yields "".
 */
public final class IpPrefix {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    private IpPrefix() {}

    public static String of(String ip) {
        if (ip == null) {
            return "";
        }
        String value = ip.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        String lower = value.toLowerCase();
        if (lower.startsWith("::ffff:") && IPV4.matcher(lower.substring(7)).matches()) {
            return ipv4(lower.substring(7));
        }
        if (IPV4.matcher(value).matches()) {
            return ipv4(value);
        }
        return value.contains(":") ? ipv6(lower) : "";
    }

    private static String ipv4(String value) {
        String[] parts = value.split("\\.");
        for (String part : parts) {
            if (Integer.parseInt(part) > 255) {
                return "";
            }
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + ".0";
    }

    private static String ipv6(String value) {
        String[] groups = expand(value);
        if (groups == null) {
            return "";
        }
        return hex(groups[0]) + ":" + hex(groups[1]) + ":" + hex(groups[2]) + "::";
    }

    /** Expands "::" so there are exactly 8 groups; null if not a valid IPv6 literal. */
    private static String[] expand(String value) {
        if (!value.matches("[0-9a-f:]+") || value.contains(":::")) {
            return null;
        }
        String[] halves = value.split("::", -1);
        if (halves.length > 2) {
            return null;
        }
        String[] head = halves[0].isEmpty() ? new String[0] : halves[0].split(":");
        String[] tail = halves.length == 2 && !halves[1].isEmpty() ? halves[1].split(":") : new String[0];
        int missing = 8 - head.length - tail.length;
        if (halves.length == 1 ? missing != 0 : missing < 1) {
            return null;
        }
        String[] groups = new String[8];
        int i = 0;
        for (String g : head) groups[i++] = g;
        for (int m = 0; m < missing; m++) groups[i++] = "0";
        for (String g : tail) groups[i++] = g;
        for (String g : groups) {
            if (g.isEmpty() || g.length() > 4) {
                return null;
            }
        }
        return groups;
    }

    private static String hex(String group) {
        return Integer.toHexString(Integer.parseInt(group, 16));
    }
}
