package at.rolinek.cda.usage;

import at.rolinek.cda.api.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Records usage events. Best-effort: a recording failure never affects the request. */
@Service
public class UsageRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(UsageRecorder.class);
    private static final int MAX_USERNAME = 64;
    private static final int MAX_DETAIL = 200;

    private final UsageRepository repository;
    private final Clock clock;

    public UsageRecorder(UsageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(UsageType type, int httpStatus, String username, HttpServletRequest request, String detail) {
        try {
            boolean ok = httpStatus >= 200 && httpStatus < 300;
            repository.insert(new UsageEvent(
                clock.instant(),
                type,
                ok,
                httpStatus,
                cap(username == null ? "" : username.trim(), MAX_USERNAME),
                IpPrefix.of(ClientIp.from(request)),
                ok ? cap(detail == null ? "" : detail, MAX_DETAIL) : reasonFor(httpStatus)));
        } catch (RuntimeException ex) {
            LOG.warn("Nutzungsereignis konnte nicht gespeichert werden ({}): {}", type.wireName(), ex.getMessage());
        }
    }

    static String reasonFor(int status) {
        return switch (status) {
            case 400 -> "bad_request";
            case 403 -> "forbidden";
            case 413 -> "too_large";
            case 429 -> "throttled";
            case 503 -> "busy_or_timeout";
            default -> status >= 500 ? "server_error" : "client_error";
        };
    }

    private static String cap(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
