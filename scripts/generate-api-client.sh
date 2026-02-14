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

if ! command -v openapi-generator-cli &> /dev/null; then
  echo -e "${RED}❌ openapi-generator-cli not found${NC}"
  echo -e "${GREEN}   npm install -g @openapitools/openapi-generator-cli${NC}"
  exit 1
fi

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

openapi-generator-cli generate \
  -i "$SPEC_FILE" \
  -g typescript-axios \
  -o "$OUTPUT_DIR" \
  --additional-properties=supportsES6=true,npmName=${PACKAGE_NAME},npmVersion=1.0.0

echo -e "${GREEN}✅ API Client generation complete!${NC}"
echo -e "${GREEN}   Package: ${PACKAGE_NAME}${NC}"
echo -e "${GREEN}   Generated at: $OUTPUT_DIR${NC}"
