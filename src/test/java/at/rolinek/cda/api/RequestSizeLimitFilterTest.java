package at.rolinek.cda.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring/Tomcat don't cap JSON request bodies, so without this filter a single huge POST
 * to /api/pdf or /api/scenarios is read fully into heap.
 */
class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(10);

    @Test
    void declaredLengthOverLimit_isRejectedWith413WithoutReachingTheApp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pdf");
        request.setContent(new byte[11]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"message\":\"Anfrage ist zu groß.\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void bodyWithinLimit_isPassedThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pdf");
        request.setContent(new byte[10]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize(10);
    }

    @Test
    void chunkedBodyOverLimit_failsWhileReading() throws Exception {
        // Chunked transfer: no Content-Length, so the limit must be enforced on the stream.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pdf") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(new byte[11]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThatThrownBy(() -> chain.getRequest().getInputStream().readAllBytes())
            .isInstanceOf(IOException.class);
    }
}
