#!/usr/bin/env bash
set -euo pipefail

# Query PostgreSQL database via Docker container
# Usage: ./query-db.sh [SQL query]
#   If no query provided, opens interactive psql session
#
# Examples:
#   ./query-db.sh "SELECT * FROM muscle_groups;"
#   ./query-db.sh "SELECT COUNT(*) FROM workout_routines;"
#   ./query-db.sh  # Opens interactive session

DB_CONTAINER="read-service-xq-fitness-db-1"
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

