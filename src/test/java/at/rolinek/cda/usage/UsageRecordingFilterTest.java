package at.rolinek.cda.usage;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UsageRecordingFilterTest {

    private final UsageRecorder recorder = mock(UsageRecorder.class);
    private final UsageRecordingFilter filter = new UsageRecordingFilter(recorder);

    private void send(String method, String path, String user, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (user != null) request.addHeader("X-Cda-User", user);
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(status);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void watermarkedPdfRequestIsRecordedWithUserAndStatus() throws Exception {
        send("POST", "/api/pdf", "anna", 200);
        verify(recorder).record(eq(UsageType.PDF), eq(200), eq("anna"), any(), eq(""));
    }

    @Test
    void cleanPdfFailureIsRecorded() throws Exception {
        send("POST", "/api/pdf/upload", null, 429);
        verify(recorder).record(eq(UsageType.CLEAN_PDF), eq(429), eq(null), any(), eq(""));
    }

    @Test
    void otherPathsAndMethodsAreNotRecorded() throws Exception {
        send("POST", "/api/scenarios", "anna", 200);
        send("GET", "/api/pdf", "anna", 405);
        verify(recorder, never()).record(any(), anyInt(), any(), any(), any());
    }

    @Test
    void exceptionInTheChainIsRecordedAsServerError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pdf");
        FilterChain failing = (req, res) -> { throw new IllegalStateException("boom"); };
        try {
            filter.doFilter(request, new MockHttpServletResponse(), failing);
        } catch (Exception expected) {
            // rethrown unchanged
        }
        verify(recorder).record(eq(UsageType.PDF), eq(500), eq(null), any(), eq(""));
    }

    @Test
    void decodeUserHandlesPercentEncodingAndMalformedInput() {
        org.junit.jupiter.api.Assertions.assertEquals("Anna \uD83D\uDE91", UsageRecordingFilter.decodeUser("Anna%20%F0%9F%9A%91"));
        org.junit.jupiter.api.Assertions.assertEquals("J\u00FCrgen", UsageRecordingFilter.decodeUser("J%C3%BCrgen"));
        org.junit.jupiter.api.Assertions.assertEquals("anna", UsageRecordingFilter.decodeUser("anna"));
        org.junit.jupiter.api.Assertions.assertEquals("%E0%A4%A", UsageRecordingFilter.decodeUser("%E0%A4%A"));
        org.junit.jupiter.api.Assertions.assertNull(UsageRecordingFilter.decodeUser(null));
    }
}
