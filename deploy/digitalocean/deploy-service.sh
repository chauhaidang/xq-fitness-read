#!/usr/bin/env bash
set -euo pipefail

# Deploy a single service to DigitalOcean App Platform
# Usage: ./deploy-service.sh [read-service|write-service]

if ! command -v doctl >/dev/null; then
  echo "doctl CLI is required (https://docs.digitalocean.com/reference/doctl/)." >&2
  exit 1
fi

if ! command -v jq >/dev/null; then
  echo "jq is required for parsing DigitalOcean JSON responses." >&2
  exit 1
fi

if ! command -v envsubst >/dev/null; then
  echo "envsubst (gettext) is required to render the App Platform spec template." >&2
  exit 1
fi

: "${DO_TOKEN:?Set DO_TOKEN with a DigitalOcean API token that has write access.}"

SERVICE_NAME="${1:-}"
if [[ -z "$SERVICE_NAME" ]] || [[ ! "$SERVICE_NAME" =~ ^(read-service|write-service)$ ]]; then
  echo "Usage: $0 [read-service|write-service]" >&2
  exit 1
fi

REGION="${REGION:-sgp1}"
APP_NAME="${APP_NAME:-xq-fitness}"
GITHUB_OWNER="${GITHUB_OWNER:-}"
# Default image name based on service type
if [[ "$SERVICE_NAME" == "read-service" ]]; then
  IMAGE_NAME="${IMAGE_NAME:-chauhaidang/xq-fitness-read-service}"
else
  IMAGE_NAME="${IMAGE_NAME:-chauhaidang/xq-fitness-write-service}"
fi
IMAGE_TAG="${IMAGE_TAG:-latest}"
GITHUB_REGISTRY_CREDENTIALS="${GITHUB_REGISTRY_CREDENTIALS:-}"

# Source database connection details (from provision-db.sh or environment)
: "${DB_HOST:?Set DB_HOST (run provision-db.sh first or export DB_* variables)}"
: "${DB_PORT:?Set DB_PORT}"
: "${DB_USER:?Set DB_USER}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
# DB_NAME is optional for read-service as it uses the default database name from application.yml
DB_NAME="${DB_NAME:-xq_fitness}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_SPEC_TEMPLATE="${SCRIPT_DIR}/app-spec.${SERVICE_NAME}.template.yaml"
APP_SPEC_RENDERED="${SCRIPT_DIR}/app-spec.${SERVICE_NAME}.rendered.yaml"

if [[ ! -f "$APP_SPEC_TEMPLATE" ]]; then
  echo "Template file not found: $APP_SPEC_TEMPLATE" >&2
  exit 1
fi

echo ">> Authenticating doctl context"
doctl auth init -t "$DO_TOKEN" >/dev/null

echo ">> Using GitHub Container Registry (ghcr.io)"
echo "   Repository: ghcr.io/${IMAGE_NAME}"
echo "   Tag: ${IMAGE_TAG}"

# Validate required variables
if [[ -z "$IMAGE_NAME" ]]; then
  echo "Error: IMAGE_NAME is required" >&2
  exit 1
fi

if [[ -z "$GITHUB_REGISTRY_CREDENTIALS" ]]; then
  echo "Warning: GITHUB_REGISTRY_CREDENTIALS is not set. This may cause issues with private images." >&2
  echo "   Set it in the format: username:personal-access-token" >&2
fi

export APP_NAME REGION IMAGE_NAME IMAGE_TAG GITHUB_REGISTRY_CREDENTIALS
export DB_HOST DB_PORT DB_USER DB_PASSWORD DB_NAME

# Construct SPRING_DATASOURCE_URL for read-service (Spring Boot)
if [[ "$SERVICE_NAME" == "read-service" ]]; then
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
  echo "   Constructed SPRING_DATASOURCE_URL for read-service"
fi

echo ">> Rendering App Platform spec -> $APP_SPEC_RENDERED"
envsubst < "$APP_SPEC_TEMPLATE" > "$APP_SPEC_RENDERED"

# Remove registry_credentials line if empty (to avoid invalid YAML)
if [[ -z "$GITHUB_REGISTRY_CREDENTIALS" ]]; then
  echo "   Removing empty registry_credentials field"
  sed -i '/registry_credentials: ""$/d' "$APP_SPEC_RENDERED"
  sed -i '/registry_credentials: $/d' "$APP_SPEC_RENDERED"
fi

# Validate rendered spec
if [[ ! -s "$APP_SPEC_RENDERED" ]]; then
  echo "Error: Rendered spec file is empty" >&2
  exit 1
fi

echo ">> Rendered spec saved to: $APP_SPEC_RENDERED"

echo ""
echo ">> Validating rendered spec..."
# Note: doctl apps spec validate might not be available in all versions
# If it fails, we'll continue and let the create/update command validate
if doctl apps spec validate "$APP_SPEC_RENDERED" 2>&1; then
  echo "   ✓ Spec validation passed"
else
  echo "   ⚠️  Spec validation failed or not available, continuing anyway..."
  echo "   (The create/update command will validate the spec)"
fi

echo ">> Creating or updating App Platform app ($APP_NAME)"
APP_ID=$(doctl apps list --output json 2>/dev/null | jq -r '.[] | select(.spec.name=="'"$APP_NAME"'") | .id' || echo "")

if [[ -z "$APP_ID" ]]; then
  echo "   Creating new app..."
  if ! CREATE_RESPONSE=$(doctl apps create --spec "$APP_SPEC_RENDERED" --output json 2>&1); then
    echo "Error creating app:" >&2
    echo "$CREATE_RESPONSE" >&2
    echo "" >&2
    echo "Rendered spec file location: $APP_SPEC_RENDERED" >&2
    echo "(Spec file contains sensitive data and is not displayed)" >&2
    exit 1
  fi
  APP_ID=$(echo "$CREATE_RESPONSE" | jq -r '.app.id // .id // empty')
  if [[ -z "$APP_ID" ]]; then
    echo "Error: Could not extract app ID from create response:" >&2
    echo "$CREATE_RESPONSE" >&2
    exit 1
  fi
  echo "   Created new app: $APP_ID"
else
  echo "   Updating existing app: $APP_ID"
  if ! UPDATE_RESPONSE=$(doctl apps update "$APP_ID" --spec "$APP_SPEC_RENDERED" --output json 2>&1); then
    echo "Error updating app:" >&2
    echo "$UPDATE_RESPONSE" >&2
    echo "" >&2
    echo "Rendered spec file location: $APP_SPEC_RENDERED" >&2
    echo "(Spec file contains sensitive data and is not displayed)" >&2
    exit 1
  fi
  echo "   Updated app: $APP_ID"
fi

cat <<EOF

App Platform app ready: $APP_ID

Next steps:
1. Check deployment status: doctl apps get $APP_ID
2. View app URL: doctl apps get $APP_ID --format DefaultIngress
3. Monitor logs: doctl apps logs $APP_ID --type run --follow
EOF

