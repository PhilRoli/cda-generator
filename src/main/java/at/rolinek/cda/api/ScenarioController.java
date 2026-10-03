package at.rolinek.cda.api;

import at.rolinek.cda.security.LogSafe;
import at.rolinek.cda.scenario.ScenarioRecord;
import at.rolinek.cda.scenario.ScenarioService;
import at.rolinek.cda.usage.UsageRecorder;
import at.rolinek.cda.usage.UsageRecordingFilter;
import at.rolinek.cda.usage.UsageType;
import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
public class ScenarioController {

    private static final Logger LOG = LoggerFactory.getLogger(ScenarioController.class);

    private final ScenarioService scenarioService;
    private final UsageRecorder usageRecorder;

    public ScenarioController(ScenarioService scenarioService, UsageRecorder usageRecorder) {
        this.scenarioService = scenarioService;
        this.usageRecorder = usageRecorder;
    }

    @GetMapping("/scenarios")
    public List<ScenarioSummaryResponse> list(@RequestParam("username") @NotBlank String username) {
        return scenarioService.listByUsername(username).stream()
            .map(ScenarioSummaryResponse::from)
            .toList();
    }

    @GetMapping("/scenarios/all")
    public List<ScenarioSummaryResponse> listAll() {
        return scenarioService.listAll().stream()
            .map(ScenarioSummaryResponse::from)
            .toList();
    }

    @GetMapping("/scenarios/{id}")
    public ScenarioDetailResponse get(
            @PathVariable String id,
        @RequestParam(value = "username", required = false) String username,
        @RequestHeader(name = UsageRecordingFilter.USER_HEADER, required = false) String headerUser,
        HttpServletRequest httpRequest
    ) {
        boolean isPublic = (username == null || username.isBlank());
        ScenarioRecord record = isPublic
            ? scenarioService.getByIdPublic(id)
            : scenarioService.getByIdForUser(id, username);
        LOG.info("event=scenario_loaded ip={} id={} public={}", ClientIp.from(httpRequest), LogSafe.of(id), isPublic);
        usageRecorder.record(UsageType.SCENARIO_LOAD, 200, isPublic ? headerUser : username, httpRequest, record.id());
        return ScenarioDetailResponse.from(record, scenarioService.payloadToJson(record));
    }

    @PostMapping("/scenarios")
    public ScenarioSummaryResponse save(@RequestBody ScenarioSaveBody body, HttpServletRequest httpRequest) {
        String action = (body.id() == null || body.id().isBlank()) ? "created" : "updated";
        ScenarioRecord saved = scenarioService.saveForUser(
            new ScenarioService.ScenarioSaveRequest(body.id(), body.username(), body.title(), body.state())
        );
        LOG.info("event=scenario_saved ip={} user={} id={} title={} action={}", ClientIp.from(httpRequest), LogSafe.of(saved.username()), LogSafe.of(saved.id()), LogSafe.of(saved.title()), action);
        usageRecorder.record(UsageType.SCENARIO_SAVE, 200, saved.username(), httpRequest, saved.id());
        return ScenarioSummaryResponse.from(saved);
    }

    @GetMapping("/admin/scenarios")
    public List<ScenarioSummaryResponse> listForAdmin(
        @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return scenarioService.listAllForAdmin(authorization).stream()
            .map(ScenarioSummaryResponse::from)
            .toList();
    }

    @DeleteMapping("/admin/scenarios/{id}")
    public void deleteAsAdmin(
            @PathVariable String id,
        @RequestHeader(name = "Authorization", required = false) String authorization,
        HttpServletRequest httpRequest
    ) {
        scenarioService.adminDelete(id, authorization);
        LOG.info("event=scenario_admin_deleted ip={} id={}", ClientIp.from(httpRequest), LogSafe.of(id));
    }

    @GetMapping("/admin/scenarios/export")
    public ScenarioService.ExportResult exportScenarios(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        HttpServletRequest httpRequest
    ) {
        ScenarioService.ExportResult result = scenarioService.exportAll(authorization);
        LOG.info("event=scenarios_exported ip={} count={}", ClientIp.from(httpRequest), result.count());
        return result;
    }

    @PostMapping("/admin/scenarios/import")
    public ImportSummaryResponse importScenarios(
        @RequestBody(required = false) ImportBody body,
        @RequestHeader(name = "Authorization", required = false) String authorization,
        HttpServletRequest httpRequest
    ) {
        int imported = scenarioService.adminImport(body == null ? null : body.scenarios(), authorization);
        LOG.info("event=scenarios_imported ip={} count={}", ClientIp.from(httpRequest), imported);
        return new ImportSummaryResponse(imported);
    }

    public record ScenarioSaveBody(String id, String username, String title, JsonNode state) {}

    public record ImportBody(List<ScenarioService.ImportEntry> scenarios) {}

    public record ImportSummaryResponse(int imported) {}

    public record ScenarioSummaryResponse(
        String id,
        String username,
        String title,
        String createdAt,
        String updatedAt
    ) {
        static ScenarioSummaryResponse from(ScenarioRecord record) {
            return new ScenarioSummaryResponse(
                record.id(),
                record.username(),
                record.title(),
                record.createdAt(),
                record.updatedAt()
            );
        }
    }

    public record ScenarioDetailResponse(
        String id,
        String username,
        String title,
        JsonNode state,
        String createdAt,
        String updatedAt
    ) {
        static ScenarioDetailResponse from(ScenarioRecord record, JsonNode state) {
            return new ScenarioDetailResponse(
                record.id(),
                record.username(),
                record.title(),
                state,
                record.createdAt(),
                record.updatedAt()
            );
        }
    }
}
