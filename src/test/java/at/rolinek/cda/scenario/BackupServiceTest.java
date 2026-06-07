package at.rolinek.cda.scenario;

import at.rolinek.cda.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    private ScenarioService scenarioService;
    private ObjectMapper objectMapper;
    private AppProperties properties;
    private BackupService backupService;

    @BeforeEach
    void setUp() {
        scenarioService = mock(ScenarioService.class);
        objectMapper = new ObjectMapper();
        properties = new AppProperties();
        backupService = new BackupService(scenarioService, objectMapper, properties);
    }

    // -----------------------------------------------------------------------
    // writeBackup
    // -----------------------------------------------------------------------

    @Test
    void writeBackup_createsFileWithValidEnvelope() throws Exception {
        given(scenarioService.listAll()).willReturn(List.of(
            new ScenarioRecord("id1", "alice", "T1", "{}", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z")
        ));
        given(scenarioService.payloadToJson(new ScenarioRecord("id1", "alice", "T1", "{}", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z")))
            .willReturn(objectMapper.readTree("{}"));

        Path file = backupService.writeBackup(tempDir);

        assertThat(file).exists();
        assertThat(file.getFileName().toString())
            .startsWith("scenarios-")
            .endsWith(".json");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = objectMapper.readValue(file.toFile(), Map.class);
        assertThat(envelope).containsKey("exportedAt");
        assertThat(envelope).containsKey("count");
        assertThat(envelope).containsKey("scenarios");
        assertThat((int) envelope.get("count")).isEqualTo(1);
    }

    @Test
    void writeBackup_createsDirectoryIfMissing() throws Exception {
        given(scenarioService.listAll()).willReturn(List.of());

        Path subDir = tempDir.resolve("new-subdir/nested");
        backupService.writeBackup(subDir);

        assertThat(subDir).isDirectory();
        assertThat(Files.list(subDir).count()).isEqualTo(1);
    }

    @Test
    void writeBackup_emptyDb_writesEnvelopeWithCountZero() throws Exception {
        given(scenarioService.listAll()).willReturn(List.of());

        Path file = backupService.writeBackup(tempDir);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = objectMapper.readValue(file.toFile(), Map.class);
        assertThat((int) envelope.get("count")).isEqualTo(0);
        assertThat(envelope.get("scenarios")).isInstanceOf(List.class);
        assertThat((List<?>) envelope.get("scenarios")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // pruneOldBackups
    // -----------------------------------------------------------------------

    @Test
    void pruneOldBackups_keepsNewestRetentionFiles() throws Exception {
        // Create 5 backup-named files with small delays to ensure ordering by creation time
        for (int i = 1; i <= 5; i++) {
            Path f = tempDir.resolve("scenarios-202601" + String.format("%02d", i) + "-120000.json");
            Files.writeString(f, "{}");
            // tiny sleep so creation timestamps differ on fast systems
            Thread.sleep(5);
        }

        backupService.pruneOldBackups(tempDir, 3);

        long remaining = Files.list(tempDir)
            .filter(p -> p.getFileName().toString().startsWith("scenarios-"))
            .count();
        assertThat(remaining).isEqualTo(3);
    }

    @Test
    void pruneOldBackups_doesNothingWhenUnderRetention() throws Exception {
        Files.writeString(tempDir.resolve("scenarios-20260101-120000.json"), "{}");
        Files.writeString(tempDir.resolve("scenarios-20260102-120000.json"), "{}");

        backupService.pruneOldBackups(tempDir, 5);

        long remaining = Files.list(tempDir).count();
        assertThat(remaining).isEqualTo(2);
    }

    @Test
    void pruneOldBackups_ignoresNonBackupFiles() throws Exception {
        Files.writeString(tempDir.resolve("other-file.json"), "{}");
        Files.writeString(tempDir.resolve("scenarios-20260101-120000.json"), "{}");
        Files.writeString(tempDir.resolve("scenarios-20260102-120000.json"), "{}");
        Files.writeString(tempDir.resolve("scenarios-20260103-120000.json"), "{}");

        backupService.pruneOldBackups(tempDir, 2);

        // 2 backup files kept + 1 non-backup file untouched
        assertThat(Files.exists(tempDir.resolve("other-file.json"))).isTrue();
        long backupCount = Files.list(tempDir)
            .filter(p -> p.getFileName().toString().startsWith("scenarios-"))
            .count();
        assertThat(backupCount).isEqualTo(2);
    }

    @Test
    void pruneOldBackups_gracefulOnNonExistentDir() throws IOException {
        Path missing = tempDir.resolve("does-not-exist");
        // Should not throw
        backupService.pruneOldBackups(missing, 5);
    }
}
