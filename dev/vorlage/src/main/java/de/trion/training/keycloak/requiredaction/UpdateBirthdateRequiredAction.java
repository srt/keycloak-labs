package de.trion.training.keycloak.requiredaction;

import de.trion.training.keycloak.birthdate.Birthdates;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.utils.FormMessage;

/**
 * Fragt nach dem Login das Geburtsdatum ab, wenn es noch fehlt, und speichert es im User-Attribut "birthdate".
 * Tests: UpdateBirthdateRequiredActionIT
 */
public class UpdateBirthdateRequiredAction implements RequiredActionProvider {

    static final String TEMPLATE = "login-update-birthdate.ftl";
    static final String FIELD = "birthdate";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        // TODO 1: Läuft bei jedem Login. Fehlt das Attribut Birthdates.USER_ATTRIBUTE,
        //         die Aktion am Benutzer setzen: user.addRequiredAction(UpdateBirthdateRequiredActionFactory.ID)
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        // TODO 2: Formular anzeigen: context.challenge(createForm(context, null))
        // TODO 2a: Ist das Attribut inzwischen schon gesetzt (z.B. durch VERIFY_PROFILE)? Dann context.success()
        //          statt Formular (Test doesNotAskTwiceWhenProfileFormAlreadyAskedForIt)
    }

    @Override
    public void processAction(RequiredActionContext context) {
        // TODO 3: Eingabe aus context.getHttpRequest().getDecodedFormParameters() lesen (Feld FIELD)
        //         und mit Birthdates.normalize(...) prüfen.
        //         - ungültig: Formular mit Fehler "invalidBirthdateFormatMessage" erneut anzeigen
        //         - gültig:   Attribut am Benutzer speichern, dann context.success()
    }

    private Response createForm(RequiredActionContext context, String error) {
        var form = context.form();
        if (error != null) {
            form.addError(new FormMessage(FIELD, error));
        }
        return form.createForm(TEMPLATE);
    }

    @Override
    public void close() {
    }
}
