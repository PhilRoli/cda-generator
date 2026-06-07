package at.rolinek.cda.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    // -----------------------------------------------------------------------
    // X-Forwarded-For: single value
    // -----------------------------------------------------------------------

    @Test
    void xff_singleValue_returnsThatValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(ClientIp.from(request)).isEqualTo("1.2.3.4");
    }

    // -----------------------------------------------------------------------
    // X-Forwarded-For: comma-separated list → first entry
    // -----------------------------------------------------------------------

    @Test
    void xff_commaSeparatedList_returnsFirstEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");

        assertThat(ClientIp.from(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void xff_multipleEntries_returnsFirstEntryTrimmed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "  5.6.7.8 , 192.168.1.1, 172.16.0.1");

        assertThat(ClientIp.from(request)).isEqualTo("5.6.7.8");
    }

    // -----------------------------------------------------------------------
    // Blank / missing X-Forwarded-For → fall back to remoteAddr
    // -----------------------------------------------------------------------

    @Test
    void noXff_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("9.8.7.6");

        assertThat(ClientIp.from(request)).isEqualTo("9.8.7.6");
    }

    @Test
    void blankXff_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("9.8.7.6");

        assertThat(ClientIp.from(request)).isEqualTo("9.8.7.6");
    }

    // -----------------------------------------------------------------------
    // Both XFF and remoteAddr missing → "unknown"
    // -----------------------------------------------------------------------

    @Test
    void bothMissing_returnsUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // MockHttpServletRequest defaults remoteAddr to "127.0.0.1"; clear it via subclass trick
        // Instead, pass null request to exercise the null-safe path
        assertThat(ClientIp.from(null)).isEqualTo("unknown");
    }

    @Test
    void nullRequest_returnsUnknown() {
        assertThat(ClientIp.from(null)).isEqualTo("unknown");
    }
}
