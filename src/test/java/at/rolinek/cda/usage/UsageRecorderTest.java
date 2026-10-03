package at.rolinek.cda.usage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UsageRecorderTest {

    private final UsageRepository repository = mock(UsageRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    private final UsageRecorder recorder = new UsageRecorder(repository, clock);

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private UsageEvent recorded() {
        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(repository).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void successfulEventKeepsDetailAndShortensIp() {
        recorder.record(UsageType.SCENARIO_LOAD, 200, " anna ", request("203.0.113.7"), "scenario-1");

        UsageEvent e = recorded();
        assertThat(e.occurredAt()).isEqualTo(Instant.parse("2026-10-03T12:00:00Z"));
        assertThat(e.ok()).isTrue();
        assertThat(e.username()).isEqualTo("anna");
        assertThat(e.ipPrefix()).isEqualTo("203.0.113.0");
        assertThat(e.detail()).isEqualTo("scenario-1");
    }

    @Test
    void failedEventGetsReasonFromStatus() {
        recorder.record(UsageType.PDF, 503, null, request("203.0.113.7"), "");

        UsageEvent e = recorded();
        assertThat(e.ok()).isFalse();
        assertThat(e.httpStatus()).isEqualTo(503);
        assertThat(e.username()).isEmpty();
        assertThat(e.detail()).isEqualTo("busy_or_timeout");
    }

    @Test
    void reasonsCoverAllStatusClasses() {
        assertThat(UsageRecorder.reasonFor(400)).isEqualTo("bad_request");
        assertThat(UsageRecorder.reasonFor(403)).isEqualTo("forbidden");
        assertThat(UsageRecorder.reasonFor(413)).isEqualTo("too_large");
        assertThat(UsageRecorder.reasonFor(429)).isEqualTo("throttled");
        assertThat(UsageRecorder.reasonFor(503)).isEqualTo("busy_or_timeout");
        assertThat(UsageRecorder.reasonFor(500)).isEqualTo("server_error");
        assertThat(UsageRecorder.reasonFor(404)).isEqualTo("client_error");
    }

    @Test
    void hostileUsernameAndDetailAreCapped() {
        String longName = "<img src=x onerror=alert(1)>".repeat(10);
        recorder.record(UsageType.SCENARIO_SAVE, 200, longName, request("203.0.113.7"), "d".repeat(500));

        UsageEvent e = recorded();
        assertThat(e.username()).hasSize(64);
        assertThat(e.detail()).hasSize(200);
    }

    @Test
    void databaseFailureIsSwallowed() {
        doThrow(new DataAccessResourceFailureException("database is locked")).when(repository).insert(any());

        assertThatCode(() -> recorder.record(UsageType.PDF, 200, "anna", request("203.0.113.7"), ""))
            .doesNotThrowAnyException();
    }
}
