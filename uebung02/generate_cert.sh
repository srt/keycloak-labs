#!/bin/bash
set -eu
cd "$(dirname "$0")"

# Certificate for the host Keycloak is reached at: KEYCLOAK_HOST from .env (set it first, see the
# exercise), or the hostname given as argument, e.g. ./generate_cert.sh student05.lvm.trion-training.de
if [ -f .env ]; then
  set -a; . ./.env; set +a
fi
SERVER_HOSTNAME="${1:-${KEYCLOAK_HOST:-}}"

if [ -z "${SERVER_HOSTNAME}" ] || [ "${SERVER_HOSTNAME}" = "keycloak.local.2qn.org" ]; then
  echo "Bitte zuerst in .env KEYCLOAK_HOST auf den Hostnamen deiner Schulungs-VM setzen" >&2
  echo "(z. B. KEYCLOAK_HOST=$(hostname -s).<schulung>.trion-training.de) oder ihn als Argument angeben." >&2
  exit 1
fi

echo "Generating certificate for ${SERVER_HOSTNAME} 📜"
echo

docker run --rm -it -v "$(pwd)/acme.sh:/acme.sh" -p 80:80 -p 443:443 neilpang/acme.sh \
  --register-account -m zerossl@example.com

docker run --rm -it -v "$(pwd)/acme.sh:/acme.sh" -p 80:80 -p 443:443 neilpang/acme.sh \
  --issue -d "${SERVER_HOSTNAME}" --standalone

echo
echo "Fertig. Zertifikat für Traefik übernehmen:"
echo "  sudo cp acme.sh/${SERVER_HOSTNAME}_ecc/${SERVER_HOSTNAME}.key certs/privkey.pem"
echo "  sudo cp acme.sh/${SERVER_HOSTNAME}_ecc/fullchain.cer certs/cert.pem"
