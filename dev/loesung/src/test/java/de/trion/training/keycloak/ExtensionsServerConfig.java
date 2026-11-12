package de.trion.training.keycloak;

import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

/**
 * Gemeinsame Server-Konfiguration für alle Integrationstests. Gleiche Konfiguration = derselbe Server,
 * Keycloak startet dann nur einmal pro Testlauf.
 */
public class ExtensionsServerConfig implements KeycloakServerConfig {

    /** HTTP-Stub des Test Frameworks (@InjectHttpServer) ersetzt die HIBP-API */
    public static final String HIBP_STUB_URL = "http://127.0.0.1:8500/range/";

    @Override
    public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
        // Eigene Klassen aus target/classes + Bibliotheken, die nicht im Keycloak enthalten sind
        return config.dependencyCurrentProject()
                .dependency("org.altcha", "altcha")
                .dependency("org.json", "json")
                // gleiche Option wie im Betrieb: --spi-password-policy--hibp--api-url=...
                .spiOption("password-policy", "hibp", "api-url", HIBP_STUB_URL);
    }
}
