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

echo ">> Merging service spec" >&2
echo "   New spec file: $NEW_SPEC_FILE" >&2
echo "   App name: $APP_NAME" >&2
echo "   Output file: $OUTPUT_FILE" >&2
echo "" >&2

# Get existing app ID if it exists
APP_ID=$(doctl apps list --output json 2>/dev/null | jq -r '.[] | select(.spec.name=="'"$APP_NAME"'") | .id' | head -n1 || echo "")

if [ -z "$APP_ID" ]; then
  echo ">> App '$APP_NAME' does not exist, using new spec as-is" >&2
  cp "$NEW_SPEC_FILE" "$OUTPUT_FILE"
  echo "✓ Created merged spec: $OUTPUT_FILE" >&2
  exit 0
fi

echo ">> App '$APP_NAME' exists (ID: $APP_ID), fetching current spec..." >&2

# Fetch current app spec
CURRENT_SPEC=$(doctl apps spec get "$APP_ID" --output yaml 2>/dev/null || echo "")

if [ -z "$CURRENT_SPEC" ]; then
  echo "⚠️  Warning: Could not fetch current spec, using new spec as-is (may remove other services)" >&2
  cp "$NEW_SPEC_FILE" "$OUTPUT_FILE"
  echo "✓ Created merged spec: $OUTPUT_FILE" >&2
  exit 0
fi

echo ">> Merging services from new spec into existing app spec..." >&2

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

# Check for routing conflicts: ingress.rules and component routes are mutually exclusive
HAS_INGRESS=$(jq -e '.ingress.rules != null and (.ingress.rules | length > 0)' "$CURRENT_JSON" 2>/dev/null && echo "true" || echo "false")
HAS_COMPONENT_ROUTES_NEW=$(jq -e '[.services[]?.routes[]?] | length > 0' "$NEW_JSON" 2>/dev/null && echo "true" || echo "false")
HAS_COMPONENT_ROUTES_EXISTING=$(jq -e '[.services[]?.routes[]?] | length > 0' "$CURRENT_JSON" 2>/dev/null && echo "true" || echo "false")

# If new spec uses component routes, remove ingress from existing spec
if [ "$HAS_INGRESS" = "true" ] && [ "$HAS_COMPONENT_ROUTES_NEW" = "true" ]; then
  echo ">> Warning: Existing app uses ingress.rules, but new spec uses component routes" >&2
  echo ">> Removing ingress.rules from existing spec to use component-level routes" >&2
  # Remove ingress from current spec
  jq 'del(.ingress)' "$CURRENT_JSON" > "$CURRENT_JSON.tmp"
  mv "$CURRENT_JSON.tmp" "$CURRENT_JSON"
fi

# If existing spec uses component routes but new spec has ingress, remove ingress from new spec
if [ "$HAS_COMPONENT_ROUTES_EXISTING" = "true" ] && [ "$(jq -e '.ingress.rules != null and (.ingress.rules | length > 0)' "$NEW_JSON" 2>/dev/null && echo "true" || echo "false")" = "true" ]; then
  echo ">> Warning: Existing app uses component routes, but new spec uses ingress.rules" >&2
  echo ">> Removing ingress.rules from new spec to use component-level routes" >&2
  # Remove ingress from new spec
  jq 'del(.ingress)' "$NEW_JSON" > "$NEW_JSON.tmp"
  mv "$NEW_JSON.tmp" "$NEW_JSON"
fi

# Get all service names from new spec
NEW_SERVICE_NAMES=$(jq -r '.services[].name' "$NEW_JSON")

if [ -z "$NEW_SERVICE_NAMES" ]; then
  echo "Error: No services found in new spec file" >&2
  rm -f "$CURRENT_SPEC_FILE" "$CURRENT_JSON" "$NEW_JSON" "$MERGED_JSON" "$MERGED_JSON_TMP"
  exit 1
fi

echo ">> Services to merge: $(echo "$NEW_SERVICE_NAMES" | tr '\n' ' ')" >&2
echo "" >&2

# Check if services actually changed to avoid unnecessary redeployments
# (DigitalOcean redeploys ALL services when app spec is updated)
SERVICES_CHANGED=false

for SERVICE_NAME in $NEW_SERVICE_NAMES; do
  echo "  - Checking service: $SERVICE_NAME" >&2
  
  # Extract the service from new spec
  NEW_SERVICE_JSON=$(jq '.services[] | select(.name == "'"$SERVICE_NAME"'")' "$NEW_JSON")
  
  if [ -z "$NEW_SERVICE_JSON" ] || [ "$NEW_SERVICE_JSON" == "null" ]; then
    echo "    ⚠️  Warning: Service '$SERVICE_NAME' not found in new spec, skipping" >&2
    continue
  fi
  
  # Check if service exists in current spec
  EXISTING_SERVICE_JSON=$(jq '.services[] | select(.name == "'"$SERVICE_NAME"'")' "$CURRENT_JSON")
  
  if [ -z "$EXISTING_SERVICE_JSON" ] || [ "$EXISTING_SERVICE_JSON" == "null" ]; then
    echo "    → Service is new, will be added" >&2
    SERVICES_CHANGED=true
  else
    # Compare service configs (normalize JSON for comparison)
    NEW_SERVICE_NORMALIZED=$(echo "$NEW_SERVICE_JSON" | jq -S '.')
    EXISTING_SERVICE_NORMALIZED=$(echo "$EXISTING_SERVICE_JSON" | jq -S '.')
    
    if [ "$NEW_SERVICE_NORMALIZED" != "$EXISTING_SERVICE_NORMALIZED" ]; then
      echo "    → Service configuration changed, will be updated" >&2
      SERVICES_CHANGED=true
    else
      echo "    ✓ Service configuration unchanged, skipping update" >&2
      echo "      (This prevents unnecessary redeployment of other services)" >&2
    fi
  fi
done

echo "" >&2

# If no services changed, use current spec to avoid redeployment
if [ "$SERVICES_CHANGED" = false ]; then
  echo ">> No service changes detected. Using current spec to avoid redeployment." >&2
  cp "$CURRENT_SPEC_FILE" "$OUTPUT_FILE"
  rm -f "$CURRENT_SPEC_FILE" "$CURRENT_JSON" "$NEW_JSON" "$MERGED_JSON" "$MERGED_JSON_TMP"
  echo "✓ No changes needed, using existing spec: $OUTPUT_FILE" >&2
  exit 0
fi

# Merge services that changed
echo ">> Merging changed services into app spec..." >&2
# Start with current JSON (which should already have ingress removed if needed)
# But ensure ingress is removed before we start merging
if [ "$HAS_COMPONENT_ROUTES_NEW" = "true" ]; then
  echo ">> Ensuring ingress is removed from current spec before merging..." >&2
  jq 'del(.ingress)' "$CURRENT_JSON" > "$CURRENT_JSON.tmp"
  mv "$CURRENT_JSON.tmp" "$CURRENT_JSON"
fi
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

# Final check: ALWAYS remove ingress.rules if ANY component routes exist (they are mutually exclusive)
# This is critical - DigitalOcean rejects specs with both ingress.rules and component routes
HAS_INGRESS_FINAL=$(jq -e '.ingress != null' "$MERGED_JSON" 2>/dev/null && echo "true" || echo "false")
HAS_COMPONENT_ROUTES_FINAL=$(jq -e '[.services[]?.routes[]?] | length > 0' "$MERGED_JSON" 2>/dev/null && echo "true" || echo "false")

echo ">> Final check: ingress=$HAS_INGRESS_FINAL, component_routes=$HAS_COMPONENT_ROUTES_FINAL" >&2

if [ "$HAS_COMPONENT_ROUTES_FINAL" = "true" ]; then
  if [ "$HAS_INGRESS_FINAL" = "true" ]; then
    echo ">> CRITICAL: Removing ingress.rules from merged spec (component routes are present)" >&2
    jq 'del(.ingress)' "$MERGED_JSON" > "$MERGED_JSON.tmp"
    mv "$MERGED_JSON.tmp" "$MERGED_JSON"
    echo ">> Ingress removed successfully" >&2
  else
    echo ">> No ingress found, component routes are safe" >&2
  fi
else
  echo ">> No component routes found, keeping ingress if present" >&2
fi

# Double-check after removal
HAS_INGRESS_AFTER=$(jq -e '.ingress != null' "$MERGED_JSON" 2>/dev/null && echo "true" || echo "false")
if [ "$HAS_COMPONENT_ROUTES_FINAL" = "true" ] && [ "$HAS_INGRESS_AFTER" = "true" ]; then
  echo ">> ERROR: Ingress still present after removal attempt!" >&2
  echo ">> Attempting force removal..." >&2
  jq 'del(.ingress)' "$MERGED_JSON" > "$MERGED_JSON.tmp" 2>&1
  if [ $? -eq 0 ]; then
    mv "$MERGED_JSON.tmp" "$MERGED_JSON"
    echo ">> Force removal successful" >&2
  else
    echo ">> Force removal failed, showing merged JSON structure:" >&2
    jq '.' "$MERGED_JSON" | head -50 >&2
    exit 1
  fi
fi

# Final aggressive removal: Convert to JSON, remove ingress, convert back
# This ensures ingress is definitely gone before creating YAML
if [ "$HAS_COMPONENT_ROUTES_FINAL" = "true" ]; then
  echo ">> Final aggressive ingress removal before YAML conversion..." >&2
  jq 'del(.ingress)' "$MERGED_JSON" > "$MERGED_JSON.tmp"
  mv "$MERGED_JSON.tmp" "$MERGED_JSON"
  
  # Verify removal
  INGRESS_CHECK=$(jq -e '.ingress // empty' "$MERGED_JSON" 2>/dev/null)
  if [ -n "$INGRESS_CHECK" ] && [ "$INGRESS_CHECK" != "null" ] && [ "$INGRESS_CHECK" != "{}" ] && [ "$INGRESS_CHECK" != "empty" ]; then
    echo ">> ERROR: Ingress still exists after removal: $INGRESS_CHECK" >&2
    echo ">> Attempting alternative removal method..." >&2
    jq 'with_entries(select(.key != "ingress"))' "$MERGED_JSON" > "$MERGED_JSON.tmp"
    mv "$MERGED_JSON.tmp" "$MERGED_JSON"
  fi
  echo ">> Ingress removal verified" >&2
fi

# Convert back to YAML
yq eval -P "$MERGED_JSON" > "$OUTPUT_FILE"

# Final YAML-level check and removal (belt and suspenders approach)
if [ "$HAS_COMPONENT_ROUTES_FINAL" = "true" ]; then
  if grep -q "^ingress:" "$OUTPUT_FILE"; then
    echo ">> WARNING: Ingress found in final YAML, removing with sed..." >&2
    # Remove ingress block (handles multi-line YAML)
    awk '/^ingress:/{flag=1} /^[a-zA-Z]/{if(flag) flag=0} !flag' "$OUTPUT_FILE" > "$OUTPUT_FILE.tmp"
    mv "$OUTPUT_FILE.tmp" "$OUTPUT_FILE"
    echo ">> Ingress removed from YAML file" >&2
  fi
fi

# Remove registry_credentials field if present (not a valid field in DO App Platform spec)
# GHCR authentication is handled through DigitalOcean's registry configuration, not in app spec
sed -i '/registry_credentials:/d' "$OUTPUT_FILE"

# Cleanup temporary files
rm -f "$CURRENT_SPEC_FILE" "$CURRENT_JSON" "$NEW_JSON" "$MERGED_JSON" "$MERGED_JSON_TMP"

echo "✓ Merged spec created: $OUTPUT_FILE" >&2
echo "" >&2
echo ">> Merged spec preview (sanitized - sensitive values hidden):" >&2
# Show spec but mask sensitive values
sed 's/value:.*DB_PASSWORD.*/value: ***HIDDEN***/g' "$OUTPUT_FILE" | \
  sed 's/registry_credentials:.*/registry_credentials: ***HIDDEN***/g' | \
  sed 's/value:.*PASSWORD.*/value: ***HIDDEN***/g' | \
  head -n 50 >&2
echo "" >&2

# Validate merged spec
echo ">> Validating merged spec..." >&2
if doctl apps spec validate "$OUTPUT_FILE" 2>&1; then
  echo "✓ Spec validation passed" >&2
else
  echo "⚠️  Spec validation failed or not available, but spec file created" >&2
fi

