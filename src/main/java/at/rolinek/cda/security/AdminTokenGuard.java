package at.rolinek.cda.security;

import at.rolinek.cda.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Checks the admin bearer token for every /api/admin/* operation (fail-closed). */
@Component
public class AdminTokenGuard {

    private final String adminToken;

    public AdminTokenGuard(AppProperties properties) {
        this.adminToken = properties.getAdminToken() == null ? "" : properties.getAdminToken().trim();
    }

    public void require(String authorizationHeader) {
        if (adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin-Funktion ist nicht konfiguriert.");
        }
        if (!ConstantTime.equals(bearerToken(authorizationHeader), adminToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiger Admin-Token.");
        }
    }

    private static String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }
}
