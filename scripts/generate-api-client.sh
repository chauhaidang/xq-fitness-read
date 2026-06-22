#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check for service name parameter
if [ -z "$1" ]; then
  echo -e "${RED}❌ Service name is required${NC}"
  echo -e "${YELLOW}Usage: $0 <service-name>${NC}"
  echo -e "${YELLOW}Example: $0 read-service${NC}"
  echo ""
  echo -e "${BLUE}Available services:${NC}"
  echo -e "  - read-service (api/read-service-api.yaml)"
  exit 1
fi

SERVICE_NAME="$1"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC_FILE="$PROJECT_ROOT/api/${SERVICE_NAME}-api.yaml"
OUTPUT_DIR="$PROJECT_ROOT/generated-clients/${SERVICE_NAME}"
SERVICE_PREFIX="${SERVICE_NAME%-service}"
# PROJECT_NAME is replaced when instantiating template (default: xq-fitness)
PACKAGE_NAME="xq-fitness-${SERVICE_PREFIX}-client"

echo -e "${BLUE}🔧 Generating TypeScript API Client for ${YELLOW}${SERVICE_NAME}${BLUE}...${NC}"

if [ ! -f "$SPEC_FILE" ]; then
  echo -e "${RED}❌ OpenAPI spec not found: $SPEC_FILE${NC}"
  exit 1
fi

# Client generation is a bootstrap step, so it must work before dependencies are installed.
if [ -n "${npm_execpath:-}" ]; then
  YARN_COMMAND=("$npm_execpath")
elif command -v "${COREPACK_BIN:-corepack}" &> /dev/null; then
  YARN_COMMAND=("${COREPACK_BIN:-corepack}" yarn@4.17.0)
elif command -v yarn &> /dev/null && [[ "$(yarn --version)" == "4.17.0" ]]; then
  YARN_COMMAND=(yarn)
else
  echo -e "${RED}❌ Yarn is not available${NC}"
  echo -e "${BLUE}   Enable the package-manager version pinned by this repository:${NC}"
  echo -e "${GREEN}   corepack enable${NC}"
  exit 1
fi

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

BOOTSTRAP_DIR="$(mktemp -d)"
trap 'rm -rf "$BOOTSTRAP_DIR"' EXIT
cp "$PROJECT_ROOT/openapitools.json" "$BOOTSTRAP_DIR/openapitools.json"

(cd "$BOOTSTRAP_DIR" \
  && YARN_NODE_LINKER=node-modules YARN_ENABLE_SCRIPTS=true \
  "${YARN_COMMAND[@]}" dlx @openapitools/openapi-generator-cli@2.25.2 generate \
  -i "$SPEC_FILE" \
  -g typescript-axios \
  -o "$OUTPUT_DIR" \
  --additional-properties=supportsES6=true,npmName=${PACKAGE_NAME},npmVersion=1.0.0)

echo -e "${GREEN}✅ API Client generation complete!${NC}"
echo -e "${GREEN}   Package: ${PACKAGE_NAME}${NC}"
echo -e "${GREEN}   Generated at: $OUTPUT_DIR${NC}"
echo ""
echo -e "${BLUE}Next, install the repository dependencies:${NC}"
echo -e "${YELLOW}  yarn install --immutable${NC}"
