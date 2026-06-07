package at.rolinek.cda.api;

import at.rolinek.cda.scenario.ScenarioRecord;
import at.rolinek.cda.scenario.ScenarioService;
import com.fasterxml.jackson.databind.JsonNode;
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

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
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
        HttpServletRequest httpRequest
    ) {
        boolean isPublic = (username == null || username.isBlank());
        ScenarioRecord record = isPublic
            ? scenarioService.getByIdPublic(id)
            : scenarioService.getByIdForUser(id, username);
        LOG.info("event=scenario_loaded ip={} id={} public={}", ClientIp.from(httpRequest), id, isPublic);
        return ScenarioDetailResponse.from(record, scenarioService.payloadToJson(record));
    }

    @PostMapping("/scenarios")
    public ScenarioSummaryResponse save(@RequestBody ScenarioSaveBody body, HttpServletRequest httpRequest) {
        String action = (body.id() == null || body.id().isBlank()) ? "created" : "updated";
        ScenarioRecord saved = scenarioService.saveForUser(
            new ScenarioService.ScenarioSaveRequest(body.id(), body.username(), body.title(), body.state())
        );
        LOG.info("event=scenario_saved ip={} user={} id={} title={} action={}", ClientIp.from(httpRequest), saved.username(), saved.id(), saved.title(), action);
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
        LOG.info("event=scenario_admin_deleted ip={} id={}", ClientIp.from(httpRequest), id);
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
