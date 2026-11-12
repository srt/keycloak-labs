package de.trion.training.keycloak.birthdate;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.altcha.altcha.v2.Altcha;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Zusätzlicher Wissensfaktor: Geburtsdatum abfragen und mit dem User-Attribut "birthdate" vergleichen.
 * <ul>
 *     <li>Falsches Geburtsdatum zählt als Fehlversuch für den Brute-Force-Schutz (failureChallenge)</li>
 *     <li>Gesperrte Benutzer werden vor jeder Prüfung abgewiesen</li>
 *     <li>ALTCHA (Proof of Work) bremst automatisierte Versuche; ein fehlendes/falsches Captcha zählt NICHT als Fehlversuch</li>
 * </ul>
 */
public class BirthdateAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(BirthdateAuthenticator.class);

    static final String TEMPLATE = "login-birthdate.ftl";
    static final String FIELD_BIRTHDATE = "birthdate";
    static final String FIELD_ALTCHA = "altcha";
    static final String USER_ATTRIBUTE = Birthdates.USER_ATTRIBUTE;
    static final int CHALLENGE_LIFESPAN_SECONDS = 300;

    private final String defaultSecret;

    public BirthdateAuthenticator(String defaultSecret) {
        this.defaultSecret = defaultSecret;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (isDisabledByBruteForce(context)) {
            return;
        }
        context.challenge(createForm(context, null, null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        UserModel user = context.getUser();

        // 1. Gesperrt? Dann gar nicht erst prüfen (sonst könnte man trotz Sperre weiter raten)
        if (isDisabledByBruteForce(context)) {
            return;
        }

        // 2. Captcha - ein Fehler hier ist KEIN Fehlversuch, sonst könnte jeder fremde Konten sperren
        if (!verifyAltcha(context, form.getFirst(FIELD_ALTCHA))) {
            context.getEvent().user(user).error(Errors.INVALID_INPUT);
            context.forceChallenge(createForm(context, "altchaInvalidMessage", null));
            return;
        }

        // 3. Geburtsdatum - ein Fehler hier zählt für den Brute-Force-Schutz
        if (!birthdateMatches(user, Birthdates.normalize(form.getFirst(FIELD_BIRTHDATE)))) {
            // Eingabe NICHT loggen - das Geburtsdatum ist ein Geheimnis wie ein Passwort
            context.getEvent().user(user).error(Errors.INVALID_USER_CREDENTIALS);
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    createForm(context, "invalidBirthdateMessage", FIELD_BIRTHDATE));
            return;
        }

        context.success();
    }

    /** Vergleich in konstanter Zeit; given ist bereits ins ISO-Format normalisiert (oder null). */
    private static boolean birthdateMatches(UserModel user, String given) {
        String expected = user.getFirstAttribute(USER_ATTRIBUTE);
        return expected != null && given != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** Wie die eingebauten Authenticators: gesperrte Benutzer abweisen, ohne den Zähler weiter zu erhöhen. */
    private boolean isDisabledByBruteForce(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        String bruteForceError = AuthenticatorUtils.getDisabledByBruteForceEventError(context, user);
        if (bruteForceError == null) {
            return false;
        }
        context.getEvent().user(user).error(bruteForceError);
        context.forceChallenge(createForm(context, Messages.ACCOUNT_TEMPORARILY_DISABLED, null));
        return true;
    }

    private Response createForm(AuthenticationFlowContext context, String error, String field) {
        LoginFormsProvider form = context.form().setAttribute("altchaChallenge", createChallenge(context));
        if (error != null && field != null) {
            form.addError(new FormMessage(field, error));
        } else if (error != null) {
            form.setError(error);
        }
        return form.createForm(TEMPLATE);
    }

    private String createChallenge(AuthenticationFlowContext context) {
        try {
            return Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                    .algorithm("PBKDF2/SHA-256")
                    .deriveKey(Altcha.pbkdf2())
                    .cost(cost(context))
                    .expiresInSeconds(CHALLENGE_LIFESPAN_SECONDS)
                    .hmacSignatureSecret(secret(context))).toJson();
        } catch (Exception e) {
            throw new IllegalStateException("ALTCHA-Challenge konnte nicht erzeugt werden", e);
        }
    }

    private boolean verifyAltcha(AuthenticationFlowContext context, String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        try {
            // prüft Signatur (Challenge stammt von uns), Ablaufzeit und die Proof-of-Work-Lösung
            Altcha.VerifySolutionResult result = Altcha.verifySolution(payload, secret(context), Altcha.pbkdf2());
            if (!result.verified()) {
                LOG.debugf("ALTCHA abgelehnt: %s", result);
                return false;
            }
            // Replay-Schutz: jede gelöste Challenge nur einmal akzeptieren (clusterweit über den SingleUseObjectProvider)
            String nonce = Altcha.parsePayload(payload).challenge().parameters().nonce();
            return context.getSession().singleUseObjects().putIfAbsent("altcha:" + nonce, CHALLENGE_LIFESPAN_SECONDS);
        } catch (Exception e) {
            LOG.debugf("ALTCHA-Payload ungültig: %s", e.getMessage());
            return false;
        }
    }

    private String secret(AuthenticationFlowContext context) {
        String configured = config(context, BirthdateAuthenticatorFactory.CONFIG_SECRET);
        return configured != null ? configured : defaultSecret;
    }

    private int cost(AuthenticationFlowContext context) {
        String configured = config(context, BirthdateAuthenticatorFactory.CONFIG_COST);
        return configured != null ? Integer.parseInt(configured.trim()) : BirthdateAuthenticatorFactory.DEFAULT_COST;
    }

    private static String config(AuthenticationFlowContext context, String key) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null || config.getConfig() == null) {
            return null;
        }
        String value = config.getConfig().get(key);
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public boolean requiresUser() {
        return true; // läuft nach Benutzername/Passwort
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return user.getFirstAttribute(USER_ATTRIBUTE) != null;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
