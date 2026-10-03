package at.rolinek.cda.usage;

import at.rolinek.cda.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shapes the admin statistics responses from {@link StatsQueries}. */
@Service
public class StatsService {

    private static final int RECENT_FAILURES = 50;
    private static final int TOP_SCENARIOS = 10;

    public record Totals(long ok, long failed) {}
    public record Summary(int days, Map<String, Totals> current, Map<String, Totals> previous) {}
    public record Day(String day, Map<String, Long> ok, long failed) {}
    public record Timeline(int days, List<Day> entries) {}
    public record UserRow(String who, Map<String, Long> ok, String lastActivity) {}
    public record Users(int days, int detailWindowDays, List<UserRow> users) {}
    public record FailureGroup(String type, String reason, long count) {}
    public record FailureRow(String occurredAt, String type, String who, String reason, Integer httpStatus) {}
    public record Failures(int days, int detailWindowDays, List<FailureGroup> groups, List<FailureRow> recent) {}
    public record ScenarioUserRow(String who, long saves, long loads) {}
    public record TopScenario(String id, String title, long loads) {}
    public record ScenarioStats(int days, int detailWindowDays, List<ScenarioUserRow> perUser, List<TopScenario> top) {}

    private final StatsQueries queries;
    private final int retentionDays;
    private final Clock clock;

    public StatsService(StatsQueries queries, AppProperties properties, Clock clock) {
        this.queries = queries;
        this.retentionDays = properties.getUsage().getRetentionDays();
        this.clock = clock;
    }

    public Summary summary(int days) {
        StatsPeriod period = period(days);
        StatsPeriod previous = period.previous();
        return new Summary(days, totals(period), totals(previous));
    }

    public Timeline timeline(int days) {
        StatsPeriod period = period(days);
        Map<String, Map<String, Long>> ok = new LinkedHashMap<>();
        Map<String, Long> failed = new LinkedHashMap<>();
        for (LocalDate d = period.from(); d.isBefore(period.toExclusive()); d = d.plusDays(1)) {
            ok.put(d.toString(), zeroPerType());
            failed.put(d.toString(), 0L);
        }
        for (StatsQueries.DailyCount c : queries.dailyCounts(period.from(), period.toExclusive())) {
            if ("ok".equals(c.outcome())) {
                ok.get(c.day()).merge(c.type(), c.count(), Long::sum);
            } else {
                failed.merge(c.day(), c.count(), Long::sum);
            }
        }
        List<Day> entries = new ArrayList<>();
        ok.forEach((day, counts) -> entries.add(new Day(day, counts, failed.get(day))));
        return new Timeline(days, entries);
    }

    public Users users(int days) {
        LocalDate from = detailFrom(period(days));
        Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
        Map<String, String> last = new LinkedHashMap<>();
        for (StatsQueries.UserTypeCount row : queries.okCountsByUser(from)) {
            counts.computeIfAbsent(row.who(), k -> zeroPerType()).put(row.type(), row.count());
            last.merge(row.who(), row.last(), (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        List<UserRow> rows = new ArrayList<>();
        counts.forEach((who, c) -> rows.add(new UserRow(who, c, last.get(who))));
        rows.sort(Comparator.comparingLong((UserRow r) -> r.ok().values().stream().mapToLong(Long::longValue).sum())
            .reversed().thenComparing(UserRow::who));
        return new Users(days, retentionDays, rows);
    }

    public Failures failures(int days) {
        LocalDate from = detailFrom(period(days));
        List<FailureGroup> groups = queries.failureGroups(from).stream()
            .map(g -> new FailureGroup(g.type(), g.reason(), g.count())).toList();
        List<FailureRow> recent = queries.recentFailures(from, RECENT_FAILURES).stream()
            .map(f -> new FailureRow(f.occurredAt(), f.type(), f.who(), f.reason(), f.httpStatus())).toList();
        return new Failures(days, retentionDays, groups, recent);
    }

    public ScenarioStats scenarios(int days) {
        LocalDate from = detailFrom(period(days));
        Map<String, long[]> perUser = new LinkedHashMap<>();
        for (StatsQueries.UserScenarioCount row : queries.scenarioCountsByUser(from)) {
            long[] c = perUser.computeIfAbsent(row.who(), k -> new long[2]);
            c["scenario_save".equals(row.type()) ? 0 : 1] += row.count();
        }
        List<ScenarioUserRow> users = new ArrayList<>();
        perUser.forEach((who, c) -> users.add(new ScenarioUserRow(who, c[0], c[1])));
        users.sort(Comparator.comparingLong((ScenarioUserRow r) -> r.saves() + r.loads()).reversed()
            .thenComparing(ScenarioUserRow::who));
        List<TopScenario> top = queries.topLoadedScenarios(from, TOP_SCENARIOS).stream()
            .map(t -> new TopScenario(t.id(), t.title(), t.loads())).toList();
        return new ScenarioStats(days, retentionDays, users, top);
    }

    private StatsPeriod period(int days) {
        return StatsPeriod.of(days, LocalDate.now(clock));
    }

    private LocalDate detailFrom(StatsPeriod period) {
        LocalDate windowStart = LocalDate.now(clock).minusDays(retentionDays - 1L);
        return period.from().isAfter(windowStart) ? period.from() : windowStart;
    }

    private Map<String, Totals> totals(StatsPeriod period) {
        Map<String, long[]> sums = new LinkedHashMap<>();
        for (UsageType t : UsageType.values()) {
            sums.put(t.wireName(), new long[2]);
        }
        for (StatsQueries.DailyCount c : queries.dailyCounts(period.from(), period.toExclusive())) {
            long[] s = sums.get(c.type());
            if (s != null) {
                s["ok".equals(c.outcome()) ? 0 : 1] += c.count();
            }
        }
        Map<String, Totals> result = new LinkedHashMap<>();
        sums.forEach((type, s) -> result.put(type, new Totals(s[0], s[1])));
        return result;
    }

    private static Map<String, Long> zeroPerType() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (UsageType t : UsageType.values()) {
            m.put(t.wireName(), 0L);
        }
        return m;
    }
}
