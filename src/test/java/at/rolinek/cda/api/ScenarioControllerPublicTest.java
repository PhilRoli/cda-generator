package at.rolinek.cda.api;

import at.rolinek.cda.scenario.ScenarioRecord;
import at.rolinek.cda.scenario.ScenarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerPublicTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ScenarioService scenarioService;

    private static final ScenarioRecord SAMPLE = new ScenarioRecord(
        "id1", "testuser", "Szenario A", "{}", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"
    );

    @Test
    void listAll_returnsAllScenariosWithoutAuth() throws Exception {
        given(scenarioService.listAll()).willReturn(List.of(SAMPLE));

        mvc.perform(get("/api/scenarios/all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("testuser"))
            .andExpect(jsonPath("$[0].title").value("Szenario A"));
    }

    @Test
    void getById_withoutUsername_returnsPublicScenario() throws Exception {
        given(scenarioService.getByIdPublic("id1")).willReturn(SAMPLE);
        given(scenarioService.payloadToJson(any())).willReturn(new ObjectMapper().readTree("{}"));

        mvc.perform(get("/api/scenarios/id1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.title").value("Szenario A"));
    }

    @Test
    void getById_withUsername_delegatesToOwnerCheck() throws Exception {
        given(scenarioService.getByIdForUser("id1", "testuser")).willReturn(SAMPLE);
        given(scenarioService.payloadToJson(any())).willReturn(new ObjectMapper().readTree("{}"));

        mvc.perform(get("/api/scenarios/id1").param("username", "testuser"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        given(scenarioService.getByIdPublic("missing"))
            .willThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Szenario nicht gefunden."));

        mvc.perform(get("/api/scenarios/missing"))
            .andExpect(status().isNotFound());
    }
}
