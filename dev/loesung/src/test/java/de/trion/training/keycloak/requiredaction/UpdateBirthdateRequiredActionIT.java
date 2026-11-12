package de.trion.training.keycloak.requiredaction;

import de.trion.training.keycloak.ExtensionsServerConfig;
import de.trion.training.keycloak.birthdate.Birthdates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderSimpleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.representations.userprofile.config.UPAttributeRequired;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.ui.annotations.InjectPage;
import org.keycloak.testframework.ui.annotations.InjectWebDriver;
import org.keycloak.testframework.ui.page.LoginPage;
import org.keycloak.testframework.ui.webdriver.ManagedWebDriver;
import org.openqa.selenium.By;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End-Test: Fehlt das Geburtsdatum, fragt Keycloak es nach dem Login ab und speichert es.
 */
@KeycloakIntegrationTest(config = ExtensionsServerConfig.class)
class UpdateBirthdateRequiredActionIT {

    static final String PASSWORD = "Kc-Schulung-2026!";
    static final By FORM = By.id("kc-update-birthdate-form");

    @InjectRealm(config = RequiredActionRealm.class)
    ManagedRealm realm;

    @InjectOAuthClient
    OAuthClient oauth;

    @InjectWebDriver
    ManagedWebDriver driver;

    @InjectPage
    LoginPage loginPage;

    @BeforeEach
    void setUp() {
        enableRequiredAction();
        allowBirthdateAttribute();
        realm.admin().users().list().forEach(u -> realm.admin().users().delete(u.getId()));
    }

    @Test
    void asksForMissingBirthdateAndStoresIt() {
        String userId = createUser("ben", null);
        login("ben");

        assertTrue(isFormShown(), "Formular für das Geburtsdatum erwartet");
        submit("1.4.1990");

        assertNotNull(oauth.parseLoginResponse().getCode(), "Login sollte danach abgeschlossen sein");
        assertEquals(List.of("1990-04-01"), birthdateOf(userId));
    }

    @Test
    void rejectsInvalidDate() {
        String userId = createUser("ben", null);
        login("ben");

        submit("31.02.1990");

        assertTrue(isFormShown(), "Formular muss erneut erscheinen");
        assertTrue(driver.page().getPageSource().contains("gültiges Datum"), "Fehlermeldung erwartet");
        assertNull(birthdateOf(userId));
    }

    @Test
    void doesNotAskWhenBirthdateIsKnown() {
        createUser("clara", "1985-12-24");

        login("clara");

        assertNotNull(oauth.parseLoginResponse().getCode(), "Kein Zwischenschritt erwartet");
    }

    @Test
    void doesNotAskTwiceWhenProfileFormAlreadyAskedForIt() {
        // Jemand macht "birthdate" im User Profile zum Pflichtfeld, das Benutzer selbst pflegen dürfen.
        // Dann fragt bereits VERIFY_PROFILE danach - unsere Aktion darf nicht ein zweites Mal fragen.
        configureBirthdate(new UPAttributePermissions(Set.of("admin", "user"), Set.of("admin", "user")),
                new UPAttributeRequired(Set.of("user"), Set.of()));
        String userId = createUser("ben", null);
        login("ben");

        assertTrue(driver.findElements(FORM).isEmpty(), "Zuerst sollte das Profilformular kommen");
        driver.findElement(By.name("birthdate")).sendKeys("1990-04-01");
        driver.findElement(By.id("kc-update-profile-form")).submit();

        assertNotNull(oauth.parseLoginResponse().getCode(), "Kein zweites Formular für das Geburtsdatum erwartet");
        assertEquals(List.of("1990-04-01"), birthdateOf(userId));
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    private void login(String username) {
        oauth.openLoginForm();
        loginPage.fillLogin(username, PASSWORD);
        loginPage.submit();
    }

    private boolean isFormShown() {
        return !driver.findElements(FORM).isEmpty();
    }

    private void submit(String birthdate) {
        driver.findElement(By.name("birthdate")).clear();
        driver.findElement(By.name("birthdate")).sendKeys(birthdate);
        driver.findElement(FORM).submit();
    }

    private List<String> birthdateOf(String userId) {
        Map<String, List<String>> attributes = realm.admin().users().get(userId).toRepresentation().getAttributes();
        return attributes == null ? null : attributes.get(Birthdates.USER_ATTRIBUTE);
    }

    private String createUser(String username, String birthdate) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmail(username + "@example.org");
        user.setFirstName(username);
        user.setLastName("Muster");
        if (birthdate != null) {
            user.setAttributes(Map.of(Birthdates.USER_ATTRIBUTE, List.of(birthdate)));
        }
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(PASSWORD);
        password.setTemporary(false);
        user.setCredentials(List.of(password));
        realm.admin().users().create(user).close();
        return realm.admin().users().searchByUsername(username, true).get(0).getId();
    }

    /** Eigene Required Actions müssen im Realm registriert und aktiviert sein (wie in setup-realm.sh) */
    private void enableRequiredAction() {
        var flows = realm.admin().flows();
        String id = UpdateBirthdateRequiredActionFactory.ID;
        if (flows.getRequiredActions().stream().noneMatch(a -> a.getAlias().equals(id))) {
            RequiredActionProviderSimpleRepresentation register = new RequiredActionProviderSimpleRepresentation();
            register.setProviderId(id);
            register.setName("Geburtsdatum erfassen");
            flows.registerRequiredAction(register);
        }
        RequiredActionProviderRepresentation action = flows.getRequiredAction(id);
        action.setEnabled(true);
        action.setPriority(200); // nach VERIFY_PROFILE (100)
        flows.updateRequiredAction(id, action);
    }

    /** Standard: "birthdate" nur für Admins (Wissensfaktor) - Benutzer erfassen es über die Required Action */
    private void allowBirthdateAttribute() {
        configureBirthdate(new UPAttributePermissions(Set.of("admin"), Set.of("admin")), null);
    }

    private void configureBirthdate(UPAttributePermissions permissions, UPAttributeRequired required) {
        UPConfig profile = realm.admin().users().userProfile().getConfiguration();
        UPAttribute attribute = new UPAttribute(Birthdates.USER_ATTRIBUTE);
        attribute.setPermissions(permissions);
        attribute.setRequired(required);
        profile.addOrReplaceAttribute(attribute);
        realm.admin().users().userProfile().update(profile);
    }

    // ---------------------------------------------------------------- Konfiguration

    static class RequiredActionRealm implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.internationalizationEnabled(true).supportedLocales("de").defaultLocale("de");
        }
    }
}
