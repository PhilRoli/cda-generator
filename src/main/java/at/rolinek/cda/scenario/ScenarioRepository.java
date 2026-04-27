package at.rolinek.cda.scenario;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Repository
public class ScenarioRepository {
    private final JdbcTemplate jdbcTemplate;

    public ScenarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS scenarios (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                title TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_scenarios_username_updated
            ON scenarios(username, updated_at DESC)
            """);
    }

    public List<ScenarioRecord> listByUsername(String username) {
        return jdbcTemplate.query("""
                SELECT id, username, title, payload_json, created_at, updated_at
                FROM scenarios
                WHERE username = ?
                ORDER BY updated_at DESC
                """,
            scenarioRowMapper(),
            username);
    }

    public List<ScenarioRecord> listAll() {
        return jdbcTemplate.query("""
                SELECT id, username, title, payload_json, created_at, updated_at
                FROM scenarios
                ORDER BY updated_at DESC
                """,
            scenarioRowMapper());
    }

    public Optional<ScenarioRecord> findById(String id) {
        List<ScenarioRecord> rows = jdbcTemplate.query("""
                SELECT id, username, title, payload_json, created_at, updated_at
                FROM scenarios
                WHERE id = ?
                LIMIT 1
                """,
            scenarioRowMapper(),
            id);
        return rows.stream().findFirst();
    }

    public void insert(ScenarioRecord record) {
        jdbcTemplate.update("""
                INSERT INTO scenarios (id, username, title, payload_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            record.id(),
            record.username(),
            record.title(),
            record.payloadJson(),
            record.createdAt(),
            record.updatedAt());
    }

    public void update(ScenarioRecord record) {
        jdbcTemplate.update("""
                UPDATE scenarios
                SET title = ?, payload_json = ?, updated_at = ?
                WHERE id = ?
                """,
            record.title(),
            record.payloadJson(),
            record.updatedAt(),
            record.id());
    }

    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM scenarios WHERE id = ?", id);
    }

    private org.springframework.jdbc.core.RowMapper<ScenarioRecord> scenarioRowMapper() {
        return (rs, idx) -> new ScenarioRecord(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("title"),
            rs.getString("payload_json"),
            rs.getString("created_at"),
            rs.getString("updated_at"));
    }
}
