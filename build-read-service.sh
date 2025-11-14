#!/bin/sh
# Build script for XQ Fitness Read Service Docker image
# This script builds the Java Spring Boot read service

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SERVICE_NAME="xq-fitness-read-service"
SERVICE_DIR="./read-service"
IMAGE_TAG="${1:-latest}"
REGISTRY="${2:-}"

echo "${YELLOW}Building ${SERVICE_NAME}...${NC}"

# Check if service directory exists
if [ ! -d "$SERVICE_DIR" ]; then
    echo "${RED}Error: $SERVICE_DIR directory not found${NC}"
    exit 1
fi

# Check if Dockerfile exists
if [ ! -f "$SERVICE_DIR/Dockerfile" ]; then
    echo "${RED}Error: Dockerfile not found in $SERVICE_DIR${NC}"
    exit 1
fi

# Build the image
if [ -n "$REGISTRY" ]; then
    IMAGE_NAME="${REGISTRY}/${SERVICE_NAME}:${IMAGE_TAG}"
else
    IMAGE_NAME="${SERVICE_NAME}:${IMAGE_TAG}"
fi

echo "${YELLOW}Building image: ${IMAGE_NAME}${NC}"

docker build -t "$IMAGE_NAME" "$SERVICE_DIR"

if [ $? -eq 0 ]; then
    echo "${GREEN}Successfully built ${IMAGE_NAME}${NC}"
    echo "${GREEN}Image ID: $(docker images -q $IMAGE_NAME | head -1)${NC}"
else
    echo "${RED}Failed to build ${IMAGE_NAME}${NC}"
    exit 1
fi

# Print useful commands
echo ""
echo "${YELLOW}Next steps:${NC}"
echo "  Run the service:"
echo "    docker run -p 8080:8080 --name ${SERVICE_NAME} ${IMAGE_NAME}"
echo ""
echo "  With database connection (if database is running on localhost):"
echo "    docker run -p 8080:8080 --network host --name ${SERVICE_NAME} ${IMAGE_NAME}"
echo ""
if [ -n "$REGISTRY" ]; then
    echo "  Push to registry:"
    echo "    docker push ${IMAGE_NAME}"
fi
