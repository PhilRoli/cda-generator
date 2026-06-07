package at.rolinek.cda.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts the originating client IP address from an HTTP request.
 *
 * <p>The application runs behind a Caddy reverse proxy that sets the
 * {@code X-Forwarded-For} header. The first entry in that header is the
 * real client address; Caddy appends the connecting-peer IP to the list.
 * If the header is absent (e.g. direct connections in tests/dev) the method
 * falls back to {@link HttpServletRequest#getRemoteAddr()}.
 */
public final class ClientIp {

    private ClientIp() {}

    /**
     * Returns the best-effort originating client IP address.
     * Never throws; returns {@code "unknown"} if nothing is available.
     *
     * @param request the incoming HTTP request (may be null)
     * @return the client IP string, never null
     */
    public static String from(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF may be "client, proxy1, proxy2" — take the first entry
            int commaIdx = xff.indexOf(',');
            String first = commaIdx >= 0 ? xff.substring(0, commaIdx) : xff;
            String trimmed = first.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        String remote = request.getRemoteAddr();
        return (remote != null && !remote.isBlank()) ? remote : "unknown";
    }
}
