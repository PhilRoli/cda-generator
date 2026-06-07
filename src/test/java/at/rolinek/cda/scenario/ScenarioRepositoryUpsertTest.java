package at.rolinek.cda.scenario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ScenarioRepository#upsert using a temporary SQLite file.
 */
class ScenarioRepositoryUpsertTest {

    private Path tempDb;
    private ScenarioRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        tempDb = Files.createTempFile("scenarios-upsert-test-", ".db");
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:" + tempDb.toAbsolutePath());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        repository = new ScenarioRepository(jdbc);
        repository.initSchema();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempDb);
        Files.deleteIfExists(Path.of(tempDb + "-wal"));
        Files.deleteIfExists(Path.of(tempDb + "-shm"));
    }

    @Test
    void upsert_insertsNewRecord() {
        ScenarioRecord record = new ScenarioRecord(
            "id-new", "alice", "Neu", "{\"a\":1}", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");

        repository.upsert(record);

        Optional<ScenarioRecord> found = repository.findById("id-new");
        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("alice");
        assertThat(found.get().title()).isEqualTo("Neu");
        assertThat(found.get().payloadJson()).isEqualTo("{\"a\":1}");
    }

    @Test
    void upsert_replacesExistingRecord_idIsStable() {
        ScenarioRecord original = new ScenarioRecord(
            "id-exists", "bob", "Alt", "{\"v\":1}", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
        repository.insert(original);

        ScenarioRecord replacement = new ScenarioRecord(
            "id-exists", "carol", "Neu", "{\"v\":99}", "2025-06-01T00:00:00Z", "2026-06-01T00:00:00Z");
        repository.upsert(replacement);

        Optional<ScenarioRecord> found = repository.findById("id-exists");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("id-exists");          // id stable
        assertThat(found.get().username()).isEqualTo("carol");        // fields updated
        assertThat(found.get().title()).isEqualTo("Neu");
        assertThat(found.get().payloadJson()).isEqualTo("{\"v\":99}");
        assertThat(found.get().createdAt()).isEqualTo("2025-06-01T00:00:00Z");
        assertThat(found.get().updatedAt()).isEqualTo("2026-06-01T00:00:00Z");

        // Only one row in the table
        assertThat(repository.listAll()).hasSize(1);
    }

    @Test
    void upsert_preservesAllFieldsOnInsert() {
        ScenarioRecord record = new ScenarioRecord(
            "id-preserve", "delta", "Test", "{}", "2024-03-15T08:30:00Z", "2024-03-16T09:00:00Z");
        repository.upsert(record);

        ScenarioRecord found = repository.findById("id-preserve").orElseThrow();
        assertThat(found.createdAt()).isEqualTo("2024-03-15T08:30:00Z");
        assertThat(found.updatedAt()).isEqualTo("2024-03-16T09:00:00Z");
        assertThat(found.username()).isEqualTo("delta");
    }
}
