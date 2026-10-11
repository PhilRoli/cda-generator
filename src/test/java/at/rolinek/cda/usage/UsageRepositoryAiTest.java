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

import static org.assertj.core.api.Assertions.assertThat;

class UsageRepositoryAiTest {

    private Path db;
    private JdbcTemplate jdbc;
    private UsageRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        db = Files.createTempFile("usage-ai-test-", ".db");
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + db.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        repository = new UsageRepository(jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (String suffix : new String[] {"", "-wal", "-shm"}) {
            Files.deleteIfExists(Path.of(db + suffix));
        }
    }

    private UsageEvent aiEvent(String at, long in, long out) {
        return new UsageEvent(Instant.parse(at), UsageType.AI_FILL, true, 200, "anna", "203.0.113.0", "", in, out);
    }

    @Test
    void oldSchemaIsUpgradedInPlaceAndIdempotently() {
        jdbc.execute("CREATE TABLE usage_events (id INTEGER PRIMARY KEY AUTOINCREMENT, occurred_at TEXT NOT NULL, "
            + "type TEXT NOT NULL, outcome TEXT NOT NULL, http_status INTEGER, username TEXT NOT NULL DEFAULT '', "
            + "ip_prefix TEXT NOT NULL DEFAULT '', detail TEXT NOT NULL DEFAULT '')");
        jdbc.execute("CREATE TABLE usage_daily (day TEXT NOT NULL, type TEXT NOT NULL, outcome TEXT NOT NULL, "
            + "count INTEGER NOT NULL, PRIMARY KEY (day, type, outcome))");
        jdbc.update("INSERT INTO usage_events (occurred_at, type, outcome) VALUES ('2026-10-01T10:00:00Z', 'pdf', 'ok')");

        repository.initSchema();
        repository.initSchema();

        Long tokens = jdbc.queryForObject("SELECT input_tokens FROM usage_events", Long.class);
        assertThat(tokens).isZero();
        repository.insert(aiEvent("2026-10-02T10:00:00Z", 1200, 300));
        Long stored = jdbc.queryForObject(
            "SELECT output_tokens FROM usage_events WHERE type = 'ai_fill'", Long.class);
        assertThat(stored).isEqualTo(300);
    }

    @Test
    void rollupKeepsTokenTotals() {
        repository.initSchema();
        repository.insert(aiEvent("2026-09-01T10:00:00Z", 1000, 200));
        repository.insert(aiEvent("2026-09-01T11:00:00Z", 500, 100));

        repository.rollupBefore(LocalDate.parse("2026-10-01"));
        repository.insert(aiEvent("2026-09-01T12:00:00Z", 100, 10));
        repository.rollupBefore(LocalDate.parse("2026-10-01"));

        Long in = jdbc.queryForObject("SELECT input_tokens FROM usage_daily WHERE type = 'ai_fill'", Long.class);
        Long out = jdbc.queryForObject("SELECT output_tokens FROM usage_daily WHERE type = 'ai_fill'", Long.class);
        Long count = jdbc.queryForObject("SELECT count FROM usage_daily WHERE type = 'ai_fill'", Long.class);
        assertThat(in).isEqualTo(1600);
        assertThat(out).isEqualTo(310);
        assertThat(count).isEqualTo(3);
    }
}
