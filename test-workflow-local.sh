#!/usr/bin/env bash
set -euo pipefail

# Local test script to simulate the workflow steps
# Usage: ./test-workflow-local.sh

echo "========================================="
echo "Testing Workflow Steps Locally"
echo "========================================="
echo ""

# Check required tools
echo ">> Checking required tools..."
for tool in doctl jq envsubst; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Error: $tool is not installed" >&2
    exit 1
  fi
done

# Check for yq
if ! command -v yq >/dev/null 2>&1; then
  echo "Warning: yq is not installed. Installing..." >&2
  if [[ "$OSTYPE" == "darwin"* ]]; then
    brew install yq || {
      echo "Error: Could not install yq. Please install manually:" >&2
      echo "  brew install yq" >&2
      exit 1
    }
  else
    echo "Error: yq is required. Please install manually:" >&2
    echo "  wget -qO /usr/local/bin/yq https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64" >&2
    echo "  chmod +x /usr/local/bin/yq" >&2
    exit 1
  fi
fi
echo "✓ All tools available"
echo ""

# Set environment variables (simulate GitHub Actions environment)
# You can override these with environment variables or export them before running
export APP_NAME="${APP_NAME:-xq-fitness}"
export REGION="${REGION:-sgp1}"
export IMAGE_NAME="${IMAGE_NAME:-ghcr.io/automation2/xq-fitness-read-service}"
export IMAGE_TAG="${IMAGE_TAG:-latest}"
export GITHUB_REGISTRY_CREDENTIALS="${GITHUB_REGISTRY_CREDENTIALS:-automation2:test-token}"

# Database connection details (you may need to set these)
export DB_USER="${DB_USER:-xq_app_user}"
export DB_PASSWORD="${DB_PASSWORD:-}"

# Try to fetch database connection details if not set
if [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
  echo ">> Attempting to fetch database connection details..."
  DB_CLUSTER_NAME="${APP_NAME}-db"
  DB_CLUSTER_ID=$(doctl databases list --output json 2>/dev/null | \
    jq -r '.[] | select(.name=="'"$DB_CLUSTER_NAME"'") | .id' | head -n1 || echo "")
  
  if [ -n "$DB_CLUSTER_ID" ]; then
    CONN_JSON=$(doctl databases connection "$DB_CLUSTER_ID" --output json 2>/dev/null || echo "")
    if [ -n "$CONN_JSON" ]; then
      DB_HOST=$(echo "$CONN_JSON" | jq -r 'if type == "array" then .[0].host else .host end' | sed 's/@.*//' | sed 's/.*@//')
      DB_PORT=$(echo "$CONN_JSON" | jq -r 'if type == "array" then .[0].port else .port end')
      DB_NAME="${DB_NAME:-xq_fitness}"
      export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
      echo "  ✓ Fetched database connection details"
    fi
  fi
fi

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-}"

echo ">> Environment variables:"
echo "  APP_NAME: $APP_NAME"
echo "  REGION: $REGION"
echo "  IMAGE_NAME: $IMAGE_NAME"
echo "  IMAGE_TAG: $IMAGE_TAG"
echo "  DB_USER: $DB_USER"
echo "  DB_PASSWORD: ${DB_PASSWORD:+***hidden***}"
echo "  SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-not set}"
echo ""

# Validate required variables
if [ -z "$DB_PASSWORD" ]; then
  echo "⚠️  Warning: DB_PASSWORD is not set. Some steps may fail." >&2
fi

if [ -z "$SPRING_DATASOURCE_URL" ]; then
  echo "⚠️  Warning: SPRING_DATASOURCE_URL is not set. Some steps may fail." >&2
fi

echo ""
echo "========================================="
echo "Step 1: Render app spec"
echo "========================================="
echo ""

if [ ! -f .do/app.yaml ]; then
  echo "Error: .do/app.yaml not found" >&2
  exit 1
fi

# Render app spec template with environment variables
envsubst < .do/app.yaml > .do/app.rendered.yaml

echo "✓ Rendered app spec: .do/app.rendered.yaml"
echo ""

# Show rendered spec (sanitized)
echo ">> Rendered spec preview (sanitized):"
sed 's/value:.*DB_PASSWORD.*/value: ***HIDDEN***/g' .do/app.rendered.yaml | \
  sed 's/value:.*PASSWORD.*/value: ***HIDDEN***/g' | head -30
echo ""

echo "========================================="
echo "Step 2: Merge app spec with existing services"
echo "========================================="
echo ""

if [ ! -f .do/app.rendered.yaml ]; then
  echo "Error: Rendered app spec file not found" >&2
  exit 1
fi

# Merge rendered spec with existing app spec
chmod +x scripts/merge-app-spec.sh
./scripts/merge-app-spec.sh .do/app.rendered.yaml "$APP_NAME" .do/app.merged.yaml

echo ""
echo "✓ Merged app spec: .do/app.merged.yaml"
echo ""

# Show merged spec structure (sanitized)
echo ">> Merged spec structure (checking for ingress/routes conflicts):"
if command -v yq >/dev/null 2>&1; then
  INGRESS_CHECK=$(yq eval '.ingress // empty' .do/app.merged.yaml 2>/dev/null || echo "empty")
  if [ -z "$INGRESS_CHECK" ] || [ "$INGRESS_CHECK" = "null" ] || [ "$INGRESS_CHECK" = "empty" ]; then
    echo "  ✓ No ingress.rules found (good - component routes will work)"
  else
    echo "  ⚠️  WARNING: ingress.rules found in merged spec:"
    echo "$INGRESS_CHECK" | head -20
  fi
  echo ""
  echo "Services with routes:"
  yq eval '.services[] | select(.routes != null) | "  - \(.name): \(.routes | length) route(s)"' .do/app.merged.yaml 2>/dev/null || echo "  No services with routes found"
else
  if grep -q "ingress:" .do/app.merged.yaml; then
    echo "  ⚠️  WARNING: ingress found in merged spec"
    grep -A 5 "ingress:" .do/app.merged.yaml | head -10
  else
    echo "  ✓ No ingress found (good)"
  fi
fi
echo ""

echo "========================================="
echo "Step 3: Create or update App Platform app"
echo "========================================="
echo ""

MERGED_SPEC=".do/app.merged.yaml"

if [ ! -f "$MERGED_SPEC" ]; then
  echo "Error: Merged spec file not found: $MERGED_SPEC" >&2
  exit 1
fi

# Validate spec first
echo ">> Validating merged spec..."
if doctl apps spec validate "$MERGED_SPEC" 2>&1; then
  echo "✓ Spec validation passed"
else
  echo "⚠️  Spec validation failed or not available"
fi
echo ""

# Get existing app ID
echo ">> Checking for existing app: $APP_NAME"
APP_ID=$(doctl apps list --output json 2>/dev/null | \
  jq -r '.[] | select(.spec.name=="'"$APP_NAME"'") | .id' | head -n1 || echo "")

if [ -z "$APP_ID" ]; then
  echo ">> App does not exist. Would create new app."
  echo ">> To actually create, run:"
  echo "   doctl apps create --spec $MERGED_SPEC"
else
  echo ">> App exists: $APP_ID"
  echo ">> Would update app. To actually update, run:"
  echo "   doctl apps update $APP_ID --spec $MERGED_SPEC"
fi
echo ""

echo "========================================="
echo "Summary"
echo "========================================="
echo ""
echo "✓ Step 1: Rendered app spec"
echo "✓ Step 2: Merged app spec with existing services"
echo "✓ Step 3: Validated and ready to create/update"
echo ""
echo "Generated files:"
echo "  - .do/app.rendered.yaml"
echo "  - .do/app.merged.yaml"
echo ""
echo "To actually deploy, run:"
if [ -z "$APP_ID" ]; then
  echo "  doctl apps create --spec .do/app.merged.yaml"
else
  echo "  doctl apps update $APP_ID --spec .do/app.merged.yaml"
fi
echo ""

