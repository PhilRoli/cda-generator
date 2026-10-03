package at.rolinek.cda.security;

import at.rolinek.cda.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminTokenGuardTest {

    private AdminTokenGuard guard(String token) {
        AppProperties props = new AppProperties();
        props.setAdminToken(token);
        return new AdminTokenGuard(props);
    }

    @Test
    void acceptsTheConfiguredBearerToken() {
        assertThatCode(() -> guard("secret").require("Bearer secret")).doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongMissingOrMalformedTokens() {
        for (String header : new String[] {"Bearer wrong", null, "secret", "Basic secret", "Bearer "}) {
            assertThatThrownBy(() -> guard("secret").require(header))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ungültiger Admin-Token.");
        }
    }

    @Test
    void failsClosedWhenNoTokenIsConfigured() {
        assertThatThrownBy(() -> guard("  ").require("Bearer "))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Admin-Funktion ist nicht konfiguriert.");
    }
}
