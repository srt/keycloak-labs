FROM quay.io/keycloak/keycloak:26.0.2

RUN /opt/keycloak/bin/kc.sh build \
  --db=postgres \
  --health-enabled=true \
  --metrics-enabled=true
