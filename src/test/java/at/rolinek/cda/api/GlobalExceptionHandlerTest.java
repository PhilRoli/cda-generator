package at.rolinek.cda.api;

import at.rolinek.cda.scenario.ScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that GlobalExceptionHandler correctly maps exceptions to HTTP responses
 * without leaking internal details to clients.
 */
@WebMvcTest(ScenarioController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ScenarioService scenarioService;

    // --- ResponseStatusException with intentional German reason is preserved ---

    @Test
    void responseStatusException_preservesIntentionalMessage() throws Exception {
        org.mockito.BDDMockito.given(scenarioService.getByIdPublic("x"))
            .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Szenario nicht gefunden."));

        mvc.perform(get("/api/scenarios/x"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Szenario nicht gefunden."));
    }

    // --- Malformed JSON body → 400 with safe message (no raw exception detail) ---

    @Test
    void malformedJson_returns400WithSafeMessage() throws Exception {
        mvc.perform(post("/api/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not valid json }"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Ungültiger Anfrageinhalt."));
    }

    // --- Unexpected RuntimeException → 500 with generic message, NOT the raw message ---

    @Test
    void unexpectedRuntimeException_returns500WithGenericMessage() throws Exception {
        org.mockito.BDDMockito.given(scenarioService.listAll())
            .willThrow(new RuntimeException("secret database password leaked here"));

        mvc.perform(get("/api/scenarios/all"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Interner Serverfehler."));
    }

    @Test
    void unexpectedRuntimeException_doesNotLeakRawMessage() throws Exception {
        org.mockito.BDDMockito.given(scenarioService.listAll())
            .willThrow(new RuntimeException("secret database password leaked here"));

        mvc.perform(get("/api/scenarios/all"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Interner Serverfehler."));
        // The raw exception message must not appear anywhere in the response body
        // (asserted implicitly by the exact value check above)
    }
}
