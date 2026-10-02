package at.rolinek.cda.scenario;

import at.rolinek.cda.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ScenarioServiceSaveTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ScenarioRepository repository;
    private ScenarioService service;

    @BeforeEach
    void setUp() {
        repository = mock(ScenarioRepository.class);
        AppProperties props = new AppProperties();
        props.setMaxScenarioBytes(100);
        service = new ScenarioService(repository, objectMapper, props);
    }

    private ScenarioService.ScenarioSaveRequest requestWithText(String text) {
        ObjectNode state = objectMapper.createObjectNode().put("text", text);
        return new ScenarioService.ScenarioSaveRequest(null, "alice", "Titel", state);
    }

    @Test
    void oversizedScenario_isRejectedWith413AndNotStored() {
        assertThatThrownBy(() -> service.saveForUser(requestWithText("x".repeat(200))))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                assertThat(ex.getReason()).isEqualTo("Szenario ist zu groß.");
            });
        verify(repository, never()).insert(any());
    }

    @Test
    void scenarioWithinLimit_isStored() {
        service.saveForUser(requestWithText("kurz"));
        verify(repository).insert(any());
    }
}
