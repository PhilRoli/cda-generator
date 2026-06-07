package at.rolinek.cda.scenario;

import at.rolinek.cda.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class ScenarioServiceImportTest {

    private ScenarioRepository repository;
    private ScenarioService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository = mock(ScenarioRepository.class);
        objectMapper = new ObjectMapper();
        AppProperties props = new AppProperties();
        props.setAdminToken("secret");
        service = new ScenarioService(repository, objectMapper, props);
    }

    // -----------------------------------------------------------------------
    // adminImport – auth guard
    // -----------------------------------------------------------------------

    @Test
    void adminImport_withoutToken_throws403() {
        List<ScenarioService.ImportEntry> entries = sampleEntries();
        assertThatThrownBy(() -> service.adminImport(entries, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminImport_withWrongToken_throws403() {
        List<ScenarioService.ImportEntry> entries = sampleEntries();
        assertThatThrownBy(() -> service.adminImport(entries, "Bearer wrong"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminImport_withEmptyList_throws400() {
        assertThatThrownBy(() -> service.adminImport(Collections.emptyList(), "Bearer secret"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminImport_withNullList_throws400() {
        assertThatThrownBy(() -> service.adminImport(null, "Bearer secret"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminImport_callsUpsertForEachEntry() throws Exception {
        List<ScenarioService.ImportEntry> entries = sampleEntries(3);
        int count = service.adminImport(entries, "Bearer secret");
        assertThat(count).isEqualTo(3);
        then(repository).should(times(3)).upsert(any(ScenarioRecord.class));
    }

    @Test
    void adminImport_preservesIdAndTimestamps() throws Exception {
        ScenarioService.ImportEntry entry = new ScenarioService.ImportEntry(
            "fixed-id", "bob", "My Title",
            objectMapper.readTree("{\"x\":1}"),
            "2025-05-01T10:00:00Z", "2025-05-02T12:00:00Z"
        );

        service.adminImport(List.of(entry), "Bearer secret");

        then(repository).should().upsert(org.mockito.ArgumentMatchers.argThat(record ->
            "fixed-id".equals(record.id())
            && "bob".equals(record.username())
            && "My Title".equals(record.title())
            && "2025-05-01T10:00:00Z".equals(record.createdAt())
            && "2025-05-02T12:00:00Z".equals(record.updatedAt())
        ));
    }

    // -----------------------------------------------------------------------
    // exportAll – auth guard
    // -----------------------------------------------------------------------

    @Test
    void exportAll_withoutToken_throws403() {
        assertThatThrownBy(() -> service.exportAll(null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void exportAll_withValidToken_returnsAllRecords() {
        org.mockito.BDDMockito.given(repository.listAll()).willReturn(List.of(
            new ScenarioRecord("id1", "alice", "T1", "{}", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z")
        ));

        ScenarioService.ExportResult result = service.exportAll("Bearer secret");
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.scenarios()).hasSize(1);
        assertThat(result.scenarios().get(0).id()).isEqualTo("id1");
        assertThat(result.exportedAt()).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private List<ScenarioService.ImportEntry> sampleEntries() {
        return sampleEntries(1);
    }

    private List<ScenarioService.ImportEntry> sampleEntries(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new ScenarioService.ImportEntry(
                "id-" + i, "user", "Title " + i,
                objectMapper.createObjectNode(),
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))
            .toList();
    }
}
