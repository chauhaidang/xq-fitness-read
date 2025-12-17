#!/usr/bin/env bash
set -euo pipefail

# Query PostgreSQL database via Docker container
# Usage: ./query-db.sh [DB_CONTAINER] [SQL query]
#   DB_CONTAINER: Name of the database container (optional, can also be set via DB_CONTAINER env var)
#                 Defaults to: write-service-xq-fitness-db-1
#   SQL query: SQL query to execute (optional, if not provided opens interactive psql session)
#
# Examples:
#   ./query-db.sh "SELECT * FROM muscle_groups;"
#   ./query-db.sh "SELECT COUNT(*) FROM workout_routines;"
#   ./query-db.sh  # Opens interactive session
#   DB_CONTAINER=my-db-container ./query-db.sh "SELECT * FROM muscle_groups;"
#   ./query-db.sh my-db-container "SELECT * FROM muscle_groups;"
#   ./query-db.sh my-db-container  # Opens interactive session with custom container

# Determine DB_CONTAINER: check env var first, then first arg if it's a container name, else use default
if [ -n "${DB_CONTAINER:-}" ]; then
  # DB_CONTAINER already set via environment variable
  :
elif [ $# -gt 0 ] && docker ps --format "{{.Names}}" 2>/dev/null | grep -q "^${1}$"; then
  # First argument is a running container name
  DB_CONTAINER="$1"
  shift  # Remove container name from arguments
else
  # Use default container name
  DB_CONTAINER="${DB_CONTAINER:-write-service-xq-fitness-db-1}"
fi

# Use explicit defaults (not environment variables) for local Docker database
DB_USER="${QUERY_DB_USER:-xq_user}"
DB_NAME="${QUERY_DB_NAME:-xq_fitness}"

# Check if container is running
if ! docker ps --format "{{.Names}}" | grep -q "^${DB_CONTAINER}$"; then
  echo "Error: Database container '$DB_CONTAINER' is not running" >&2
  echo "Start it with: xq-infra up" >&2
  exit 1
fi

if [ $# -eq 0 ]; then
  # Interactive mode
  echo "Connecting to PostgreSQL database '$DB_NAME' as user '$DB_USER'..."
  echo "Type '\\q' to exit"
  echo ""
  docker exec -it "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME"
else
  # Execute query
  QUERY="$*"
  docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "$QUERY"
fi

