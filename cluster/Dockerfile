FROM quay.io/keycloak/keycloak:24.0.3

RUN /opt/keycloak/bin/kc.sh build \
  --db=postgres \
  --health-enabled=true \
  --metrics-enabled=true
