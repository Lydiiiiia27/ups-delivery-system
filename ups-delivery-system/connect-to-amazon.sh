#!/bin/bash

# Script to connect UPS to an Amazon instance
# For use with the erss-project-ys467-jt454 UPS delivery system

# Set colors for better readability
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Display banner
echo -e "${GREEN}UPS-Amazon Connection Utility${NC}"
echo "==============================="

# Check if we're connecting to mock or external Amazon
if [ $# -eq 0 ]; then
    echo -e "${YELLOW}No Amazon host specified. Using local mock Amazon on port 8082.${NC}"
    USE_MOCK=true
    AMAZON_HOST="localhost"
    AMAZON_PORT=8082
elif [ "$1" = "mock" ]; then
    echo -e "${YELLOW}Using local mock Amazon on port 8082.${NC}"
    USE_MOCK=true
    AMAZON_HOST="localhost"
    AMAZON_PORT=8082
else
    if [ $# -ne 1 ] && [ $# -ne 2 ]; then
        echo -e "${RED}Usage: $0 <amazon-host> [port]${NC}"
        echo "Examples:"
        echo "  $0 vcm-12345.vm.duke.edu      (uses default port 8080)"
        echo "  $0 vcm-12345.vm.duke.edu 8082 (uses specified port 8082)"
        echo "  $0 mock                       (uses local mock Amazon)"
        exit 1
    fi
    
    USE_MOCK=false
    AMAZON_HOST=$1
    AMAZON_PORT=${2:-8080}  # Default to 8080 if port not specified
    echo -e "${YELLOW}Connecting to external Amazon instance at ${AMAZON_HOST}:${AMAZON_PORT}${NC}"
fi

# Determine correct URL based on host type
if [ "$AMAZON_HOST" = "localhost" ] || [ "$AMAZON_HOST" = "127.0.0.1" ]; then
    # For localhost, use the Docker bridge network IP to ensure connectivity from container
    AMAZON_SERVICE_URL="http://host.docker.internal:${AMAZON_PORT}"
    # On Linux, may need to use the Docker gateway IP
    if [ "$(uname)" = "Linux" ]; then
        # Check if we can use host.docker.internal
        if ! docker info | grep -q "host.docker.internal"; then
            DOCKER_GATEWAY_IP=$(docker network inspect bridge --format='{{range .IPAM.Config}}{{.Gateway}}{{end}}')
            if [ -n "$DOCKER_GATEWAY_IP" ]; then
                AMAZON_SERVICE_URL="http://${DOCKER_GATEWAY_IP}:${AMAZON_PORT}"
            fi
        fi
    fi
else
    # For remote hosts, use the hostname directly
    AMAZON_SERVICE_URL="http://${AMAZON_HOST}:${AMAZON_PORT}"
fi

# Update the environment variable
export AMAZON_SERVICE_URL
echo -e "${GREEN}Setting Amazon service URL to: ${AMAZON_SERVICE_URL}${NC}"

# Test connection to Amazon
echo "Testing connection to Amazon service..."
if curl -s --head --fail "${AMAZON_SERVICE_URL}/health" > /dev/null; then
    echo -e "${GREEN}✓ Amazon service is reachable${NC}"
else
    echo -e "${YELLOW}⚠ Unable to reach Amazon service at ${AMAZON_SERVICE_URL}${NC}"
    echo "This may be normal if the Amazon instance doesn't support the /health endpoint."
    echo "Continuing anyway..."
fi

# Restart the UPS container with the new environment variable
echo "Restarting UPS container with new Amazon connection..."
docker-compose stop ups-app
docker-compose rm -f ups-app
docker-compose up -d ups-app

echo -e "${GREEN}UPS is now connecting to Amazon at ${AMAZON_SERVICE_URL}${NC}"
echo "Waiting for UPS container to start..."
sleep 5

# Verify the connection in UPS logs
echo "Checking UPS logs for Amazon connection information..."
AMAZON_CONFIG=$(docker logs ups-app 2>&1 | grep -i "amazon.*url\|connect.*amazon" | tail -3)
if [ -n "$AMAZON_CONFIG" ]; then
    echo -e "${GREEN}Found Amazon configuration in UPS logs:${NC}"
    echo "$AMAZON_CONFIG"
else
    echo -e "${YELLOW}No Amazon configuration found in UPS logs. This might be normal.${NC}"
fi

echo -e "${GREEN}Setup complete!${NC}"
echo "Check detailed logs with: docker logs -f ups-app | grep -i amazon"

# If using mock Amazon, provide additional info
if [ "$USE_MOCK" = true ]; then
    echo -e "\n${YELLOW}Using Mock Amazon:${NC}"
    echo "- Mock Amazon Dashboard: http://localhost:8082/"
    echo "- UPS Tracking: http://localhost:8080/tracking"
    echo "- Run 'demo-script.sh' for a guided demonstration"
fi 