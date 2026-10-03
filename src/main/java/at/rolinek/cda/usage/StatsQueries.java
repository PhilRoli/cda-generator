package at.rolinek.cda.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** Read-side SQL for the admin statistics. Day bounds are 'YYYY-MM-DD' strings (UTC). */
@Repository
public class StatsQueries {

    private static final String WHO =
        "CASE WHEN e.username <> '' THEN e.username "
            + "WHEN e.ip_prefix <> '' THEN 'anonym · ' || e.ip_prefix ELSE 'anonym' END";

    private final JdbcTemplate jdbc;

    public StatsQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record DailyCount(String day, String type, String outcome, long count) {}
    record UserTypeCount(String who, String type, long count, String last) {}
    record FailureGroupRow(String type, String reason, long count) {}
    record FailureEventRow(String occurredAt, String type, String who, String reason, Integer httpStatus) {}
    record UserScenarioCount(String who, String type, long count) {}
    record TopScenarioRow(String id, String title, long loads) {}

    /** Counts per day/type/outcome from detailed events and rolled-up daily totals. */
    List<DailyCount> dailyCounts(LocalDate from, LocalDate toExclusive) {
        return jdbc.query("""
                SELECT substr(occurred_at, 1, 10) AS day, type, outcome, COUNT(*) AS c
                FROM usage_events WHERE occurred_at >= ? AND occurred_at < ?
                GROUP BY 1, 2, 3
                UNION ALL
                SELECT day, type, outcome, count FROM usage_daily WHERE day >= ? AND day < ?
                """,
            (rs, i) -> new DailyCount(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)),
            from.toString(), toExclusive.toString(), from.toString(), toExclusive.toString());
    }

    List<UserTypeCount> okCountsByUser(LocalDate from) {
        return jdbc.query("SELECT " + WHO + " AS who, e.type, COUNT(*), MAX(e.occurred_at) "
                + "FROM usage_events e WHERE e.outcome = 'ok' AND e.occurred_at >= ? "
                + "AND e.type IN ('pdf', 'clean_pdf', 'xml_download') GROUP BY who, e.type",
            (rs, i) -> new UserTypeCount(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4)),
            from.toString());
    }

    List<FailureGroupRow> failureGroups(LocalDate from) {
        return jdbc.query("SELECT e.type, e.detail, COUNT(*) FROM usage_events e "
                + "WHERE e.outcome = 'failed' AND e.occurred_at >= ? GROUP BY e.type, e.detail ORDER BY 3 DESC",
            (rs, i) -> new FailureGroupRow(rs.getString(1), rs.getString(2), rs.getLong(3)),
            from.toString());
    }

    List<FailureEventRow> recentFailures(LocalDate from, int limit) {
        return jdbc.query("SELECT e.occurred_at, e.type, " + WHO + ", e.detail, e.http_status FROM usage_events e "
                + "WHERE e.outcome = 'failed' AND e.occurred_at >= ? ORDER BY e.occurred_at DESC, e.id DESC LIMIT ?",
            (rs, i) -> new FailureEventRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                (Integer) rs.getObject(5)),
            from.toString(), limit);
    }

    List<UserScenarioCount> scenarioCountsByUser(LocalDate from) {
        return jdbc.query("SELECT " + WHO + " AS who, e.type, COUNT(*) FROM usage_events e "
                + "WHERE e.outcome = 'ok' AND e.occurred_at >= ? AND e.type IN ('scenario_save', 'scenario_load') "
                + "GROUP BY who, e.type",
            (rs, i) -> new UserScenarioCount(rs.getString(1), rs.getString(2), rs.getLong(3)),
            from.toString());
    }

    List<TopScenarioRow> topLoadedScenarios(LocalDate from, int limit) {
        return jdbc.query("""
                SELECT e.detail, COALESCE(s.title, '(gelöscht)'), COUNT(*) AS c
                FROM usage_events e LEFT JOIN scenarios s ON s.id = e.detail
                WHERE e.type = 'scenario_load' AND e.outcome = 'ok' AND e.occurred_at >= ?
                GROUP BY e.detail ORDER BY c DESC, e.detail LIMIT ?
                """,
            (rs, i) -> new TopScenarioRow(rs.getString(1), rs.getString(2), rs.getLong(3)),
            from.toString(), limit);
    }
}
