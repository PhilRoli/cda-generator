package at.rolinek.cda.scenario;

import at.rolinek.cda.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ScenarioService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ScenarioRepository repository;
    private final ObjectMapper objectMapper;
    private final String adminToken;

    public ScenarioService(ScenarioRepository repository, ObjectMapper objectMapper, AppProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.adminToken = properties.getAdminToken() == null ? "" : properties.getAdminToken().trim();
    }

    public List<ScenarioRecord> listByUsername(String username) {
        String normalized = normalizeUsername(username);
        return repository.listByUsername(normalized);
    }

    public List<ScenarioRecord> listAllForAdmin(String bearerToken) {
        requireAdminToken(bearerToken);
        return listAll();
    }

    public List<ScenarioRecord> listAll() {
        return repository.listAll();
    }

    public ScenarioRecord getByIdPublic(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Szenario nicht gefunden."));
    }

    public ScenarioRecord getByIdForUser(String id, String username) {
        String normalized = normalizeUsername(username);
        ScenarioRecord record = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Szenario nicht gefunden."));
        if (!record.username().equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kein Zugriff auf dieses Szenario.");
        }
        return record;
    }

    public ScenarioRecord saveForUser(ScenarioSaveRequest request) {
        String normalizedUser = normalizeUsername(request.username());
        if (request.state() == null || request.state().isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Szenario-Inhalt fehlt.");
        }

        String title = normalizeTitle(request.title());
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(TS);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(request.state());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Szenario-Inhalt.");
        }

        if (request.id() == null || request.id().isBlank()) {
            ScenarioRecord created = new ScenarioRecord(
                UUID.randomUUID().toString(),
                normalizedUser,
                title,
                payload,
                now,
                now
            );
            repository.insert(created);
            return created;
        }

        ScenarioRecord existing = repository.findById(request.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Szenario nicht gefunden."));
        if (!existing.username().equals(normalizedUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Szenario gehört einem anderen Benutzer.");
        }

        ScenarioRecord updated = new ScenarioRecord(
            existing.id(),
            existing.username(),
            title,
            payload,
            existing.createdAt(),
            now
        );
        repository.update(updated);
        return updated;
    }

    public ExportResult exportAll(String bearerToken) {
        requireAdminToken(bearerToken);
        List<ScenarioRecord> records = repository.listAll();
        String exportedAt = OffsetDateTime.now(ZoneOffset.UTC).format(TS);
        List<ExportEntry> entries = records.stream()
            .map(r -> new ExportEntry(r.id(), r.username(), r.title(), payloadToJson(r), r.createdAt(), r.updatedAt()))
            .toList();
        return new ExportResult(exportedAt, entries.size(), entries);
    }

    public int adminImport(List<ImportEntry> scenarios, String bearerToken) {
        requireAdminToken(bearerToken);
        if (scenarios == null || scenarios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keine Szenarien zum Importieren.");
        }
        for (ImportEntry entry : scenarios) {
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(entry.state());
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Szenario-Inhalt.");
            }
            repository.upsert(new ScenarioRecord(
                entry.id(),
                entry.username(),
                entry.title(),
                payloadJson,
                entry.createdAt(),
                entry.updatedAt()
            ));
        }
        return scenarios.size();
    }

    public void adminDelete(String id, String bearerToken) {
        requireAdminToken(bearerToken);
        int deleted = repository.deleteById(id);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Szenario nicht gefunden.");
        }
    }

    public JsonNode payloadToJson(ScenarioRecord record) {
        try {
            return objectMapper.readTree(record.payloadJson());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Szenario-Daten sind beschädigt.");
        }
    }

    private void requireAdminToken(String bearerToken) {
        if (adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin-Funktion ist nicht konfiguriert.");
        }
        String provided = extractBearerToken(bearerToken);
        if (!adminToken.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token.");
        }
    }

    private String extractBearerToken(String header) {
        if (header == null) {
            return null;
        }
        if (!header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Benutzername fehlt.");
        }
        String value = username.trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Benutzername fehlt.");
        }
        if (value.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Benutzername ist zu lang.");
        }
        return value;
    }

    private String normalizeTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isBlank()) {
            return "Unbenanntes Szenario";
        }
        if (value.length() > 160) {
            return value.substring(0, 160);
        }
        return value;
    }

    public record ScenarioSaveRequest(String id, String username, String title, JsonNode state) {}

    public record ExportEntry(String id, String username, String title, JsonNode state, String createdAt, String updatedAt) {}

    public record ExportResult(String exportedAt, int count, List<ExportEntry> scenarios) {}

    public record ImportEntry(String id, String username, String title, JsonNode state, String createdAt, String updatedAt) {}
}
