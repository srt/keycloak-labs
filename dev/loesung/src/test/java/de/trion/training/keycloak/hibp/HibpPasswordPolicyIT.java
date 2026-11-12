package de.trion.training.keycloak.hibp;

import de.trion.training.keycloak.ExtensionsServerConfig;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.testframework.annotations.InjectHttpServer;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.InjectUser;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.ManagedUser;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integrationstest: Keycloak läuft mit diesem Projekt als Erweiterung, die HIBP-API wird durch einen
 * lokalen HTTP-Server ersetzt (kein Internet nötig, reproduzierbar, Fehlerfälle simulierbar).
 */
@KeycloakIntegrationTest(config = ExtensionsServerConfig.class)
class HibpPasswordPolicyIT {

    /** SHA-1("password") = 5BAA6 1E4C9B93F3F0682250B6CF8331B7EE68FD8 */
    private static final String PWNED_PREFIX = "5BAA6";
    private static final String PWNED_SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    @InjectRealm(config = HibpRealm.class)
    ManagedRealm realm;

    @InjectUser
    ManagedUser user;

    /** Vom Test Framework bereitgestellter HTTP-Server auf http://127.0.0.1:8500 */
    @InjectHttpServer
    HttpServer hibp;

    /** Welche Anfragen kamen beim Stub an? (Pfad + Header "Add-Padding") */
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @AfterEach
    void removeStub() {
        hibp.removeContext("/range/");
    }

    @Test
    void rejectsPwnedPassword() {
        stub(200, "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + PWNED_SUFFIX + ":9659365\r\n");

        assertThrows(BadRequestException.class, () -> setPassword("password"));
    }

    @Test
    void acceptsUnknownPassword() {
        stub(200, "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n");

        assertDoesNotThrow(() -> setPassword("password"));
    }

    @Test
    void sendsOnlyHashPrefixWithPadding() {
        // k-Anonymity: nur die ersten 5 Zeichen des Hashs verlassen den Server
        stub(200, "");

        setPassword("password");

        assertEquals(List.of("/range/" + PWNED_PREFIX + " padding=true"), requests);
    }

    @Test
    void ignoresPaddingEntries() {
        // Mit "Add-Padding: true" liefert die API zusätzliche Einträge mit Anzahl 0
        stub(200, PWNED_SUFFIX + ":0\r\n");

        assertDoesNotThrow(() -> setPassword("password"));
    }

    @Test
    void failsOpenWhenHibpIsDown() {
        // Entscheidung "fail open": ist HIBP nicht erreichbar, wird das Passwort nicht blockiert
        stub(503, "");

        assertDoesNotThrow(() -> setPassword("password"));
    }

    private void stub(int status, String body) {
        hibp.createContext("/range/", exchange -> {
            requests.add(exchange.getRequestURI().getPath() + " padding=" + exchange.getRequestHeaders().getFirst("Add-Padding"));
            boolean prefixOk = exchange.getRequestURI().getPath().equals("/range/" + PWNED_PREFIX);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(prefixOk ? status : 404, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
    }

    private void setPassword(String password) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        realm.admin().users().get(user.getId()).resetPassword(cred);
    }

    static class HibpRealm implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.passwordPolicy("hibp(0)");
        }
    }
}
