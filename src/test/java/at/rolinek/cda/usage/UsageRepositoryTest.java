package at.rolinek.cda.usage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UsageRepositoryTest {

    private Path db;
    private JdbcTemplate jdbc;
    private UsageRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        db = Files.createTempFile("usage-test-", ".db");
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + db.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        repository = new UsageRepository(jdbc);
        repository.initSchema();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (String suffix : new String[] {"", "-wal", "-shm"}) {
            Files.deleteIfExists(Path.of(db + suffix));
        }
    }

    private UsageEvent event(String at, UsageType type, boolean ok) {
        return new UsageEvent(Instant.parse(at), type, ok, ok ? 200 : 503, "philipp", "203.0.113.0",
            ok ? "" : "busy_or_timeout");
    }

    @Test
    void insertStoresAllFields() {
        repository.insert(event("2026-10-01T10:00:00Z", UsageType.PDF, false));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM usage_events");
        assertThat(row).containsEntry("type", "pdf").containsEntry("outcome", "failed")
            .containsEntry("http_status", 503).containsEntry("username", "philipp")
            .containsEntry("ip_prefix", "203.0.113.0").containsEntry("detail", "busy_or_timeout");
        assertThat((String) row.get("occurred_at")).startsWith("2026-10-01T10:00:00");
    }

    @Test
    void rollupFoldsOldEventsIntoDailyTotalsAndDeletesThem() {
        repository.insert(event("2026-06-01T08:00:00Z", UsageType.PDF, true));
        repository.insert(event("2026-06-01T23:59:59.999Z", UsageType.PDF, true));
        repository.insert(event("2026-06-01T09:00:00Z", UsageType.PDF, false));
        repository.insert(event("2026-06-02T09:00:00Z", UsageType.XML_DOWNLOAD, true));
        repository.insert(event("2026-07-01T09:00:00Z", UsageType.PDF, true)); // newer: kept

        int folded = repository.rollupBefore(LocalDate.parse("2026-06-15"));

        assertThat(folded).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usage_events", Integer.class)).isEqualTo(1);
        List<Map<String, Object>> daily = jdbc.queryForList(
            "SELECT day, type, outcome, count FROM usage_daily ORDER BY day, type, outcome");
        assertThat(daily).containsExactly(
            Map.of("day", "2026-06-01", "type", "pdf", "outcome", "failed", "count", 1),
            Map.of("day", "2026-06-01", "type", "pdf", "outcome", "ok", "count", 2),
            Map.of("day", "2026-06-02", "type", "xml_download", "outcome", "ok", "count", 1));
    }

    @Test
    void rollupAddsToExistingDailyRowsAndIsIdempotent() {
        repository.insert(event("2026-06-01T08:00:00Z", UsageType.PDF, true));
        repository.rollupBefore(LocalDate.parse("2026-06-15"));
        repository.insert(event("2026-06-01T12:00:00Z", UsageType.PDF, true)); // late arrival, same day

        repository.rollupBefore(LocalDate.parse("2026-06-15"));
        int second = repository.rollupBefore(LocalDate.parse("2026-06-15"));

        assertThat(second).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT count FROM usage_daily WHERE day='2026-06-01' AND type='pdf' AND outcome='ok'",
            Integer.class)).isEqualTo(2);
    }

    @Test
    void rollupWithNoEventsIsANoOp() {
        assertThat(repository.rollupBefore(LocalDate.parse("2026-06-15"))).isZero();
    }
}
