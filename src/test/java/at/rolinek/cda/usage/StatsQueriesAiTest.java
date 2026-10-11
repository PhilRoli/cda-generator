package at.rolinek.cda.usage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StatsQueriesAiTest {

    private Path db;
    private UsageRepository repository;
    private StatsQueries queries;

    @BeforeEach
    void setUp() throws Exception {
        db = Files.createTempFile("stats-ai-test-", ".db");
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + db.toAbsolutePath());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        repository = new UsageRepository(jdbc);
        repository.initSchema();
        queries = new StatsQueries(jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(db);
    }

    private void insert(String at, boolean ok, int status, String user, String ip, String detail, long in, long out) {
        repository.insert(new UsageEvent(Instant.parse(at), UsageType.AI_FILL, ok, status, user, ip, detail, in, out));
    }

    @Test
    void attemptsAreKeyedByUserOrAnonymousIpAndBoundedByUtcDay() {
        LocalDate day = LocalDate.parse("2026-10-11");
        insert("2026-10-10T23:59:59Z", true, 200, "anna", "10.0.0.0", "", 1, 1);   // yesterday
        insert("2026-10-11T00:00:00Z", true, 200, "anna", "10.0.0.0", "", 1, 1);   // counts
        insert("2026-10-11T08:00:00.500Z", true, 200, "anna", "10.9.9.0", "", 1, 1); // counts (other IP, same user)
        insert("2026-10-11T09:00:00Z", false, 429, "anna", "10.0.0.0", "throttled", 0, 0); // never counts
        insert("2026-10-11T10:00:00Z", true, 200, "", "198.51.100.0", "", 1, 1);   // anonymous
        insert("2026-10-11T10:05:00Z", true, 200, "", "198.51.100.0", "", 1, 1);   // anonymous
        insert("2026-10-11T10:10:00Z", true, 200, "", "198.51.101.0", "", 1, 1);   // other anonymous
        insert("2026-10-12T00:00:00Z", true, 200, "anna", "10.0.0.0", "", 1, 1);   // tomorrow

        assertThat(queries.aiAttemptsOn(day, "anna", "10.0.0.0")).isEqualTo(2);
        assertThat(queries.aiAttemptsOn(day, "", "198.51.100.0")).isEqualTo(2);
        assertThat(queries.aiAttemptsOn(day, "", "198.51.101.0")).isEqualTo(1);
        assertThat(queries.aiAttemptsOn(day, "bob", "10.0.0.0")).isZero();
    }

    @Test
    void monthTotalsCombineEventsAndRolledUpDays() {
        insert("2026-10-02T10:00:00Z", true, 200, "anna", "10.0.0.0", "", 1000, 200);
        insert("2026-10-03T10:00:00Z", false, 502, "anna", "10.0.0.0", "server_error", 0, 0);
        insert("2026-09-30T23:59:59Z", true, 200, "anna", "10.0.0.0", "", 5000, 5000); // previous month
        repository.rollupBefore(LocalDate.parse("2026-10-03")); // folds 09-30 and 10-02

        StatsQueries.AiMonthTotals totals = queries.aiTotals(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-01"));

        assertThat(totals.fills()).isEqualTo(1);
        assertThat(totals.inputTokens()).isEqualTo(1000);
        assertThat(totals.outputTokens()).isEqualTo(200);
    }
}
