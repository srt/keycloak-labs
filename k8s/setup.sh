KEYCLOAK_VERSION="26.5.4"

echo ""
echo "🔐 Installing Keycloak via operator..."
kubectl apply \
  -f "https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/${KEYCLOAK_VERSION}/kubernetes/keycloaks.k8s.keycloak.org-v1.yml" \
  -f "https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/${KEYCLOAK_VERSION}/kubernetes/keycloakrealmimports.k8s.keycloak.org-v1.yml"
kubectl create namespace keycloak
kubectl -n keycloak apply \
  -f "https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/${KEYCLOAK_VERSION}/kubernetes/kubernetes.yml"
  -f pg-cluster.yaml \
  -f keycloak.yaml

echo
echo Password for keycloak temp-admin user will be at:"
echo "kubectl get -n keycloak secrets/keycloak-initial-admin -o jsonpath='{.data.password}' | base64 -d | xclip"
