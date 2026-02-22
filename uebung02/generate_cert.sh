#!/bin/bash
set -eu

# SERVER_HOSTNAME="studentXX.example.com"

echo "Generating certificate for ${SERVER_HOSTNAME} 📜"
echo

docker run --rm -it -v "$(pwd)/acme.sh:/acme.sh" --net=host neilpang/acme.sh \
  --register-account -m zerossl@example.com

docker run --rm -it -v "$(pwd)/acme.sh:/acme.sh" --net=host neilpang/acme.sh \
  --issue -d "${SERVER_HOSTNAME}" --standalone
