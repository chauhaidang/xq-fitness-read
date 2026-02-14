#!/bin/sh
# Build script for XQ Fitness Write Service Docker image

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SERVICE_NAME="xq-fitness-read-service"
SERVICE_DIR="."
IMAGE_TAG="${1:-latest}"
REGISTRY="${2:-}"

echo "${YELLOW}Building ${SERVICE_NAME}...${NC}"

if [ ! -d "$SERVICE_DIR" ]; then
    echo "${RED}Error: $SERVICE_DIR directory not found${NC}"
    exit 1
fi

if [ ! -f "$SERVICE_DIR/Dockerfile" ]; then
    echo "${RED}Error: Dockerfile not found in $SERVICE_DIR${NC}"
    exit 1
fi

if [ -n "$REGISTRY" ]; then
    IMAGE_NAME="${REGISTRY}/${SERVICE_NAME}:${IMAGE_TAG}"
else
    IMAGE_NAME="${SERVICE_NAME}:${IMAGE_TAG}"
fi

echo "${YELLOW}Building image: ${IMAGE_NAME}${NC}"

docker build --build-arg GITHUB_TOKEN="${GITHUB_TOKEN}" -t "$IMAGE_NAME" "$SERVICE_DIR"

if [ $? -eq 0 ]; then
    echo "${GREEN}Successfully built ${IMAGE_NAME}${NC}"
    echo "${GREEN}Image ID: $(docker images -q $IMAGE_NAME | head -1)${NC}"
else
    echo "${RED}Failed to build ${IMAGE_NAME}${NC}"
    exit 1
fi

echo ""
echo "${YELLOW}Next steps:${NC}"
echo "  Run the service:"
echo "    docker run -p 3000:3000 --name ${SERVICE_NAME} ${IMAGE_NAME}"
echo ""
if [ -n "$REGISTRY" ]; then
    echo "  Push to registry:"
    echo "    docker push ${IMAGE_NAME}"
fi
