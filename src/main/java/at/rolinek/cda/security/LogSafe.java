package at.rolinek.cda.security;

/** Makes user-supplied values safe to embed in single-line key=value log events. */
public final class LogSafe {

    private static final int MAX_LENGTH = 200;

    private LogSafe() {}

    /**
     * Escapes CR/LF (so a value can't start a forged log line), replaces other control
     * characters with '?', and truncates to {@value #MAX_LENGTH} characters.
     */
    public static String of(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(Math.min(value.length(), MAX_LENGTH) + 8);
        for (int i = 0; i < value.length() && i < MAX_LENGTH; i++) {
            char c = value.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (Character.isISOControl(c)) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        if (value.length() > MAX_LENGTH) {
            sb.append('…');
        }
        return sb.toString();
    }
}
