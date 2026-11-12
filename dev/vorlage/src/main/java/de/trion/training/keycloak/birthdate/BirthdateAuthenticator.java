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
 * Zusätzlicher Wissensfaktor: Geburtsdatum abfragen und mit dem User-Attribut "birthdate" vergleichen,
 * geschützt durch den Brute-Force-Schutz von Keycloak und ALTCHA.
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
        // TODO 3: gesperrte Benutzer gar nicht erst nach dem Geburtsdatum fragen (isDisabledByBruteForce)
        context.challenge(createForm(context, null, null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        UserModel user = context.getUser();
        String birthdate = Birthdates.normalize(form.getFirst(FIELD_BIRTHDATE));   // ISO-Format oder null

        // TODO 3: Vor allen Prüfungen: Ist der Benutzer gesperrt? -> isDisabledByBruteForce(context), dann return

        // Vorgegeben: Captcha prüfen. Ein Fehler hier ist KEIN Fehlversuch, sonst könnte jeder fremde Konten sperren.
        if (!verifyAltcha(context, form.getFirst(FIELD_ALTCHA))) {
            context.getEvent().user(user).error(Errors.INVALID_INPUT);
            context.forceChallenge(createForm(context, "altchaInvalidMessage", null));
            return;
        }

        // TODO 1: Geburtsdatum prüfen (birthdateMatches).
        //         Bei Erfolg: context.success()
        //         Bei Fehler: Event-Fehler Errors.INVALID_USER_CREDENTIALS setzen und das Formular mit Fehlermeldung
        //         "invalidBirthdateMessage" am Feld FIELD_BIRTHDATE erneut anzeigen (createForm) - so, dass der
        //         Fehlversuch für den Brute-Force-Schutz zählt.
        //         Welche Methode: context.forceChallenge(...) oder context.failureChallenge(...)?

        // TODO 2: Zählen die Fehlversuche? (Test wrongBirthdateCountsAsFailedLogin, in der Admin-Konsole:
        //         Users → anna → Brute-Force-Status). Falls nicht: BirthdateAuthenticatorFactory.getReferenceCategory()
    }

    /** Vergleicht die Eingabe (bereits ISO-Format oder null) mit dem User-Attribut USER_ATTRIBUTE. */
    private static boolean birthdateMatches(UserModel user, String given) {
        // TODO 1a: user.getFirstAttribute(USER_ATTRIBUTE) mit given vergleichen; null auf beiden Seiten = false.
        //          Zusatz: Vergleich in konstanter Zeit (MessageDigest.isEqual) - das Datum ist ein Geheimnis
        return false;
    }

    /** Gesperrte Benutzer abweisen, ohne den Fehlerzähler weiter zu erhöhen. true = gesperrt, Antwort ist gesetzt. */
    private boolean isDisabledByBruteForce(AuthenticationFlowContext context) {
        // TODO 3a: AuthenticatorUtils.getDisabledByBruteForceEventError(context, user) liefert bei Sperre einen
        //          Event-Fehler, sonst null. Bei Sperre: Event-Fehler setzen und mit forceChallenge das Formular mit
        //          Messages.ACCOUNT_TEMPORARILY_DISABLED anzeigen
        return false;
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

    /** Erzeugt eine signierte Proof-of-Work-Aufgabe, die das Widget im Browser löst. */
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
