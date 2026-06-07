package at.rolinek.cda.api;

import at.rolinek.cda.scenario.ScenarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScenarioController.class)
@Import(GlobalExceptionHandler.class)
class ScenarioControllerAdminTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ScenarioService scenarioService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------------------------------------------------------
    // A) Export endpoint
    // -----------------------------------------------------------------------

    @Test
    void export_withValidToken_returns200AndEnvelope() throws Exception {
        ScenarioService.ExportResult result = new ScenarioService.ExportResult(
            "2026-01-01T00:00:00Z", 0, List.of());
        given(scenarioService.exportAll("Bearer secret")).willReturn(result);

        mvc.perform(get("/api/admin/scenarios/export")
                .header("Authorization", "Bearer secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.exportedAt").value("2026-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.scenarios").isArray());
    }

    @Test
    void export_withoutToken_returns403() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token."))
            .given(scenarioService).exportAll(any());

        mvc.perform(get("/api/admin/scenarios/export"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Ungültiger Admin-Token."));
    }

    @Test
    void export_withWrongToken_returns403() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token."))
            .given(scenarioService).exportAll(eq("Bearer wrong"));

        mvc.perform(get("/api/admin/scenarios/export")
                .header("Authorization", "Bearer wrong"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Ungültiger Admin-Token."));
    }

    // -----------------------------------------------------------------------
    // B) Import endpoint
    // -----------------------------------------------------------------------

    @Test
    void import_withValidTokenAndScenarios_returns200AndCount() throws Exception {
        given(scenarioService.adminImport(any(), eq("Bearer secret"))).willReturn(2);

        String body = """
            {
              "scenarios": [
                {"id":"id1","username":"u","title":"T","state":{},"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"},
                {"id":"id2","username":"u","title":"T2","state":{},"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}
              ]
            }
            """;

        mvc.perform(post("/api/admin/scenarios/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(2));
    }

    @Test
    void import_withoutToken_returns403() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token."))
            .given(scenarioService).adminImport(any(), any());

        String body = """
            {"scenarios": [{"id":"id1","username":"u","title":"T","state":{},"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}]}
            """;

        mvc.perform(post("/api/admin/scenarios/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void import_withNullScenariosField_returns400() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keine Szenarien zum Importieren."))
            .given(scenarioService).adminImport(eq(null), eq("Bearer secret"));

        mvc.perform(post("/api/admin/scenarios/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenarios\": null}")
                .header("Authorization", "Bearer secret"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void import_withEmptyBody_returns400() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keine Szenarien zum Importieren."))
            .given(scenarioService).adminImport(eq(null), eq("Bearer secret"));

        mvc.perform(post("/api/admin/scenarios/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("Authorization", "Bearer secret"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void import_withNoTokenAndNullBody_returns403BeforeValidation() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token."))
            .given(scenarioService).adminImport(eq(null), eq(null));

        mvc.perform(post("/api/admin/scenarios/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenarios\": null}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void import_withMalformedJson_returns400() throws Exception {
        mvc.perform(post("/api/admin/scenarios/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not-json")
                .header("Authorization", "Bearer secret"))
            .andExpect(status().isBadRequest());
    }
}
