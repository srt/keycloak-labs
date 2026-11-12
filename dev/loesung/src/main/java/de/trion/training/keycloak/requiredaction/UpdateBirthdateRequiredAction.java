package de.trion.training.keycloak.requiredaction;

import de.trion.training.keycloak.birthdate.Birthdates;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;

/**
 * Fragt nach dem Login das Geburtsdatum ab, wenn es noch fehlt, und speichert es im User-Attribut "birthdate".
 * <ul>
 *     <li>evaluateTriggers: läuft bei jedem Login und setzt die Aktion nur, wenn das Attribut fehlt</li>
 *     <li>requiredActionChallenge: zeigt das Formular</li>
 *     <li>processAction: prüft die Eingabe, speichert und meldet success()</li>
 * </ul>
 */
public class UpdateBirthdateRequiredAction implements RequiredActionProvider {

    static final String TEMPLATE = "login-update-birthdate.ftl";
    static final String FIELD = "birthdate";

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        UserModel user = context.getUser();
        if (user.getFirstAttribute(Birthdates.USER_ATTRIBUTE) == null) {
            user.addRequiredAction(UpdateBirthdateRequiredActionFactory.ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        // evaluateTriggers lief beim Login-Beginn. Inzwischen kann eine andere Aktion (z.B. VERIFY_PROFILE)
        // das Attribut schon gesetzt haben -> nicht ein zweites Mal fragen
        if (context.getUser().getFirstAttribute(Birthdates.USER_ATTRIBUTE) != null) {
            context.success();
            return;
        }
        context.challenge(createForm(context, null));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        String birthdate = Birthdates.normalize(context.getHttpRequest().getDecodedFormParameters().getFirst(FIELD));
        if (birthdate == null) {
            context.challenge(createForm(context, "invalidBirthdateFormatMessage"));
            return;
        }

        context.getUser().setSingleAttribute(Birthdates.USER_ATTRIBUTE, birthdate);
        context.success();
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
