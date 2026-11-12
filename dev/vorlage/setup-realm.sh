#!/usr/bin/env bash
# Erzeugt den Realm "demo" für die Übung per kcadm und exportiert ihn nach import/demo-realm.json.
# Aufruf: ./setup-realm.sh [mit-flow]  (Vorlage: ohne Flow - den baut ihr selbst)   (läuft gegen den gestarteten Container)
#
# import/demo-realm.json wird von diesem Skript erzeugt und mit eingecheckt (Start per "docker compose up"
# ohne weitere Schritte). Änderungen am Realm hier vornehmen, nicht in der JSON-Datei.
# Neu erzeugen, ohne einen laufenden Keycloak zu stören:
#   mvn package -DskipTests
#   export COMPOSE_PROJECT_NAME=realm-gen KC_PORT=18080 KC_DEBUG_PORT=18787
#   docker compose up -d keycloak && ./setup-realm.sh [mit-flow] && docker compose down
#
# Achtung: Der Export enthält Passwort und ALTCHA-Secret im Klartext - nur für die Übung.
# In echten Projekten gehören Secrets nicht ins Git (Vault, Umgebungsvariablen, Platzhalter).
set -euo pipefail
cd "$(dirname "$0")"
R=demo
kc() { docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }

kc config credentials --server http://localhost:8080 --realm master --user admin --password admin >/dev/null
kc delete realms/$R >/dev/null 2>&1 || true
kc create realms -s realm=$R -s enabled=true -s 'displayName=Demo (Übung Authenticator)' \
  -s bruteForceProtected=true -s failureFactor=3 -s waitIncrementSeconds=60 -s maxFailureWaitSeconds=900 \
  -s internationalizationEnabled=true -s 'supportedLocales=["de","en"]' -s defaultLocale=de

# User Profile: Attribut "birthdate" - nur für Admins sichtbar/änderbar (Wissensfaktor, wie ein Passwort);
# Benutzer erfassen es über die Required Action "Geburtsdatum erfassen"
kc get users/profile -r $R | jq '.attributes += [{
    name: "birthdate", displayName: "Geburtsdatum",
    validations: {pattern: {pattern: "^\\d{4}-\\d{2}-\\d{2}$", "error-message": "Format JJJJ-MM-TT"}},
    permissions: {view: ["admin"], edit: ["admin"]}, multivalued: false}]' \
  | kc update users/profile -r $R -f -

kc create users -r $R -s username=anna -s enabled=true -s email=anna@example.org -s emailVerified=true \
  -s firstName=Anna -s lastName=Muster -s 'attributes.birthdate=["1990-04-01"]' >/dev/null
kc set-password -r $R --username anna --new-password 'Kc-Schulung-2026!'

if [[ "${1:-}" == "mit-flow" ]]; then
  F="browser-birthdate"
  kc create authentication/flows -r $R -s alias=$F -s providerId=basic-flow -s topLevel=true -s builtIn=false >/dev/null
  kc create authentication/flows/$F/executions/execution -r $R -s provider=auth-cookie >/dev/null
  kc create authentication/flows/$F/executions/flow -r $R -s alias=$F-forms -s type=basic-flow >/dev/null
  kc create authentication/flows/$F-forms/executions/execution -r $R -s provider=auth-username-password-form >/dev/null
  kc create authentication/flows/$F-forms/executions/execution -r $R -s provider=birthdate-altcha >/dev/null
  kc get authentication/flows/$F/executions -r $R | jq -c '.[] | select(.level==0) | .requirement="ALTERNATIVE"' \
    | while read -r e; do echo "$e" | kc update authentication/flows/$F/executions -r $R -f -; done
  kc get authentication/flows/$F-forms/executions -r $R | jq -c '.[] | .requirement="REQUIRED"' \
    | while read -r e; do echo "$e" | kc update authentication/flows/$F-forms/executions -r $R -f -; done
  # Konfiguration mit festem Secret (sonst: Zufallswert pro Server-Start)
  EID=$(kc get authentication/flows/$F-forms/executions -r $R | jq -r '.[] | select(.providerId=="birthdate-altcha") | .id')
  kc create authentication/executions/$EID/config -r $R -s alias=birthdate-altcha-config \
    -s 'config.altchaSecret=bitte-in-produktion-aus-dem-vault' -s 'config.altchaCost=5000' >/dev/null
  kc update realms/$R -s browserFlow=$F
fi

kc create "realms/$R/partial-export?exportClients=true&exportGroupsAndRoles=true" -o > import/$R-realm.json.tmp
USERS=$(kc get users -r $R -q briefRepresentation=false \
  | jq '[.[] | del(.id, .createdTimestamp, .access) + {realmRoles: ["default-roles-demo"], credentials: [{type: "password", value: "Kc-Schulung-2026!", temporary: false}]}]')
# Der Export maskiert Secrets ("**********") -> für den Import den Klartext wieder einsetzen
jq --argjson u "$USERS" '.users = $u
  | (.authenticatorConfig[]? | select(.alias == "birthdate-altcha-config") | .config.altchaSecret) = "bitte-in-produktion-aus-dem-vault"' \
  import/$R-realm.json.tmp > import/$R-realm.json && rm import/$R-realm.json.tmp
echo "Realm $R exportiert nach import/$R-realm.json"
