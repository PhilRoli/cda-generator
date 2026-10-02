package at.rolinek.cda.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin token and the clean-PDF password are the only secrets guarding the API;
 * without a throttle they can be guessed online at full request rate.
 */
class AuthFailureThrottleFilterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final AuthFailureThrottleFilter filter =
        new AuthFailureThrottleFilter(3, Duration.ofMinutes(15), clock);
    private final AtomicInteger appCalls = new AtomicInteger();

    /** Stands in for the controller: answers every request with the given status. */
    private FilterChain appResponding(int status) {
        return (req, res) -> {
            appCalls.incrementAndGet();
            ((HttpServletResponse) res).setStatus(status);
        };
    }

    private MockHttpServletResponse send(String path, String ip, int appStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, appResponding(appStatus));
        return response;
    }

    @Test
    void tooManyFailures_blockTheIpWith429WithoutReachingTheApp() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("/api/pdf/upload", "203.0.113.1", 403);
        }
        appCalls.set(0);

        MockHttpServletResponse blocked = send("/api/pdf/upload", "203.0.113.1", 200);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString())
            .contains("\"message\":\"Zu viele Fehlversuche. Bitte später erneut versuchen.\"");
        assertThat(appCalls).hasValue(0);
    }

    @Test
    void failuresAreCountedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("/api/admin/scenarios", "203.0.113.1", 403);
        }

        assertThat(send("/api/admin/scenarios", "203.0.113.2", 200).getStatus()).isEqualTo(200);
    }

    @Test
    void successfulRequestsDoNotCount() throws Exception {
        for (int i = 0; i < 5; i++) {
            send("/api/admin/scenarios", "203.0.113.1", 200);
        }

        assertThat(send("/api/admin/scenarios", "203.0.113.1", 200).getStatus()).isEqualTo(200);
    }

    @Test
    void blockExpiresAfterTheWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("/api/admin/scenarios", "203.0.113.1", 403);
        }
        clock.advance(Duration.ofMinutes(16));

        assertThat(send("/api/admin/scenarios", "203.0.113.1", 200).getStatus()).isEqualTo(200);
    }

    @Test
    void unprotectedPathsAreNeverThrottled() throws Exception {
        // 403 on a scenario owned by someone else is not a guessed secret.
        for (int i = 0; i < 5; i++) {
            send("/api/scenarios", "203.0.113.1", 403);
        }

        assertThat(send("/api/scenarios", "203.0.113.1", 200).getStatus()).isEqualTo(200);
    }
}
