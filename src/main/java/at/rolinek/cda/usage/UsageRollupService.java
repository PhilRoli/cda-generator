package at.rolinek.cda.usage;

import at.rolinek.cda.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** Enforces the usage-event retention window nightly and once at startup. */
@Service
public class UsageRollupService {

    private static final Logger LOG = LoggerFactory.getLogger(UsageRollupService.class);

    private final UsageRepository repository;
    private final int retentionDays;
    private final Clock clock;

    public UsageRollupService(UsageRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.retentionDays = properties.getUsage().getRetentionDays();
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        runRollup();
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    void nightly() {
        runRollup();
    }

    /** @return number of events folded; 0 on failure (logged, retried next run) */
    int runRollup() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(retentionDays);
        try {
            int folded = repository.rollupBefore(cutoff);
            LOG.info("event=usage_rollup cutoff={} folded={}", cutoff, folded);
            return folded;
        } catch (RuntimeException ex) {
            LOG.warn("Nutzungsstatistik-Rollup fehlgeschlagen: {}", ex.getMessage());
            return 0;
        }
    }
}
