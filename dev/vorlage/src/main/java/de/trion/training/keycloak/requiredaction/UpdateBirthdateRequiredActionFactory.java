package de.trion.training.keycloak.requiredaction;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/** Registriert die Aktion unter Authentication → Required actions. Die Factory gibt es einmal pro Server. */
public class UpdateBirthdateRequiredActionFactory implements RequiredActionFactory {

    public static final String ID = "update-birthdate";

    private static final UpdateBirthdateRequiredAction INSTANCE = new UpdateBirthdateRequiredAction(); // zustandslos

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayText() {
        return "Geburtsdatum erfassen";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return INSTANCE;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
