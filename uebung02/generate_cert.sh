#!/bin/bash
set -eu

# SERVER_HOSTNAME="studentXX.example.com"

echo "Generating certificate for ${SERVER_HOSTNAME} 📜"
echo

docker run --rm -it -v "$(pwd)/acme.sh:/acme.sh" -p 80:80 -p 443:443 neilpang/acme.sh \
  --register-account -m zerossl@example.com

docker run --rm -it -v "$(pwd)/acme.sh:/acme.sh" -p 80:80 -p 443:443 neilpang/acme.sh \
  --issue -d "${SERVER_HOSTNAME}" --standalone
