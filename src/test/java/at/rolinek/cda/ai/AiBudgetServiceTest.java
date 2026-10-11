package at.rolinek.cda.ai;

import at.rolinek.cda.config.AppProperties;
import at.rolinek.cda.usage.StatsQueries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiBudgetServiceTest {

    private final StatsQueries queries = mock(StatsQueries.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);
    private final AppProperties props = new AppProperties();
    private AiBudgetService service;

    @BeforeEach
    void setUp() {
        props.getAi().setApiKey("sk-test");
        props.getAi().setUserDailyLimit(3);
        props.getAi().setMonthlyBudgetUsd(1.0);
        given(queries.aiTotals(any(), any())).willReturn(new StatsQueries.AiMonthTotals(0, 0, 0));
        given(queries.aiAttemptsOn(any(), anyString(), anyString())).willReturn(0L);
        service = new AiBudgetService(queries, props, clock);
    }

    private void spent(long inputTokens, long outputTokens) {
        given(queries.aiTotals(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01")))
            .willReturn(new StatsQueries.AiMonthTotals(10, inputTokens, outputTokens));
    }

    private ResponseStatusException rejection() {
        try {
            service.checkAllowed("anna", "10.0.0.0");
        } catch (ResponseStatusException ex) {
            return ex;
        }
        return null;
    }

    @Test
    void allowsWithinLimits() {
        assertThatCode(() -> service.checkAllowed("anna", "10.0.0.0")).doesNotThrowAnyException();
        assertThat(service.enabled()).isTrue();
    }

    @Test
    void blankKeyIs503AndNeverTouchesTheDatabase() {
        props.getAi().setApiKey("");
        ResponseStatusException ex = rejection();
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(service.enabled()).isFalse();
        verifyNoInteractions(queries);
    }

    @Test
    void dailyCapIs429WithGermanMessage() {
        given(queries.aiAttemptsOn(LocalDate.parse("2026-10-11"), "anna", "10.0.0.0")).willReturn(3L);
        ResponseStatusException ex = rejection();
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getReason()).contains("Tageslimit");
    }

    @Test
    void dailyCapJustBelowLimitStillAllowed() {
        given(queries.aiAttemptsOn(LocalDate.parse("2026-10-11"), "anna", "10.0.0.0")).willReturn(2L);
        assertThat(rejection()).isNull();
    }

    @Test
    void monthlyBudgetReachedIs503AndDisablesStatus() {
        spent(5_000_000, 1_000_000); // 0.50 + 0.50 = 1.00 USD, equals the budget
        ResponseStatusException ex = rejection();
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getReason()).contains("Monat");
        assertThat(service.enabled()).isFalse();
    }

    @Test
    void monthSummaryReportsEstimatedSpend() {
        spent(1_000_000, 1_000_000);
        AiBudgetService.MonthSummary summary = service.monthSummary();
        assertThat(summary.month()).isEqualTo("2026-10");
        assertThat(summary.fills()).isEqualTo(10);
        assertThat(summary.estimatedUsd()).isEqualTo(0.60, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(summary.budgetUsd()).isEqualTo(1.0);
    }

    @Test
    void anonymousUserIsCountedByIpPrefix() {
        given(queries.aiAttemptsOn(LocalDate.parse("2026-10-11"), "", "198.51.100.0")).willReturn(3L);
        assertThatThrownBy(() -> service.checkAllowed("", "198.51.100.0"))
            .isInstanceOf(ResponseStatusException.class);
    }
}
