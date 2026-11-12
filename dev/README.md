# Keycloak-Erweiterungen entwickeln und testen

Ein Maven-Projekt für alle Entwicklungs-Übungen, je Thema ein Package:

| Package | Übung | Tests |
|---|---|---|
| `de.trion.training.keycloak.hibp` | Password Policy gegen Have I Been Pwned | `HibpPasswordPolicyProviderTest` (Unit), `HibpPasswordPolicyIT` |
| `de.trion.training.keycloak.birthdate` | Authenticator: Geburtsdatum mit ALTCHA und Brute-Force-Schutz | `BirthdatesTest` (Unit), `BirthdateAuthenticatorIT` (Browser) |
| `de.trion.training.keycloak.requiredaction` | Required Action: Geburtsdatum erfassen | `UpdateBirthdateRequiredActionIT` (Browser) |

- `vorlage/` – Ausgangspunkt mit `TODO`s. Die Tests sind vorgegeben und mit `@Disabled` abgeschaltet.
- `loesung/` – Musterlösung, alle Tests aktiv und grün.

Vorgehen (test-driven): Zum Start einer Übung in deren Testklassen die Zeile `@Disabled` entfernen →
Tests laufen lassen (rot) → implementieren, bis sie grün sind. Fertige Übungen bleiben grün, noch nicht
begonnene werden übersprungen – `mvn verify` und `mvn package` funktionieren also in jedem Zwischenstand.

## Tests ausführen

Voraussetzung: JDK 21+, Maven 3.9+. Der erste Lauf lädt Keycloak (Test Framework) herunter.

```bash
mvn test      # nur Unit-Tests (Sekunden, ohne Keycloak)
mvn verify    # zusätzlich die Integrationstests mit Keycloak
```

Einzelne Tests lassen sich direkt in der IDE starten.

- **Unit-Tests** (`*Test`, Surefire): reine Logik, keine Keycloak-Abhängigkeit, laufen bei `mvn test`.
- **Integrationstests** (`*IT`, Failsafe): das [Keycloak Test Framework](https://github.com/keycloak/keycloak/tree/main/test-framework)
  startet einen echten Keycloak mit den Klassen aus `target/classes` (`dependencyCurrentProject()`),
  legt Realm, Benutzer und Client je Test an und räumt danach auf. Konfiguration: `ExtensionsServerConfig`.
- Der Testserver belegt Port 8080 (und 8500 für den Stub): vorher `docker compose down`. Sonst verbinden sich
  die Integrationstests mit dem laufenden Keycloak und scheitern mit `HTTP 401 Unauthorized`.
- HIBP wird im Test durch einen lokalen HTTP-Server ersetzt (`@InjectHttpServer`) – kein Internet nötig,
  Fehlerfälle (Timeout, HTTP 503) sind simulierbar.

## In der IDE debuggen

Integrationstest in der IDE starten, vorher die Umgebungsvariablen der Run-Konfiguration setzen:

```
KC_TEST_SERVER=embedded        # Keycloak läuft im Test-JVM -> Breakpoints in Test, Extension und Keycloak
KC_TEST_DATABASE=dev-file      # Datenbank zwischen Läufen behalten
KC_TEST_DATABASE_REUSE=true
```

Ohne diese Variablen startet das Framework Keycloak als eigenen Prozess (`distribution`, Standard).
Alternativ lassen sich die Variablen in einer Datei `.env.test` im Projektverzeichnis ablegen.

## Keycloak mit den Erweiterungen von Hand starten

```bash
mvn package -DskipTests
docker compose up -d              # http://localhost:8080, admin / admin, Realm "demo"
docker compose restart keycloak   # nach jedem Neubau
```

- Remote-Debugging: in der IDE "Remote JVM Debug" auf `localhost:8787`.
- Theme-Caching ist abgeschaltet: Änderungen an `.ftl`/CSS sind nach einem Neubau und Reload sichtbar.

### Realm `demo`

- `import/demo-realm.json` wird beim Start importiert. Die Datei wird von `./setup-realm.sh [mit-flow]` erzeugt
  (per `kcadm`, danach Export) und ist eingecheckt, damit `docker compose up` ohne weitere Schritte reicht.
- Änderungen am Realm im Skript vornehmen, **nicht in der JSON-Datei**, und die Datei danach neu erzeugen.
  Ohne Konflikt mit einem laufenden Keycloak:
  ```bash
  mvn package -DskipTests
  export COMPOSE_PROJECT_NAME=realm-gen KC_PORT=18080 KC_DEBUG_PORT=18787
  docker compose up -d keycloak && ./setup-realm.sh mit-flow && docker compose down
  ```
- `--import-realm` überschreibt einen vorhandenen Realm nicht: neuen Stand mit `docker compose down && docker compose up -d` laden.
- Der Export enthält Testpasswort und ALTCHA-Secret im Klartext. Das ist nur für die Übung in Ordnung –
  in echten Projekten gehören Secrets nicht ins Git (Vault, Umgebungsvariablen oder Platzhalter im Export).
