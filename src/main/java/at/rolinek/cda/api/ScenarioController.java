package at.rolinek.cda.api;

import at.rolinek.cda.scenario.ScenarioRecord;
import at.rolinek.cda.scenario.ScenarioService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
public class ScenarioController {
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
        @RequestParam(value = "username", required = false) String username
    ) {
        ScenarioRecord record = (username != null && !username.isBlank())
            ? scenarioService.getByIdForUser(id, username)
            : scenarioService.getByIdPublic(id);
        return ScenarioDetailResponse.from(record, scenarioService.payloadToJson(record));
    }

    @PostMapping("/scenarios")
    public ScenarioSummaryResponse save(@RequestBody ScenarioSaveBody body) {
        ScenarioRecord saved = scenarioService.saveForUser(
            new ScenarioService.ScenarioSaveRequest(body.id(), body.username(), body.title(), body.state())
        );
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
        @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        scenarioService.adminDelete(id, authorization);
    }

    @GetMapping("/admin/scenarios/export")
    public ScenarioService.ExportResult exportScenarios(
        @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return scenarioService.exportAll(authorization);
    }

    @PostMapping("/admin/scenarios/import")
    public ImportSummaryResponse importScenarios(
        @RequestBody(required = false) ImportBody body,
        @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        if (body == null || body.scenarios() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importdaten fehlen.");
        }
        int imported = scenarioService.adminImport(body.scenarios(), authorization);
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
