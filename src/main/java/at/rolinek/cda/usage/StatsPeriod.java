package at.rolinek.cda.usage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Set;

/** A statistics period of {@code days} UTC days ending today (inclusive). */
public record StatsPeriod(int days, LocalDate from, LocalDate toExclusive) {

    private static final Set<Integer> ALLOWED = Set.of(7, 30, 90, 365);

    public static StatsPeriod of(int days, LocalDate today) {
        if (!ALLOWED.contains(days)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Zeitraum.");
        }
        return new StatsPeriod(days, today.minusDays(days - 1L), today.plusDays(1));
    }

    public StatsPeriod previous() {
        return new StatsPeriod(days, from.minusDays(days), from);
    }
}
