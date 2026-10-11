package at.rolinek.cda.ai;

import at.rolinek.cda.config.AppProperties;
import at.rolinek.cda.usage.StatsQueries;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;

/** Decides whether an AI fill may run: feature on, monthly spend cap, per-user daily cap. */
@Service
public class AiBudgetService {

    public record MonthSummary(String month, long fills, long inputTokens, long outputTokens,
                               double estimatedUsd, double budgetUsd) {}

    private final StatsQueries queries;
    private final AppProperties.Ai ai;
    private final Clock clock;

    public AiBudgetService(StatsQueries queries, AppProperties properties, Clock clock) {
        this.queries = queries;
        this.ai = properties.getAi();
        this.clock = clock;
    }

    public int maxPromptChars() {
        return ai.getMaxPromptChars();
    }

    public boolean enabled() {
        return ai.enabled() && !monthlyBudgetReached();
    }

    /** @throws ResponseStatusException 503 if the feature is off or the month's budget is spent, 429 at the daily cap */
    public void checkAllowed(String username, String ipPrefix) {
        if (!ai.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Die KI-Funktion ist nicht aktiviert.");
        }
        if (monthlyBudgetReached()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Das KI-Kontingent für diesen Monat ist aufgebraucht.");
        }
        long used = queries.aiAttemptsOn(LocalDate.now(clock), username == null ? "" : username, ipPrefix);
        if (used >= ai.getUserDailyLimit()) {
            String hint = (username == null || username.isEmpty())
                ? " Ohne Benutzernamen teilen sich alle Nutzer im selben Netz ein Limit; mit eingegebenem Benutzernamen (Szenarien) gilt ein eigenes."
                : "";
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Tageslimit für KI-Anfragen erreicht. Bitte morgen erneut versuchen." + hint);
        }
    }

    public MonthSummary monthSummary() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.withDayOfMonth(1);
        StatsQueries.AiMonthTotals totals = queries.aiTotals(from, from.plusMonths(1));
        return new MonthSummary(from.toString().substring(0, 7), totals.fills(), totals.inputTokens(),
            totals.outputTokens(), ai.costUsd(totals.inputTokens(), totals.outputTokens()), ai.getMonthlyBudgetUsd());
    }

    private boolean monthlyBudgetReached() {
        MonthSummary summary = monthSummary();
        return summary.estimatedUsd() >= summary.budgetUsd();
    }
}
