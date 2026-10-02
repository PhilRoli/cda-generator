package at.rolinek.cda.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User-supplied values (scenario titles, ids, parser messages) go into key=value event
 * logs. A raw line break would let a client forge a whole extra log line.
 */
class LogSafeTest {

    @Test
    void lineBreaksAreEscapedSoNoLineCanBeForged() {
        assertThat(LogSafe.of("Titel\nevent=scenario_admin_deleted id=x"))
            .isEqualTo("Titel\\nevent=scenario_admin_deleted id=x");
        assertThat(LogSafe.of("a\r\nb")).isEqualTo("a\\r\\nb");
    }

    @Test
    void otherControlCharactersAreReplaced() {
        assertThat(LogSafe.of("a\u0000b\u001Bc\td")).isEqualTo("a?b?c?d");
    }

    @Test
    void longValuesAreTruncated() {
        assertThat(LogSafe.of("x".repeat(500))).hasSize(201).endsWith("…");
    }

    @Test
    void nullAndPlainValuesPassThrough() {
        assertThat(LogSafe.of(null)).isEqualTo("null");
        assertThat(LogSafe.of("Übungsszenario 1")).isEqualTo("Übungsszenario 1");
    }
}
