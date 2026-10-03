package at.rolinek.cda.usage;

import java.time.Instant;

/** One recorded usage event; {@code username}, {@code ipPrefix}, {@code detail} are never null. */
public record UsageEvent(Instant occurredAt, UsageType type, boolean ok, Integer httpStatus,
                         String username, String ipPrefix, String detail) {}
