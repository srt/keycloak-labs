package de.trion.training.keycloak.hibp;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;
import org.keycloak.connections.httpclient.HttpClientProvider;
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
 * Prüft Passwörter gegen die "Pwned Passwords"-Range-API von Have I Been Pwned.
 * <p>
 * k-Anonymity: Es werden nur die ersten 5 Hex-Zeichen des SHA-1-Hashs übertragen.
 * Die API liefert alle bekannten Hash-Suffixe mit diesem Präfix; der Vergleich passiert lokal.
 */
public class HibpPasswordPolicyProvider implements PasswordPolicyProvider {

    private static final Logger LOG = Logger.getLogger(HibpPasswordPolicyProvider.class);
    private static final int TIMEOUT_MILLIS = 2000;

    /** Message-Key aus theme-resources/messages/messages_*.properties */
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
        int maxCount = session.getContext().getRealm().getPasswordPolicy().getPolicyConfig(HibpPasswordPolicyProviderFactory.ID);

        String hash = sha1Hex(password);
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);

        long count;
        try {
            count = countBreaches(prefix, suffix);
        } catch (IOException e) {
            // Entscheidung "fail open": HIBP nicht erreichbar -> Passwort nicht blockieren.
            // Die übrigen Policies (Länge etc.) greifen weiterhin. Alternative: return new PolicyError(...)
            LOG.warnf("HIBP nicht erreichbar, Passwort wird nicht geprüft: %s", e.getMessage());
            return null;
        }

        if (count > maxCount) {
            // Kein Passwort und kein Hash ins Log - nur die Tatsache
            LOG.debugf("Passwort abgelehnt, %d Treffer in Pwned Passwords", count);
            return new PolicyError(ERROR_MESSAGE, count);
        }
        return null;
    }

    long countBreaches(String prefix, String suffix) throws IOException {
        HttpGet get = new HttpGet(apiUrl + prefix);
        // Padding: Antwort enthält zusätzlich Dummy-Einträge mit Count 0 -> Antwortgröße verrät nichts
        get.setHeader("Add-Padding", "true");
        get.setHeader("User-Agent", "keycloak-hibp-password-policy");
        get.setConfig(RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setSocketTimeout(TIMEOUT_MILLIS)
                .build());

        // HttpClient von Keycloak: berücksichtigt Proxy- und Truststore-Konfiguration des Servers
        var client = session.getProvider(HttpClientProvider.class).getHttpClient();
        try (CloseableHttpResponse response = client.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200) {
                throw new IOException("HTTP " + status);
            }
            return countInResponse(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), suffix);
        }
    }

    /** Antwort der Range-API auswerten: eine Zeile pro Hash-Suffix, "SUFFIX:ANZAHL"; 0 = nicht gefunden. */
    static long countInResponse(String body, String suffix) {
        for (String line : body.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equalsIgnoreCase(suffix)) {
                return Long.parseLong(line.substring(colon + 1).trim());
            }
        }
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
