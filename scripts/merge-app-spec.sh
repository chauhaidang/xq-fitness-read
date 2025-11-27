#!/usr/bin/env bash
set -euo pipefail

# Merge a service spec into an existing DigitalOcean App Platform app spec
# This prevents overwriting other services when deploying a single service
# Automatically detects services in the new spec and merges them
#
# Usage: ./merge-app-spec.sh <new-service-spec-file> <app-name> [output-file]
#
# Example:
#   ./merge-app-spec.sh .do/app.rendered.yaml xq-fitness .do/app.merged.yaml

if [ $# -lt 2 ]; then
  echo "Usage: $0 <new-service-spec-file> <app-name> [output-file]" >&2
  echo "" >&2
  echo "Arguments:" >&2
  echo "  new-service-spec-file  Path to the new service spec YAML file" >&2
  echo "  app-name               Name of the DigitalOcean App Platform app" >&2
  echo "  output-file            (Optional) Output file for merged spec (default: <new-service-spec-file>.merged.yaml)" >&2
  exit 1
fi

NEW_SPEC_FILE="${1}"
APP_NAME="${2}"
OUTPUT_FILE="${3:-${NEW_SPEC_FILE%.*}.merged.yaml}"

# Validate inputs
if [ ! -f "$NEW_SPEC_FILE" ]; then
  echo "Error: New service spec file not found: $NEW_SPEC_FILE" >&2
  exit 1
fi

if [ -z "$APP_NAME" ]; then
  echo "Error: App name cannot be empty" >&2
  exit 1
fi

# Check required tools
if ! command -v doctl >/dev/null; then
  echo "Error: doctl is required but not installed" >&2
  exit 1
fi

if ! command -v jq >/dev/null; then
  echo "Error: jq is required but not installed" >&2
  exit 1
fi

if ! command -v yq >/dev/null; then
  echo "Error: yq is required but not installed" >&2
  echo "Install with: wget -qO /usr/local/bin/yq https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 && chmod +x /usr/local/bin/yq" >&2
  exit 1
fi

echo ">> Merging service spec"
echo "   New spec file: $NEW_SPEC_FILE"
echo "   App name: $APP_NAME"
echo "   Output file: $OUTPUT_FILE"
echo ""

# Get existing app ID if it exists
APP_ID=$(doctl apps list --output json 2>/dev/null | jq -r '.[] | select(.spec.name=="'"$APP_NAME"'") | .id' | head -n1 || echo "")

if [ -z "$APP_ID" ]; then
  echo ">> App '$APP_NAME' does not exist, using new spec as-is"
  cp "$NEW_SPEC_FILE" "$OUTPUT_FILE"
  echo "✓ Created merged spec: $OUTPUT_FILE"
  exit 0
fi

echo ">> App '$APP_NAME' exists (ID: $APP_ID), fetching current spec..."

# Fetch current app spec
CURRENT_SPEC=$(doctl apps spec get "$APP_ID" --output yaml 2>/dev/null || echo "")

if [ -z "$CURRENT_SPEC" ]; then
  echo "⚠️  Warning: Could not fetch current spec, using new spec as-is (may remove other services)" >&2
  cp "$NEW_SPEC_FILE" "$OUTPUT_FILE"
  echo "✓ Created merged spec: $OUTPUT_FILE"
  exit 0
fi

echo ">> Merging services from new spec into existing app spec..."

# Save current spec to temporary file
CURRENT_SPEC_FILE=$(mktemp)
echo "$CURRENT_SPEC" > "$CURRENT_SPEC_FILE"

# Convert both specs to JSON for easier merging
CURRENT_JSON=$(mktemp)
NEW_JSON=$(mktemp)
MERGED_JSON=$(mktemp)
MERGED_JSON_TMP=$(mktemp)

yq eval -o json "$CURRENT_SPEC_FILE" > "$CURRENT_JSON"
yq eval -o json "$NEW_SPEC_FILE" > "$NEW_JSON"

# Get all service names from new spec
NEW_SERVICE_NAMES=$(jq -r '.services[].name' "$NEW_JSON")

if [ -z "$NEW_SERVICE_NAMES" ]; then
  echo "Error: No services found in new spec file" >&2
  rm -f "$CURRENT_SPEC_FILE" "$CURRENT_JSON" "$NEW_JSON" "$MERGED_JSON" "$MERGED_JSON_TMP"
  exit 1
fi

echo ">> Services to merge: $(echo "$NEW_SERVICE_NAMES" | tr '\n' ' ')"
echo ""

# Check if services actually changed to avoid unnecessary redeployments
# (DigitalOcean redeploys ALL services when app spec is updated)
SERVICES_CHANGED=false

for SERVICE_NAME in $NEW_SERVICE_NAMES; do
  echo "  - Checking service: $SERVICE_NAME"
  
  # Extract the service from new spec
  NEW_SERVICE_JSON=$(jq '.services[] | select(.name == "'"$SERVICE_NAME"'")' "$NEW_JSON")
  
  if [ -z "$NEW_SERVICE_JSON" ] || [ "$NEW_SERVICE_JSON" == "null" ]; then
    echo "    ⚠️  Warning: Service '$SERVICE_NAME' not found in new spec, skipping" >&2
    continue
  fi
  
  # Check if service exists in current spec
  EXISTING_SERVICE_JSON=$(jq '.services[] | select(.name == "'"$SERVICE_NAME"'")' "$CURRENT_JSON")
  
  if [ -z "$EXISTING_SERVICE_JSON" ] || [ "$EXISTING_SERVICE_JSON" == "null" ]; then
    echo "    → Service is new, will be added"
    SERVICES_CHANGED=true
  else
    # Compare service configs (normalize JSON for comparison)
    NEW_SERVICE_NORMALIZED=$(echo "$NEW_SERVICE_JSON" | jq -S '.')
    EXISTING_SERVICE_NORMALIZED=$(echo "$EXISTING_SERVICE_JSON" | jq -S '.')
    
    if [ "$NEW_SERVICE_NORMALIZED" != "$EXISTING_SERVICE_NORMALIZED" ]; then
      echo "    → Service configuration changed, will be updated"
      SERVICES_CHANGED=true
    else
      echo "    ✓ Service configuration unchanged, skipping update"
      echo "      (This prevents unnecessary redeployment of other services)"
    fi
  fi
done

echo ""

# If no services changed, use current spec to avoid redeployment
if [ "$SERVICES_CHANGED" = false ]; then
  echo ">> No service changes detected. Using current spec to avoid redeployment."
  cp "$CURRENT_SPEC_FILE" "$OUTPUT_FILE"
  rm -f "$CURRENT_SPEC_FILE" "$CURRENT_JSON" "$NEW_JSON" "$MERGED_JSON" "$MERGED_JSON_TMP"
  echo "✓ No changes needed, using existing spec: $OUTPUT_FILE"
  exit 0
fi

# Merge services that changed
echo ">> Merging changed services into app spec..."
cp "$CURRENT_JSON" "$MERGED_JSON_TMP"

for SERVICE_NAME in $NEW_SERVICE_NAMES; do
  # Extract the service from new spec
  SERVICE_JSON=$(jq '.services[] | select(.name == "'"$SERVICE_NAME"'")' "$NEW_JSON")
  
  if [ -z "$SERVICE_JSON" ] || [ "$SERVICE_JSON" == "null" ]; then
    continue
  fi
  
  # Check if this service actually changed (we already checked above, but double-check)
  EXISTING_SERVICE_JSON=$(jq '.services[] | select(.name == "'"$SERVICE_NAME"'")' "$CURRENT_JSON")
  if [ -n "$EXISTING_SERVICE_JSON" ] && [ "$EXISTING_SERVICE_JSON" != "null" ]; then
    NEW_SERVICE_NORMALIZED=$(echo "$SERVICE_JSON" | jq -S '.')
    EXISTING_SERVICE_NORMALIZED=$(echo "$EXISTING_SERVICE_JSON" | jq -S '.')
    if [ "$NEW_SERVICE_NORMALIZED" == "$EXISTING_SERVICE_NORMALIZED" ]; then
      continue  # Skip unchanged services
    fi
  fi
  
  # Remove existing service with same name, then add new one
  jq --argjson service "$SERVICE_JSON" \
    'del(.services[] | select(.name == "'"$SERVICE_NAME"'")) | .services += [$service]' \
    "$MERGED_JSON_TMP" > "$MERGED_JSON"
  
  # Use merged as input for next iteration
  cp "$MERGED_JSON" "$MERGED_JSON_TMP"
done

# Final result is in MERGED_JSON

# Convert back to YAML
yq eval -P "$MERGED_JSON" > "$OUTPUT_FILE"

# Cleanup temporary files
rm -f "$CURRENT_SPEC_FILE" "$CURRENT_JSON" "$NEW_JSON" "$MERGED_JSON" "$MERGED_JSON_TMP"

echo "✓ Merged spec created: $OUTPUT_FILE"
echo ""
echo ">> Merged spec preview (first 50 lines):"
head -n 50 "$OUTPUT_FILE"
echo ""

# Validate merged spec
echo ">> Validating merged spec..."
if doctl apps spec validate "$OUTPUT_FILE" 2>&1; then
  echo "✓ Spec validation passed"
else
  echo "⚠️  Spec validation failed or not available, but spec file created"
fi

