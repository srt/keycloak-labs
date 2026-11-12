package de.trion.training.keycloak.birthdate;

import org.junit.jupiter.api.Disabled;
import de.trion.training.keycloak.ExtensionsServerConfig;
import org.altcha.altcha.v2.Altcha;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
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
import org.openqa.selenium.JavascriptExecutor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End-Test im Browser (HtmlUnit): Benutzername/Passwort, danach das Geburtsdatum.
 * Den Proof of Work von ALTCHA übernimmt der Test (sonst der Browser per JavaScript).
 */
@Disabled("Übung Authenticator: zum Start dieser Übung diese Zeile entfernen")
@KeycloakIntegrationTest(config = ExtensionsServerConfig.class)
class BirthdateAuthenticatorIT {

    static final String FLOW = "browser-birthdate";
    static final String SECRET = "nur-fuer-tests";
    static final String PASSWORD = "Kc-Schulung-2026!";

    @InjectRealm(config = BirthdateRealm.class)
    ManagedRealm realm;

    @InjectOAuthClient
    OAuthClient oauth;

    @InjectWebDriver
    ManagedWebDriver driver;

    @InjectPage
    LoginPage loginPage;

    String userId;

    @BeforeEach
    void createUser() {
        allowBirthdateAttribute();
        realm.admin().users().searchByUsername("anna", true).forEach(u -> realm.admin().users().delete(u.getId()));

        UserRepresentation anna = new UserRepresentation();
        anna.setUsername("anna");
        anna.setEnabled(true);
        anna.setEmail("anna@example.org");
        anna.setFirstName("Anna");
        anna.setLastName("Muster");
        anna.setAttributes(Map.of(Birthdates.USER_ATTRIBUTE, List.of("1990-04-01")));
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(PASSWORD);
        password.setTemporary(false);
        anna.setCredentials(List.of(password));
        realm.admin().users().create(anna).close();
        userId = realm.admin().users().searchByUsername("anna", true).get(0).getId();
    }

    @Test
    void correctBirthdateLogsIn() {
        loginWithPassword();

        submitBirthdate("1.4.1990", true);

        assertNotNull(oauth.parseLoginResponse().getCode(), "Login sollte erfolgreich sein");
    }

    @Test
    void wrongBirthdateCountsAsFailedLogin() {
        loginWithPassword();

        submitBirthdate("2.4.1990", true);

        assertTrue(driver.page().getPageSource().contains("Das Geburtsdatum ist nicht korrekt"));
        assertEquals(1, failures(), "Fehlversuch muss für den Brute-Force-Schutz zählen");
    }

    @Test
    void missingCaptchaIsNotCountedAsFailure() {
        loginWithPassword();

        submitBirthdate("2.4.1990", false);

        assertTrue(driver.page().getPageSource().contains("Captcha"));
        assertEquals(0, failures(), "Ohne Captcha darf nichts gezählt werden (sonst kann jeder fremde Konten sperren)");
    }

    @Test
    void lockedUserIsRejectedEvenWithCorrectBirthdate() {
        loginWithPassword();
        for (int i = 0; i < 3; i++) {
            submitBirthdate("2.4.1990", true);
        }

        submitBirthdate("1.4.1990", true);

        assertTrue(driver.page().getPageSource().contains("deaktiviert") || driver.page().getPageSource().contains("disabled"),
                "Gesperrter Benutzer darf sich nicht anmelden");
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    private void loginWithPassword() {
        oauth.openLoginForm();
        loginPage.fillLogin("anna", PASSWORD);
        loginPage.submit();
        assertNotNull(driver.findElement(By.id("kc-birthdate-form")), "Formular für das Geburtsdatum erwartet");
    }

    private void submitBirthdate(String birthdate, boolean solveCaptcha) {
        driver.findElement(By.name("birthdate")).clear();
        driver.findElement(By.name("birthdate")).sendKeys(birthdate);
        if (solveCaptcha) {
            String challenge = driver.findElement(By.tagName("altcha-widget")).getAttribute("challenge");
            ((JavascriptExecutor) driver.driver()).executeScript("""
                    var form = document.getElementById('kc-birthdate-form');
                    var input = form.querySelector('input[name=altcha]') || document.createElement('input');
                    input.type = 'hidden'; input.name = 'altcha'; input.value = arguments[0];
                    form.appendChild(input);""", solve(challenge));
        }
        driver.findElement(By.id("kc-birthdate-form")).submit();
    }

    private int failures() {
        return ((Number) realm.admin().attackDetection().bruteForceUserStatus(userId).get("numFailures")).intValue();
    }

    /** Löst die ALTCHA-Challenge wie das Widget im Browser und liefert den Payload für das Formularfeld. */
    static String solve(String challengeJson) {
        try {
            // parsePayload erwartet {challenge, solution}; die Lösung ist hier nur ein Platzhalter
            String dummy = "{\"challenge\":" + challengeJson + ",\"solution\":{\"counter\":0,\"derivedKey\":\"00\"}}";
            Altcha.Challenge challenge = Altcha.parsePayload(base64(dummy)).challenge();
            Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.pbkdf2());
            return base64("{\"challenge\":" + challengeJson + ",\"solution\":{\"counter\":" + solution.counter()
                    + ",\"derivedKey\":\"" + solution.derivedKey() + "\"}}");
        } catch (Exception e) {
            throw new IllegalStateException("ALTCHA-Challenge nicht lösbar", e);
        }
    }

    private static String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** User Profile: Attribut "birthdate" bekannt machen, sonst verwirft Keycloak es beim Anlegen. */
    private void allowBirthdateAttribute() {
        UPConfig profile = realm.admin().users().userProfile().getConfiguration();
        if (profile.getAttribute(Birthdates.USER_ATTRIBUTE) == null) {
            UPAttribute attribute = new UPAttribute(Birthdates.USER_ATTRIBUTE);
            attribute.setPermissions(new UPAttributePermissions(Set.of("admin"), Set.of("admin")));
            profile.addOrReplaceAttribute(attribute);
            realm.admin().users().userProfile().update(profile);
        }
    }

    // ---------------------------------------------------------------- Konfiguration

    /** Realm mit Brute-Force-Schutz und eigenem Browser-Flow: Cookie | (Passwort + Geburtsdatum) */
    static class BirthdateRealm implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.bruteForceProtected(true).failureFactor(3)
                    .internationalizationEnabled(true).supportedLocales("de").defaultLocale("de") // Meldungen auf Deutsch
                    .update(r -> {
                        AuthenticatorConfigRepresentation config = new AuthenticatorConfigRepresentation();
                        config.setAlias("birthdate-config");
                        config.setConfig(Map.of(BirthdateAuthenticatorFactory.CONFIG_SECRET, SECRET,
                                BirthdateAuthenticatorFactory.CONFIG_COST, "100")); // schnell lösbar im Test
                        r.setAuthenticatorConfig(new ArrayList<>(List.of(config)));

                        AuthenticationExecutionExportRepresentation birthdate =
                                execution(BirthdateAuthenticatorFactory.ID, "REQUIRED", 20);
                        birthdate.setAuthenticatorConfig("birthdate-config");
                        r.setAuthenticationFlows(new ArrayList<>(List.of(
                                flow(FLOW, true, execution("auth-cookie", "ALTERNATIVE", 10), subflow(FLOW + "-forms", 20)),
                                flow(FLOW + "-forms", false, execution("auth-username-password-form", "REQUIRED", 10), birthdate))));
                        r.setBrowserFlow(FLOW);
                    });
        }

        static AuthenticationFlowRepresentation flow(String alias, boolean topLevel,
                                                    AuthenticationExecutionExportRepresentation... executions) {
            AuthenticationFlowRepresentation flow = new AuthenticationFlowRepresentation();
            flow.setAlias(alias);
            flow.setProviderId("basic-flow");
            flow.setTopLevel(topLevel);
            flow.setBuiltIn(false);
            flow.setAuthenticationExecutions(List.of(executions));
            return flow;
        }

        static AuthenticationExecutionExportRepresentation execution(String authenticator, String requirement, int priority) {
            AuthenticationExecutionExportRepresentation e = new AuthenticationExecutionExportRepresentation();
            e.setAuthenticator(authenticator);
            e.setRequirement(requirement);
            e.setPriority(priority);
            e.setAuthenticatorFlow(false);
            return e;
        }

        static AuthenticationExecutionExportRepresentation subflow(String alias, int priority) {
            AuthenticationExecutionExportRepresentation e = new AuthenticationExecutionExportRepresentation();
            e.setFlowAlias(alias);
            e.setRequirement("ALTERNATIVE");
            e.setPriority(priority);
            e.setAuthenticatorFlow(true);
            return e;
        }
    }
}
