package de.trion.training.keycloak.hibp;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.policy.PasswordPolicyProvider;
import org.keycloak.policy.PasswordPolicyProviderFactory;

/**
 * Registriert die Policy "Pwned Passwords (HIBP)" unter Authentication → Policies → Password policy.
 * Die Factory existiert einmal pro Server, der Provider wird pro Request erzeugt.
 */
public class HibpPasswordPolicyProviderFactory implements PasswordPolicyProviderFactory {

    public static final String ID = "hibp";

    private String apiUrl;

    @Override
    public String getId() {
        return ID; // Schlüssel in der Policy-Zeichenkette, z.B. "length(12) and hibp(0)"
    }

    @Override
    public String getDisplayName() {
        return "Pwned Passwords (HIBP)";
    }

    @Override
    public String getConfigType() {
        return PasswordPolicyProvider.INT_CONFIG_TYPE;
    }

    @Override
    public String getDefaultConfigValue() {
        return "0"; // maximal erlaubte Anzahl Treffer: 0 = jedes bekannte Passwort ablehnen
    }

    @Override
    public boolean isMultiplSupported() {
        return false;
    }

    @Override
    public void init(Config.Scope config) {
        // Konfigurierbar per --spi-password-policy--hibp--api-url=... (z.B. eigener Spiegel in Produktion)
        apiUrl = config.get("api-url", "https://api.pwnedpasswords.com/range/");
    }

    @Override
    public PasswordPolicyProvider create(KeycloakSession session) {
        return new HibpPasswordPolicyProvider(session, apiUrl);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
