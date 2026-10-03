package at.rolinek.cda.api;

import at.rolinek.cda.scenario.BackupService;
import at.rolinek.cda.security.AdminTokenGuard;
import at.rolinek.cda.usage.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only usage statistics for the admin page. */
@RestController
@RequestMapping("/api/admin")
public class AdminStatsController {

    private final StatsService stats;
    private final BackupService backupService;
    private final AdminTokenGuard guard;

    public AdminStatsController(StatsService stats, BackupService backupService, AdminTokenGuard guard) {
        this.stats = stats;
        this.backupService = backupService;
        this.guard = guard;
    }

    @GetMapping("/stats/summary")
    public StatsService.Summary summary(@RequestHeader(name = "Authorization", required = false) String auth,
                                        @RequestParam(defaultValue = "30") int days) {
        guard.require(auth);
        return stats.summary(days);
    }

    @GetMapping("/stats/timeline")
    public StatsService.Timeline timeline(@RequestHeader(name = "Authorization", required = false) String auth,
                                          @RequestParam(defaultValue = "30") int days) {
        guard.require(auth);
        return stats.timeline(days);
    }

    @GetMapping("/stats/users")
    public StatsService.Users users(@RequestHeader(name = "Authorization", required = false) String auth,
                                    @RequestParam(defaultValue = "30") int days) {
        guard.require(auth);
        return stats.users(days);
    }

    @GetMapping("/stats/failures")
    public StatsService.Failures failures(@RequestHeader(name = "Authorization", required = false) String auth,
                                          @RequestParam(defaultValue = "30") int days) {
        guard.require(auth);
        return stats.failures(days);
    }

    @GetMapping("/stats/scenarios")
    public StatsService.ScenarioStats scenarios(@RequestHeader(name = "Authorization", required = false) String auth,
                                                @RequestParam(defaultValue = "30") int days) {
        guard.require(auth);
        return stats.scenarios(days);
    }

    @GetMapping("/backup/status")
    public BackupService.BackupStatus backupStatus(@RequestHeader(name = "Authorization", required = false) String auth) {
        guard.require(auth);
        return backupService.latestBackup();
    }
}
