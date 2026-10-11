package at.rolinek.cda.api;

import at.rolinek.cda.ai.AiBudgetService;
import at.rolinek.cda.scenario.BackupService;
import at.rolinek.cda.security.AdminTokenGuard;
import at.rolinek.cda.usage.StatsService;
import at.rolinek.cda.usage.UsageRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStatsController.class)
@Import(GlobalExceptionHandler.class)
class AdminStatsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean StatsService statsService;
    @MockitoBean BackupService backupService;
    @MockitoBean AdminTokenGuard adminTokenGuard;
    @MockitoBean UsageRecorder usageRecorder;
    @MockitoBean AiBudgetService aiBudgetService;

    @Test
    void summaryDefaultsTo30Days() throws Exception {
        given(statsService.summary(30)).willReturn(new StatsService.Summary(30,
            Map.of("pdf", new StatsService.Totals(3, 1)), Map.of("pdf", new StatsService.Totals(1, 0))));

        mvc.perform(get("/api/admin/stats/summary").header("Authorization", "Bearer t"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days").value(30))
            .andExpect(jsonPath("$.current.pdf.ok").value(3));
    }

    @Test
    void wrongTokenIs403AndNoStatsAreRead() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token."))
            .given(adminTokenGuard).require("Bearer wrong");

        mvc.perform(get("/api/admin/stats/users").header("Authorization", "Bearer wrong"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Ungültiger Admin-Token."));
        verifyNoInteractions(statsService);
    }

    @Test
    void nonNumericDaysIs400NotAServerError() throws Exception {
        mvc.perform(get("/api/admin/stats/timeline?days=abc").header("Authorization", "Bearer t"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Ungültiger Parameter: days"));
    }

    @Test
    void backupStatusIsReturned() throws Exception {
        given(backupService.latestBackup()).willReturn(
            new BackupService.BackupStatus("scenarios-20261003-031500.json", "2026-10-03T03:15:00Z", 12));

        mvc.perform(get("/api/admin/backup/status").header("Authorization", "Bearer t"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(12));
    }

    @Test
    void aiSpendIsReturnedForAdmins() throws Exception {
        given(aiBudgetService.monthSummary())
            .willReturn(new AiBudgetService.MonthSummary("2026-10", 12, 18000, 8400, 0.006, 40.0));

        mvc.perform(get("/api/admin/stats/ai").header("Authorization", "Bearer t"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value("2026-10"))
            .andExpect(jsonPath("$.fills").value(12))
            .andExpect(jsonPath("$.estimatedUsd").value(0.006))
            .andExpect(jsonPath("$.budgetUsd").value(40.0));
    }

    @Test
    void aiSpendRequiresTheAdminToken() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token."))
            .given(adminTokenGuard).require("Bearer wrong");

        mvc.perform(get("/api/admin/stats/ai").header("Authorization", "Bearer wrong"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(aiBudgetService);
    }
}
