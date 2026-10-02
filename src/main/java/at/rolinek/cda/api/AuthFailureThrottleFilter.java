package at.rolinek.cda.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles guessing of the admin token and the clean-PDF password: once an IP has
 * collected {@code maxFailures} 403 responses on a secret-protected endpoint within the
 * window, further requests to those endpoints are answered with 429 until it expires.
 * State is in-memory, which is enough for this single-instance deployment.
 */
@Component
public class AuthFailureThrottleFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFailureThrottleFilter.class);

    /** Bounds memory if many distinct IPs fail; expired entries are pruned first. */
    private static final int MAX_TRACKED_IPS = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Failures> failuresByIp = new ConcurrentHashMap<>();

    private record Failures(Instant windowStart, int count) {}

    @Autowired
    public AuthFailureThrottleFilter(
            @Value("${app.auth.max-failures:10}") int maxFailures,
            @Value("${app.auth.failure-window-minutes:15}") long windowMinutes) {
        this(maxFailures, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    AuthFailureThrottleFilter(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/admin/") || path.equals("/api/pdf/upload"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = ClientIp.from(request);
        Instant now = clock.instant();

        Failures current = failuresByIp.get(ip);
        if (current != null && current.count() >= maxFailures && !isExpired(current, now)) {
            long retryAfter = Math.max(1, Duration.between(now, current.windowStart().plus(window)).toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Zu viele Fehlversuche. Bitte später erneut versuchen.\"}");
            return;
        }

        chain.doFilter(request, response);

        if (response.getStatus() == HttpServletResponse.SC_FORBIDDEN) {
            recordFailure(ip, now);
        }
    }

    private boolean isExpired(Failures failures, Instant now) {
        return !now.isBefore(failures.windowStart().plus(window));
    }

    private void recordFailure(String ip, Instant now) {
        if (failuresByIp.size() >= MAX_TRACKED_IPS) {
            failuresByIp.values().removeIf(f -> isExpired(f, now));
        }
        Failures updated = failuresByIp.compute(ip, (key, f) ->
            f == null || isExpired(f, now) ? new Failures(now, 1) : new Failures(f.windowStart(), f.count() + 1));
        if (updated.count() == maxFailures) {
            LOG.warn("event=auth_throttled ip={} failures={} window={}m", ip, updated.count(), window.toMinutes());
        }
    }
}
