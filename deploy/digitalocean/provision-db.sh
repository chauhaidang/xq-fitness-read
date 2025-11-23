#!/usr/bin/env bash
set -euo pipefail

# Shared database provisioning script
# Can be called from either read-service or write-service repos

if ! command -v doctl >/dev/null; then
  echo "doctl CLI is required (https://docs.digitalocean.com/reference/doctl/)." >&2
  exit 1
fi

if ! command -v jq >/dev/null; then
  echo "jq is required for parsing DigitalOcean JSON responses." >&2
  exit 1
fi

: "${DO_TOKEN:?Set DO_TOKEN with a DigitalOcean API token that has write access.}"

REGION="${REGION:-sgp1}"
APP_NAME="${APP_NAME:-xq-fitness}"
DB_CLUSTER_NAME="${DB_CLUSTER_NAME:-${APP_NAME}-db}"
DB_SIZE="${DB_SIZE:-db-s-1vcpu-1gb}"
DB_NUM_NODES="${DB_NUM_NODES:-1}"
DB_ENGINE_VERSION="${DB_ENGINE_VERSION:-15}"
DB_STORAGE_SIZE_MIB="${DB_STORAGE_SIZE_MIB:-10240}"  # 10 GB in MiB (10 * 1024) - minimum for db-s-1vcpu-1gb
APP_DB_NAME="${APP_DB_NAME:-xq_fitness}"
APP_DB_USER="${APP_DB_USER:-xq_app_user}"
APP_DB_PASSWORD="${APP_DB_PASSWORD:-}"

# Function to wait for database cluster to be ready
wait_for_cluster_ready() {
  local cluster_id=$1
  local max_attempts=60
  local attempt=0
  local status=""
  
  echo "   Waiting for cluster to be ready..."
  while [[ $attempt -lt $max_attempts ]]; do
    status=$(doctl databases get "$cluster_id" --output json 2>/dev/null | jq -r '.[0].status // .status // "unknown"' || echo "unknown")
    
    if [[ "$status" == "online" ]] || [[ "$status" == "active" ]]; then
      echo "   Cluster is ready (status: $status)"
      return 0
    fi
    
    if [[ "$status" == "unknown" ]]; then
      # Cluster might still be initializing, wait a bit
      sleep 2
    else
      echo "   Cluster status: $status (attempt $((attempt + 1))/$max_attempts)"
      sleep 5
    fi
    
    attempt=$((attempt + 1))
  done
  
  echo "   ⚠️  Warning: Cluster may still be provisioning. Continuing anyway..." >&2
  return 0
}

echo ">> Authenticating doctl context"
doctl auth init -t "$DO_TOKEN" >/dev/null

echo ">> Ensuring managed Postgres cluster ($DB_CLUSTER_NAME) exists"
DB_CLUSTER_ID=$(doctl databases list --output json | jq -r '.[] | select(.name=="'"$DB_CLUSTER_NAME"'") | .id' || echo "")

if [[ -z "$DB_CLUSTER_ID" ]]; then
  CREATE_OUTPUT=$(doctl databases create "$DB_CLUSTER_NAME" \
    --engine pg \
    --region "$REGION" \
    --size "$DB_SIZE" \
    --num-nodes "$DB_NUM_NODES" \
    --version "$DB_ENGINE_VERSION" \
    --storage-size-mib "$DB_STORAGE_SIZE_MIB" \
    --output json)
  DB_CLUSTER_ID=$(echo "$CREATE_OUTPUT" | jq -r '.[0].id')
  echo "   Created DB cluster: $DB_CLUSTER_ID"
  wait_for_cluster_ready "$DB_CLUSTER_ID"
else
  echo "   Reusing DB cluster: $DB_CLUSTER_ID"
  # Check if existing cluster is ready
  wait_for_cluster_ready "$DB_CLUSTER_ID"
fi

echo ">> Ensuring logical database ($APP_DB_NAME)"
if ! doctl databases db list "$DB_CLUSTER_ID" --output json | jq -e '.[] | select(.name=="'"$APP_DB_NAME"'")' >/dev/null 2>&1; then
  # Retry logic for database creation (in case cluster is still finalizing)
  max_retries=5
  retry=0
  while [[ $retry -lt $max_retries ]]; do
    if doctl databases db create "$DB_CLUSTER_ID" "$APP_DB_NAME" >/dev/null 2>&1; then
      echo "   Created database $APP_DB_NAME"
      break
    else
      retry=$((retry + 1))
      if [[ $retry -lt $max_retries ]]; then
        echo "   Retrying database creation (attempt $retry/$max_retries)..."
        sleep 3
      else
        echo "   ⚠️  Failed to create database after $max_retries attempts. It may already exist or cluster is still provisioning." >&2
        exit 1
      fi
    fi
  done
else
  echo "   Database $APP_DB_NAME already exists"
fi

echo ">> Ensuring app database user ($APP_DB_USER)"
if ! doctl databases user list "$DB_CLUSTER_ID" --output json | jq -e '.[] | select(.name=="'"$APP_DB_USER"'")' >/dev/null 2>&1; then
  # Retry logic for user creation (in case cluster is still finalizing)
  max_retries=5
  retry=0
  while [[ $retry -lt $max_retries ]]; do
    USER_JSON=$(doctl databases user create "$DB_CLUSTER_ID" "$APP_DB_USER" --output json 2>&1)
    if [[ $? -eq 0 ]]; then
      APP_DB_PASSWORD=$(echo "$USER_JSON" | jq -r '.[0].password')
      echo "   Created user $APP_DB_USER and captured password"
      echo "   ⚠️  IMPORTANT: Save this password securely!"
      echo "   Password: $APP_DB_PASSWORD"
      break
    else
      retry=$((retry + 1))
      if [[ $retry -lt $max_retries ]]; then
        echo "   Retrying user creation (attempt $retry/$max_retries)..."
        sleep 3
      else
        echo "   ⚠️  Failed to create user after $max_retries attempts. Error: $USER_JSON" >&2
        exit 1
      fi
    fi
  done
else
  if [[ -z "$APP_DB_PASSWORD" ]]; then
    echo "User $APP_DB_USER already exists. Set APP_DB_PASSWORD with the stored credential." >&2
    exit 1
  fi
  echo "   Reusing user $APP_DB_USER"
fi

echo ">> Fetching connection details"
CONNECTION_JSON=$(doctl databases connection "$DB_CLUSTER_ID" --output json)
# Connection JSON can be either an object or an array - handle both cases
if echo "$CONNECTION_JSON" | jq -e 'type == "array"' >/dev/null 2>&1; then
  DB_HOST=$(echo "$CONNECTION_JSON" | jq -r '.[0].host // .[0].uri // empty')
  DB_PORT=$(echo "$CONNECTION_JSON" | jq -r '.[0].port // empty')
else
  DB_HOST=$(echo "$CONNECTION_JSON" | jq -r '.host // .uri // empty')
  DB_PORT=$(echo "$CONNECTION_JSON" | jq -r '.port // empty')
fi

# If host is a URI, extract hostname from it
if [[ "$DB_HOST" == *"@"* ]]; then
  DB_HOST=$(echo "$DB_HOST" | sed -E 's/.*@([^:]+).*/\1/')
fi

if [[ -z "$DB_HOST" ]] || [[ -z "$DB_PORT" ]]; then
  echo "   ⚠️  Warning: Could not parse connection details. Trying alternative method..." >&2
  # Fallback: try to get connection string and parse it
  CONNECTION_STRING=$(echo "$CONNECTION_JSON" | jq -r '.uri // .connection_string // .[0].uri // .[0].connection_string // empty')
  if [[ -n "$CONNECTION_STRING" ]]; then
    DB_HOST=$(echo "$CONNECTION_STRING" | sed -E 's/.*@([^:]+):.*/\1/')
    DB_PORT=$(echo "$CONNECTION_STRING" | sed -E 's/.*:([0-9]+)\/.*/\1/')
  fi
fi

if [[ -z "$DB_HOST" ]] || [[ -z "$DB_PORT" ]]; then
  echo "   Error: Failed to extract database connection details from:" >&2
  echo "$CONNECTION_JSON" | jq '.' >&2
  exit 1
fi

# Export for use by calling scripts
export DB_CLUSTER_ID DB_HOST DB_PORT
export DB_USER="$APP_DB_USER"
export DB_PASSWORD="$APP_DB_PASSWORD"
export DB_NAME="$APP_DB_NAME"

cat <<EOF

Database connection details:
  Host: $DB_HOST
  Port: $DB_PORT
  Database: $DB_NAME
  User: $DB_USER
  Password: ${DB_PASSWORD:0:4}... (hidden)

Export these variables for deployment:
  export DB_HOST="$DB_HOST"
  export DB_PORT="$DB_PORT"
  export DB_USER="$DB_USER"
  export DB_PASSWORD="$DB_PASSWORD"
  export DB_NAME="$DB_NAME"
EOF

