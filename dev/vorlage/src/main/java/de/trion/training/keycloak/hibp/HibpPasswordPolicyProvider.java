package de.trion.training.keycloak.hibp;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyProvider;
import org.keycloak.policy.PolicyError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Prüft Passwörter gegen die "Pwned Passwords"-Range-API von Have I Been Pwned:
 * GET https://api.pwnedpasswords.com/range/{erste 5 Zeichen des SHA-1-Hashs}
 * Antwort: eine Zeile pro Hash-Suffix im Format "SUFFIX:ANZAHL".
 */
public class HibpPasswordPolicyProvider implements PasswordPolicyProvider {

    private static final Logger LOG = Logger.getLogger(HibpPasswordPolicyProvider.class);

    /** Message-Key für theme-resources/messages/messages_*.properties */
    public static final String ERROR_MESSAGE = "invalidPasswordPwnedMessage";

    private final KeycloakSession session;
    private final String apiUrl;

    public HibpPasswordPolicyProvider(KeycloakSession session, String apiUrl) {
        this.session = session;
        this.apiUrl = apiUrl;
    }

    @Override
    public PolicyError validate(RealmModel realm, UserModel user, String password) {
        return validate(user == null ? null : user.getUsername(), password);
    }

    @Override
    public PolicyError validate(String username, String password) {
        // Konfigurierter Wert der Policy, z.B. hibp(0) -> 0
        int maxCount = session.getContext().getRealm().getPasswordPolicy().getPolicyConfig(HibpPasswordPolicyProviderFactory.ID);

        // TODO 1: SHA-1-Hash bilden (sha1Hex) und in Präfix (5 Zeichen) und Suffix aufteilen
        // TODO 2: countBreaches(...) aufrufen. Was soll passieren, wenn HIBP nicht erreichbar ist (IOException)?
        // TODO 3: Mehr Treffer als maxCount -> new PolicyError(ERROR_MESSAGE, anzahl), sonst null
        return null;
    }

    long countBreaches(String prefix, String suffix) throws IOException {
        // TODO 4: GET apiUrl + prefix mit dem HttpClient von Keycloak:
        //         session.getProvider(HttpClientProvider.class).getHttpClient()
        //         - Header "Add-Padding: true" setzen
        //         - Timeouts setzen (RequestConfig)
        //         - Status != 200 -> IOException
        //         - Antwort an countInResponse(...) übergeben
        return 0;
    }

    /** Antwort der Range-API auswerten: eine Zeile pro Hash-Suffix, "SUFFIX:ANZAHL"; 0 = nicht gefunden. */
    static long countInResponse(String body, String suffix) {
        // TODO 5: Zeilen durchgehen (Zeilenende "\r\n" oder "\n"), Suffix ohne Beachtung der Groß-/Kleinschreibung
        //         vergleichen, Anzahl zurückgeben. Zuerst HibpPasswordPolicyProviderTest grün bekommen!
        return 0;
    }

    static String sha1Hex(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object parseConfig(String value) {
        return parseInteger(value, 0);
    }

    @Override
    public void close() {
    }
}
