package at.rolinek.cda.scenario;

import at.rolinek.cda.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class BackupService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ScenarioService scenarioService;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public BackupService(ScenarioService scenarioService, ObjectMapper objectMapper, AppProperties properties) {
        this.scenarioService = scenarioService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Scheduled trigger — calls {@link #writeBackup(Path)} with the configured backup directory.
     * Logs on success/failure; never throws so the scheduler is not disrupted.
     */
    @Scheduled(fixedDelayString = "${app.backup.interval-ms:86400000}")
    public void runScheduledBackup() {
        Path dir = Path.of(properties.getBackup().getDir()).toAbsolutePath().normalize();
        int retention = properties.getBackup().getRetention();
        try {
            Path written = writeBackup(dir);
            int pruned = pruneOldBackups(dir, retention);
            LOG.info("event=backup_completed file={} count={} pruned={}", written.getFileName(), countInBackup(written), pruned);
        } catch (Exception ex) {
            LOG.warn("Szenario-Backup fehlgeschlagen: {}", ex.getMessage(), ex);
        }
    }

    private int countInBackup(Path file) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> envelope = objectMapper.readValue(file.toFile(), java.util.Map.class);
            Object count = envelope.get("count");
            return count instanceof Integer i ? i : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * Writes a single timestamped backup file to {@code dir}.
     * Returns the path of the written file.
     * Exported content matches the {@code /api/admin/scenarios/export} envelope shape,
     * so the file can be fed directly into {@code POST /api/admin/scenarios/import}.
     */
    public Path writeBackup(Path dir) throws IOException {
        Files.createDirectories(dir);

        ScenarioService.ExportResult export = buildExport();
        String filename = "scenarios-" + OffsetDateTime.now(ZoneOffset.UTC).format(FILE_TS) + ".json";
        Path file = dir.resolve(filename);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), export);
        return file;
    }

    /**
     * Removes the oldest backup files from {@code dir} so that at most {@code retention} files remain.
     *
     * @return the number of files actually deleted
     */
    public int pruneOldBackups(Path dir, int retention) throws IOException {
        if (retention <= 0) {
            return 0;
        }
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream
                .filter(p -> p.getFileName().toString().startsWith("scenarios-") && p.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();

            int toDelete = Math.max(0, files.size() - retention);
            for (int i = 0; i < toDelete; i++) {
                Files.deleteIfExists(files.get(i));
            }
            return toDelete;
        }
    }

    private ScenarioService.ExportResult buildExport() {
        // listAll() requires no auth — used internally only
        List<ScenarioRecord> records = scenarioService.listAll();
        String exportedAt = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        List<ScenarioService.ExportEntry> entries = records.stream()
            .map(r -> new ScenarioService.ExportEntry(
                r.id(), r.username(), r.title(),
                scenarioService.payloadToJson(r),
                r.createdAt(), r.updatedAt()))
            .toList();
        return new ScenarioService.ExportResult(exportedAt, entries.size(), entries);
    }
}
