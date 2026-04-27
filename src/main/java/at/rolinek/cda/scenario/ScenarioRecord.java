package at.rolinek.cda.scenario;

public record ScenarioRecord(
    String id,
    String username,
    String title,
    String payloadJson,
    String createdAt,
    String updatedAt
) {}
