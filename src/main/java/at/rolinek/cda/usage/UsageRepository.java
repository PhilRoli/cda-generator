package at.rolinek.cda.usage;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Writes usage events and folds old ones into anonymous daily totals. */
@Repository
public class UsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public UsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS usage_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at TEXT NOT NULL,
                type TEXT NOT NULL,
                outcome TEXT NOT NULL,
                http_status INTEGER,
                username TEXT NOT NULL DEFAULT '',
                ip_prefix TEXT NOT NULL DEFAULT '',
                detail TEXT NOT NULL DEFAULT ''
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_usage_events_time ON usage_events(occurred_at)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS usage_daily (
                day TEXT NOT NULL,
                type TEXT NOT NULL,
                outcome TEXT NOT NULL,
                count INTEGER NOT NULL,
                PRIMARY KEY (day, type, outcome)
            )
            """);
    }

    public void insert(UsageEvent event) {
        jdbcTemplate.update("""
                INSERT INTO usage_events (occurred_at, type, outcome, http_status, username, ip_prefix, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            event.occurredAt().toString(),
            event.type().wireName(),
            event.ok() ? "ok" : "failed",
            event.httpStatus(),
            event.username(),
            event.ipPrefix(),
            event.detail());
    }

    /**
     * Folds every event before {@code cutoffDay} (UTC) into usage_daily and deletes it, in
     * one transaction: nothing is deleted unless its aggregate was written.
     *
     * @return number of events folded
     */
    @Transactional
    public int rollupBefore(LocalDate cutoffDay) {
        String cutoff = cutoffDay.toString();
        jdbcTemplate.update("""
                INSERT INTO usage_daily (day, type, outcome, count)
                SELECT substr(occurred_at, 1, 10), type, outcome, COUNT(*)
                FROM usage_events WHERE occurred_at < ?
                GROUP BY substr(occurred_at, 1, 10), type, outcome
                ON CONFLICT(day, type, outcome) DO UPDATE SET count = count + excluded.count
                """, cutoff);
        return jdbcTemplate.update("DELETE FROM usage_events WHERE occurred_at < ?", cutoff);
    }
}
