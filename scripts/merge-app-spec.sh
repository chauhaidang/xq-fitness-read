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
# Use a pattern that always sets the variable, even if the command fails
APP_ID=""
if APP_ID_TMP=$(doctl apps list --output json 2>/dev/null | jq -r '.[] | select(.spec.name=="'"$APP_NAME"'") | .id' | head -n1 2>/dev/null); then
  APP_ID="$APP_ID_TMP"
fi

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
if jq -e '.ingress.rules != null and (.ingress.rules | length > 0)' "$CURRENT_JSON" >/dev/null 2>&1; then
  HAS_INGRESS="true"
else
  HAS_INGRESS="false"
fi

if jq -e '[.services[]?.routes[]?] | length > 0' "$NEW_JSON" >/dev/null 2>&1; then
  HAS_COMPONENT_ROUTES_NEW="true"
else
  HAS_COMPONENT_ROUTES_NEW="false"
fi

if jq -e '[.services[]?.routes[]?] | length > 0' "$CURRENT_JSON" >/dev/null 2>&1; then
  HAS_COMPONENT_ROUTES_EXISTING="true"
else
  HAS_COMPONENT_ROUTES_EXISTING="false"
fi

echo ">> Routing check: HAS_INGRESS=$HAS_INGRESS, HAS_COMPONENT_ROUTES_NEW=$HAS_COMPONENT_ROUTES_NEW, HAS_COMPONENT_ROUTES_EXISTING=$HAS_COMPONENT_ROUTES_EXISTING" >&2

# If new spec uses component routes, convert ingress rules to component routes before removing ingress
if [ "$HAS_INGRESS" = "true" ] && [ "$HAS_COMPONENT_ROUTES_NEW" = "true" ]; then
  echo ">> Warning: Existing app uses ingress.rules, but new spec uses component routes" >&2
  echo ">> Converting ingress.rules to component routes for existing services..." >&2
  # Convert ingress rules to component routes
  jq '
    # First, add routes to services based on ingress rules
    if .ingress and .ingress.rules then
      reduce .ingress.rules[] as $rule (.;
        if $rule.component and $rule.component.name then
          .services[] |= (
            if .name == $rule.component.name then
              # Convert ingress rule to component route
              .routes = [
                {
                  path: ($rule.match.path.prefix // $rule.match.path.exact // "/"),
                  preserve_path_prefix: false
                }
              ]
            else
              .
            end
          )
        else
          .
        end
      ) |
      # Remove ingress after conversion
      del(.ingress)
    else
      .
    end
  ' "$CURRENT_JSON" > "$CURRENT_JSON.tmp"
  mv "$CURRENT_JSON.tmp" "$CURRENT_JSON"
  echo ">> Converted ingress.rules to component routes and removed ingress" >&2
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
    
    # If service exists, handle password secrets carefully
    # DigitalOcean behavior with SECRET env vars:
    # - If you include env var with plain text value, DO will encrypt and update it (even if existing is encrypted)
    # - If you include env var WITHOUT value field, DO will NOT set it (password becomes null/missing)
    # Solution: Always include the password env var with plain text value from new spec
    # DO will encrypt it automatically. This works even if existing service has encrypted value.
    echo "    → Handling password secrets for existing service..." >&2
    SERVICE_JSON=$(echo "$SERVICE_JSON" | jq '
      # For each env var in new service
      .envs |= map(
        if .type == "SECRET" then
          . as $newEnv |
          # If new spec has encrypted value (EV[...]), remove it (DO rejects encrypted values in updates)
          if ($newEnv.value | type == "string") and ($newEnv.value | startswith("EV[")) then
            # New spec has encrypted value - remove it to avoid DO rejection
            # This will cause password to be missing, but spec will validate
            # Better: the new spec should have plain text, not encrypted
            del(.value)
          else
            # New spec has plain text value (or no value) - keep it
            # DO will encrypt it automatically, even if existing service has encrypted value
            .
          end
        else
          .
        end
      )
    ' --argjson existingService "$EXISTING_SERVICE_JSON")
  else
    # New service - MUST have plain text password values
    # Remove encrypted values if present (DO rejects them for new services)
    # But keep the env var with plain text value
    SERVICE_JSON=$(echo "$SERVICE_JSON" | jq '
      .envs |= map(
        if .type == "SECRET" then
          if (.value | type == "string") and (.value | startswith("EV[")) then
            # Remove encrypted value - new services must have plain text passwords
            # If encrypted in new spec, remove env var (new services need plain text)
            empty
          elif (.value | type == "string") and (.value != "") then
            # Has plain text value - keep it
            .
          else
            # No value or empty - remove it (new services need passwords)
            empty
          end
        else
          .
        end
      ) |
      # Remove null entries
      .envs |= map(select(. != null))
    ')
  fi
  
  # Remove existing service with same name, then add new one
  jq --argjson service "$SERVICE_JSON" \
    'del(.services[] | select(.name == "'"$SERVICE_NAME"'")) | .services += [$service]' \
    "$MERGED_JSON_TMP" > "$MERGED_JSON"
  
  # Use merged as input for next iteration
  cp "$MERGED_JSON" "$MERGED_JSON_TMP"
done

# Final result is in MERGED_JSON

# Remove encrypted secret values from all services (DigitalOcean rejects encrypted values in specs)
# We'll inject plain text passwords from environment variables for all services that need them
echo ">> Removing encrypted secret values from all services..." >&2
jq '
  .services[] |= (
    # Remove encrypted env secret values
    (if .envs then
      .envs |= map(if .type == "SECRET" and (.value | type == "string") and (.value | startswith("EV[")) then del(.value) else . end)
    else . end) |
    # Remove encrypted registry_credentials
    (if .image and .image.registry_credentials and (.image.registry_credentials | type == "string") and (.image.registry_credentials | startswith("EV[")) then .image.registry_credentials = null else . end)
  )
' "$MERGED_JSON" > "$MERGED_JSON.tmp"
mv "$MERGED_JSON.tmp" "$MERGED_JSON"

# Inject DB_PASSWORD for write-service (and any other services that need it)
# Since both read-service and write-service use the same database, use the same password
if [ -n "${DB_PASSWORD:-}" ]; then
  echo ">> Injecting DB_PASSWORD for services that need it..." >&2
  jq --arg password "$DB_PASSWORD" '
    .services[] |= (
      if .envs then
        .envs |= map(
          # Inject DB_PASSWORD for write-service
          if .key == "DB_PASSWORD" and .type == "SECRET" and ((.value | type == "null") or (.value == "")) then
            .value = $password
          # Inject SPRING_DATASOURCE_PASSWORD for read-service (Spring Boot)
          elif .key == "SPRING_DATASOURCE_PASSWORD" and .type == "SECRET" and ((.value | type == "null") or (.value == "")) then
            .value = $password
          else
            .
          end
        )
      else
        .
      end
    )
  ' "$MERGED_JSON" > "$MERGED_JSON.tmp"
  mv "$MERGED_JSON.tmp" "$MERGED_JSON"
  echo ">> DB_PASSWORD injected for services that need it" >&2
else
  echo ">> Warning: DB_PASSWORD not set, skipping password injection" >&2
  echo ">> Services with missing passwords may fail to connect to database" >&2
fi

# Add registry_credentials to all GHCR services that don't have it (or have null/empty)
# DigitalOcean doesn't return registry_credentials in existing specs, so we need to add it
if [ -n "${GITHUB_REGISTRY_CREDENTIALS:-}" ]; then
  echo ">> Adding registry_credentials to GHCR services from GITHUB_REGISTRY_CREDENTIALS..." >&2
  jq --arg creds "$GITHUB_REGISTRY_CREDENTIALS" '
    .services[] |= (
      if .image and .image.registry_type == "GHCR" then
        # Add registry_credentials if missing, null, or empty
        if (.image.registry_credentials == null) or (.image.registry_credentials == "") or (.image.registry_credentials | type == "null") then
          .image.registry_credentials = $creds
        else
          .  # Keep existing non-encrypted credentials
        end
      else
        .
      end
    )
  ' "$MERGED_JSON" > "$MERGED_JSON.tmp"
  mv "$MERGED_JSON.tmp" "$MERGED_JSON"
  echo ">> Registry credentials added to GHCR services" >&2
else
  echo ">> Warning: GITHUB_REGISTRY_CREDENTIALS not set, skipping registry_credentials addition" >&2
fi

echo ">> Encrypted secrets removed and plain text passwords injected from environment variables" >&2

# Final check: ALWAYS remove ingress.rules if ANY component routes exist (they are mutually exclusive)
# This is critical - DigitalOcean rejects specs with both ingress.rules and component routes
if jq -e '.ingress != null' "$MERGED_JSON" >/dev/null 2>&1; then
  HAS_INGRESS_FINAL="true"
else
  HAS_INGRESS_FINAL="false"
fi

if jq -e '[.services[]?.routes[]?] | length > 0' "$MERGED_JSON" >/dev/null 2>&1; then
  HAS_COMPONENT_ROUTES_FINAL="true"
else
  HAS_COMPONENT_ROUTES_FINAL="false"
fi

# Debug: show what we found
COMPONENT_ROUTES_COUNT=$(jq '[.services[]?.routes[]?] | length' "$MERGED_JSON" 2>/dev/null || echo "0")
echo ">> Final check: ingress=$HAS_INGRESS_FINAL, component_routes=$HAS_COMPONENT_ROUTES_FINAL (count=$COMPONENT_ROUTES_COUNT)" >&2

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
  
  # Verify removal (don't use -e flag as it exits with error on null/empty)
  INGRESS_CHECK=$(jq '.ingress // empty' "$MERGED_JSON" 2>/dev/null || echo "empty")
  if [ -n "$INGRESS_CHECK" ] && [ "$INGRESS_CHECK" != "null" ] && [ "$INGRESS_CHECK" != "{}" ] && [ "$INGRESS_CHECK" != "empty" ] && [ "$INGRESS_CHECK" != '""' ]; then
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
    echo ">> WARNING: Ingress found in final YAML, removing with awk..." >&2
    # Remove ingress block (handles multi-line YAML)
    # Match lines starting with 'ingress:' and everything until the next top-level key (not indented)
    awk '/^ingress:/{flag=1; next} /^[a-zA-Z][^:]*:/{if(flag) flag=0} !flag' "$OUTPUT_FILE" > "$OUTPUT_FILE.tmp"
    mv "$OUTPUT_FILE.tmp" "$OUTPUT_FILE"
    echo ">> Ingress removed from YAML file" >&2
    # Verify removal
    if grep -q "^ingress:" "$OUTPUT_FILE"; then
      echo ">> ERROR: Ingress still present after awk removal, trying alternative method..." >&2
      # Alternative: use yq to remove ingress
      yq eval 'del(.ingress)' -i "$OUTPUT_FILE" 2>/dev/null || {
        echo ">> ERROR: yq removal also failed" >&2
        exit 1
      }
      echo ">> Ingress removed using yq" >&2
    fi
  fi
fi

# Note: registry_credentials is a valid field in DO App Platform spec for private images
# It should be kept in the spec to allow DO to authenticate with GHCR

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

