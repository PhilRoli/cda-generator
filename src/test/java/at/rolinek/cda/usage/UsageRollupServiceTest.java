package at.rolinek.cda.usage;

import at.rolinek.cda.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageRollupServiceTest {

    private final UsageRepository repository = mock(UsageRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T03:15:00Z"), ZoneOffset.UTC);

    @Test
    void cutoffIsTodayMinusRetention() {
        AppProperties props = new AppProperties();
        props.getUsage().setRetentionDays(90);
        when(repository.rollupBefore(LocalDate.parse("2026-07-05"))).thenReturn(7);

        int folded = new UsageRollupService(repository, props, clock).runRollup();

        assertThat(folded).isEqualTo(7);
        verify(repository).rollupBefore(LocalDate.parse("2026-07-05"));
    }

    @Test
    void failureIsLoggedNotThrown() {
        when(repository.rollupBefore(any())).thenThrow(new DataAccessResourceFailureException("locked"));
        UsageRollupService service = new UsageRollupService(repository, new AppProperties(), clock);

        assertThatCode(service::runRollup).doesNotThrowAnyException();
    }
}
