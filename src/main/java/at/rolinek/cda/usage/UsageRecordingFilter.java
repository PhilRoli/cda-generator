package at.rolinek.cda.usage;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Records every PDF request with its final status. Ordered first so responses produced
 * by RequestSizeLimitFilter (413) and AuthFailureThrottleFilter (429) are recorded too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UsageRecordingFilter extends OncePerRequestFilter {

    public static final String USER_HEADER = "X-Cda-User";

    private final UsageRecorder recorder;

    public UsageRecordingFilter(UsageRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return typeFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            recorder.record(typeFor(request), status, request.getHeader(USER_HEADER), request, "");
        }
    }

    private static UsageType typeFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        return switch (request.getRequestURI()) {
            case "/api/pdf" -> UsageType.PDF;
            case "/api/pdf/upload" -> UsageType.CLEAN_PDF;
            default -> null;
        };
    }
}
