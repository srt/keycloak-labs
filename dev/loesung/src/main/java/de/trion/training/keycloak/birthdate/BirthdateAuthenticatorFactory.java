package de.trion.training.keycloak.birthdate;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

public class BirthdateAuthenticatorFactory implements AuthenticatorFactory {

    private static final Logger LOG = Logger.getLogger(BirthdateAuthenticatorFactory.class);

    public static final String ID = "birthdate-altcha";
    public static final String CONFIG_SECRET = "altchaSecret";
    public static final String CONFIG_COST = "altchaCost";
    public static final int DEFAULT_COST = 5000;

    private String defaultSecret;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Geburtsdatum (mit ALTCHA)";
    }

    @Override
    public String getHelpText() {
        return "Fragt das Geburtsdatum ab (User-Attribut 'birthdate'), geschützt durch ALTCHA und den Brute-Force-Schutz.";
    }

    /**
     * Achtung (seit Keycloak 26.6): Die Kategorie entscheidet, ob failureChallenge() den Brute-Force-Zähler erhöht.
     * Gezählt werden nur null/"password" (Fehlversuche) und "otp" (eigener Zähler "secondary auth failures").
     * Jede andere Kategorie (z.B. "birthdate") wird vom DefaultBruteForceProtector stillschweigend ignoriert!
     */
    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[]{Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED};
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property().name(CONFIG_SECRET).label("ALTCHA HMAC-Secret")
                .helpText("Signiert die Challenges. Leer = zufälliger Wert pro Server-Start (nicht clusterfähig!)")
                .type(ProviderConfigProperty.PASSWORD).secret(true).add()
                .property().name(CONFIG_COST).label("ALTCHA Cost")
                .helpText("PBKDF2-Iterationen pro Versuch - höher = mehr Rechenaufwand im Browser")
                .type(ProviderConfigProperty.STRING_TYPE).defaultValue(String.valueOf(DEFAULT_COST)).add()
                .build();
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new BirthdateAuthenticator(defaultSecret);
    }

    @Override
    public void init(Config.Scope config) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        defaultSecret = HexFormat.of().formatHex(random);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        LOG.debugf("Authenticator %s registriert", ID);
    }

    @Override
    public void close() {
    }
}
