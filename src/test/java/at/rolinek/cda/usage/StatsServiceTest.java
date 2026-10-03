package at.rolinek.cda.usage;

import at.rolinek.cda.config.AppProperties;
import at.rolinek.cda.scenario.ScenarioRecord;
import at.rolinek.cda.scenario.ScenarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatsServiceTest {

    private Path db;
    private JdbcTemplate jdbc;
    private UsageRepository usage;
    private StatsService stats;

    @BeforeEach
    void setUp() throws Exception {
        db = Files.createTempFile("stats-test-", ".db");
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + db.toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        usage = new UsageRepository(jdbc);
        usage.initSchema();
        ScenarioRepository scenarios = new ScenarioRepository(jdbc);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS scenarios (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                title TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """);
        scenarios.insert(new ScenarioRecord("s1", "anna", "Sturz Hüfte", "{}", "2026-09-01T00:00:00Z", "2026-09-01T00:00:00Z"));
        Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
        stats = new StatsService(new StatsQueries(jdbc), new AppProperties(), clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (String suffix : new String[] {"", "-wal", "-shm"}) {
            Files.deleteIfExists(Path.of(db + suffix));
        }
    }

    private void add(String at, UsageType type, boolean ok, String user, String ip, String detail) {
        usage.insert(new UsageEvent(Instant.parse(at), type, ok, ok ? 200 : 503, user, ip, detail));
    }

    @Test
    void summaryComparesWithThePreviousPeriod() {
        add("2026-10-03T08:00:00Z", UsageType.PDF, true, "anna", "203.0.113.0", "");
        add("2026-09-27T23:59:59.999Z", UsageType.PDF, true, "anna", "203.0.113.0", "");   // first day of the 7-day period
        add("2026-09-26T10:00:00Z", UsageType.PDF, true, "anna", "203.0.113.0", "");      // previous period
        add("2026-10-02T10:00:00Z", UsageType.PDF, false, "", "198.51.100.0", "busy_or_timeout");
        jdbc.update("INSERT INTO usage_daily(day, type, outcome, count) VALUES ('2026-09-22','pdf','ok',4)"); // previous, rolled up

        StatsService.Summary s = stats.summary(7);

        assertThat(s.current().get("pdf")).isEqualTo(new StatsService.Totals(2, 1));
        assertThat(s.previous().get("pdf")).isEqualTo(new StatsService.Totals(5, 0));
        assertThat(s.current().keySet()).containsExactlyInAnyOrder(
            "pdf", "clean_pdf", "xml_download", "scenario_save", "scenario_load");
    }

    @Test
    void timelineHasOneEntryPerDayEndingToday() {
        add("2026-10-03T00:00:00Z", UsageType.PDF, true, "anna", "", "");
        add("2026-10-01T23:59:59.999Z", UsageType.XML_DOWNLOAD, true, "", "203.0.113.0", "");
        add("2026-10-01T12:00:00Z", UsageType.PDF, false, "", "203.0.113.0", "bad_request");

        StatsService.Timeline t = stats.timeline(7);

        assertThat(t.entries()).hasSize(7);
        assertThat(t.entries().get(0).day()).isEqualTo("2026-09-27");
        assertThat(t.entries().get(6).day()).isEqualTo("2026-10-03");
        assertThat(t.entries().get(6).ok().get("pdf")).isEqualTo(1L);
        assertThat(t.entries().get(4).ok().get("xml_download")).isEqualTo(1L);
        assertThat(t.entries().get(4).failed()).isEqualTo(1L);
        assertThat(t.entries().get(5).ok().get("pdf")).isZero();
    }

    @Test
    void usersGroupAnonymousByIpPrefix() {
        add("2026-10-02T10:00:00Z", UsageType.PDF, true, "anna", "203.0.113.0", "");
        add("2026-10-03T09:00:00Z", UsageType.PDF, true, "anna", "203.0.113.0", "");
        add("2026-10-03T10:00:00Z", UsageType.XML_DOWNLOAD, true, "", "198.51.100.0", "");
        add("2026-10-03T11:00:00Z", UsageType.PDF, false, "anna", "203.0.113.0", "bad_request"); // failures don't count

        StatsService.Users u = stats.users(30);

        assertThat(u.detailWindowDays()).isEqualTo(90);
        assertThat(u.users()).extracting(StatsService.UserRow::who).containsExactly("anna", "anonym · 198.51.100.0");
        assertThat(u.users().get(0).ok().get("pdf")).isEqualTo(2L);
        assertThat(u.users().get(0).lastActivity()).startsWith("2026-10-03T09:00");
    }

    @Test
    void failuresAreGroupedAndListedNewestFirst() {
        add("2026-10-01T10:00:00Z", UsageType.PDF, false, "anna", "203.0.113.0", "busy_or_timeout");
        add("2026-10-02T10:00:00Z", UsageType.PDF, false, "", "198.51.100.0", "busy_or_timeout");
        add("2026-10-03T10:00:00Z", UsageType.CLEAN_PDF, false, "", "198.51.100.0", "throttled");

        StatsService.Failures f = stats.failures(30);

        assertThat(f.groups()).contains(new StatsService.FailureGroup("pdf", "busy_or_timeout", 2));
        assertThat(f.recent()).extracting(StatsService.FailureRow::reason)
            .containsExactly("throttled", "busy_or_timeout", "busy_or_timeout");
    }

    @Test
    void scenarioStatsUseCurrentTitlesAndMarkDeletedOnes() {
        add("2026-10-02T10:00:00Z", UsageType.SCENARIO_LOAD, true, "philipp", "", "s1");
        add("2026-10-02T11:00:00Z", UsageType.SCENARIO_LOAD, true, "", "203.0.113.0", "s1");
        add("2026-10-02T12:00:00Z", UsageType.SCENARIO_LOAD, true, "philipp", "", "gone");
        add("2026-10-02T13:00:00Z", UsageType.SCENARIO_SAVE, true, "philipp", "", "s2");

        StatsService.ScenarioStats s = stats.scenarios(30);

        assertThat(s.top()).containsExactly(
            new StatsService.TopScenario("s1", "Sturz Hüfte", 2),
            new StatsService.TopScenario("gone", "(gelöscht)", 1));
        assertThat(s.perUser()).contains(new StatsService.ScenarioUserRow("philipp", 1, 2));
    }

    @Test
    void detailQueriesAreClampedToTheRetentionWindow() {
        add("2026-06-30T10:00:00Z", UsageType.PDF, true, "alt", "", "");   // day before the window
        add("2026-07-06T00:00:00Z", UsageType.PDF, true, "neu", "", "");   // first day inside the window
        add("2026-06-30T10:00:00Z", UsageType.PDF, false, "alt", "", "old_failure");
        add("2026-07-06T00:00:00Z", UsageType.PDF, false, "neu", "", "new_failure");

        StatsService.Users u = stats.users(365);
        StatsService.Failures f = stats.failures(365);

        assertThat(u.detailWindowDays()).isEqualTo(90);
        assertThat(u.users()).extracting(StatsService.UserRow::who).containsExactly("neu");
        assertThat(f.recent()).extracting(StatsService.FailureRow::reason).containsExactly("new_failure");
        assertThat(f.groups()).extracting(StatsService.FailureGroup::reason).containsExactly("new_failure");
    }

    @Test
    void timelineUnionsDetailedEventsAndRolledUpTotals() {
        jdbc.update("INSERT INTO usage_daily(day, type, outcome, count) VALUES ('2026-09-22','pdf','ok',4)");
        jdbc.update("INSERT INTO usage_daily(day, type, outcome, count) VALUES ('2026-09-10','pdf','failed',2)");
        add("2026-09-22T10:00:00Z", UsageType.PDF, true, "anna", "", "");
        add("2026-09-30T10:00:00Z", UsageType.PDF, true, "anna", "", "");

        StatsService.Timeline t = stats.timeline(30);
        java.util.Map<String, StatsService.Day> byDay = new java.util.HashMap<>();
        t.entries().forEach(d -> byDay.put(d.day(), d));

        assertThat(byDay.get("2026-09-22").ok().get("pdf")).isEqualTo(5L);
        assertThat(byDay.get("2026-09-30").ok().get("pdf")).isEqualTo(1L);
        assertThat(byDay.get("2026-09-10").failed()).isEqualTo(2L);
    }

    @Test
    void emptyDatabaseYieldsZerosNotErrors() {
        assertThat(stats.summary(30).current().get("pdf")).isEqualTo(new StatsService.Totals(0, 0));
        assertThat(stats.timeline(30).entries()).hasSize(30);
        assertThat(stats.users(30).users()).isEmpty();
    }

    @Test
    void onlyTheAllowedPeriodsAreAccepted() {
        for (int days : new int[] {0, 1, 8, 366, -7}) {
            assertThatThrownBy(() -> stats.summary(days))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ungültiger Zeitraum.");
        }
    }
}
