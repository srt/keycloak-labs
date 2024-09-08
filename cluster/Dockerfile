FROM quay.io/keycloak/keycloak:25.0.4

RUN /opt/keycloak/bin/kc.sh build \
  --db=postgres \
  --health-enabled=true \
  --metrics-enabled=true
