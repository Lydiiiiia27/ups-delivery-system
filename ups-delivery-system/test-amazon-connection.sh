#!/bin/bash

# Test script to validate UPS-Amazon integration
# For use with the erss-project-ys467-jt454 UPS delivery system

# Set colors for better readability
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Display banner
echo -e "${BLUE}UPS-Amazon Integration Test${NC}"
echo "=========================="

# Configuration
UPS_URL="http://localhost:8080"
AMAZON_URL=$(docker exec ups-app bash -c 'echo $AMAZON_SERVICE_URL' 2>/dev/null || echo "Unknown")
TEST_PACKAGE_ID=$(date +%s)
CURRENT_TIME=$(date +%s)

echo -e "UPS URL: ${BLUE}${UPS_URL}${NC}"
echo -e "Amazon URL: ${BLUE}${AMAZON_URL}${NC}"
echo -e "Test Package ID: ${BLUE}${TEST_PACKAGE_ID}${NC}"
echo -e "Starting tests: ${BLUE}$(date)${NC}"

# 1. Check UPS container is running
echo -e "\n${BLUE}1. Checking UPS container status:${NC}"
if docker ps | grep -q ups-app; then
    echo -e "${GREEN}✅ UPS container is running${NC}"
else
    echo -e "${RED}❌ UPS container is not running${NC}"
    echo "Please start the UPS container first with: docker-compose up -d ups-app"
    exit 1
fi

# 2. Check UPS connection to Amazon
echo -e "\n${BLUE}2. Testing UPS connection to Amazon:${NC}"
UPS_CONFIG=$(docker exec ups-app bash -c 'env | grep AMAZON' 2>/dev/null)
if [ -n "$UPS_CONFIG" ]; then
    echo -e "${GREEN}✅ UPS Amazon configuration:${NC}"
    echo "$UPS_CONFIG"
else
    echo -e "${YELLOW}⚠️ No Amazon configuration found in UPS environment${NC}"
    echo "This might be expected if configuration is set via other means."
fi

# 3. Check if Amazon is reachable from UPS
echo -e "\n${BLUE}3. Testing if Amazon is reachable from UPS:${NC}"
if docker exec ups-app curl -s --max-time 5 --head --fail "${AMAZON_URL}/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Amazon service is reachable from UPS container${NC}"
else
    echo -e "${YELLOW}⚠️ Amazon service might not be reachable from UPS container${NC}"
    echo "This could be normal if Amazon doesn't have a /health endpoint or has a different URL structure."
fi

# 4. Check for any errors in UPS logs related to Amazon connection
echo -e "\n${BLUE}4. Checking UPS logs for Amazon connection issues:${NC}"
CONNECTION_ERRORS=$(docker logs ups-app 2>&1 | grep -i "amazon.*error\|failed.*amazon\|connect.*amazon.*fail" | tail -8)

if [[ -z "$CONNECTION_ERRORS" ]]; then
    echo -e "${GREEN}✅ No Amazon connection errors found in logs${NC}"
else
    echo -e "${YELLOW}⚠️ Found potential Amazon connection issues:${NC}"
    echo "$CONNECTION_ERRORS"
fi

# 5. View recent messages between UPS and Amazon
echo -e "\n${BLUE}5. Recent messages between UPS and Amazon:${NC}"
RECENT_MESSAGES=$(docker logs ups-app 2>&1 | grep -i "amazon.*notification\|sending.*amazon\|received.*amazon\|amazon.*request" | tail -8)
if [[ -z "$RECENT_MESSAGES" ]]; then
    echo -e "${YELLOW}⚠️ No recent Amazon messages found in logs${NC}"
    echo "This could be normal if no shipments have been processed yet."
else
    echo -e "${GREEN}✅ Found recent Amazon interactions:${NC}"
    echo "$RECENT_MESSAGES"
fi

# 6. Retrieve valid warehouse IDs from UPS database
echo -e "\n${BLUE}6. Getting valid warehouse IDs from UPS:${NC}"
WAREHOUSES=$(docker exec ups-db psql -U postgres -d ups -t -c "SELECT id, x, y FROM warehouse ORDER BY id;" 2>/dev/null)
if [[ -z "$WAREHOUSES" ]]; then
    echo -e "${YELLOW}⚠️ Could not retrieve warehouse information${NC}"
    echo "Using default warehouse IDs: 1, 2, 3, 999"
    VALID_WAREHOUSE_ID=1
else
    echo -e "${GREEN}✅ Found warehouses in UPS database:${NC}"
    echo "$WAREHOUSES" | sed 's/^/ /'
    # Get first warehouse ID for testing
    VALID_WAREHOUSE_ID=$(echo "$WAREHOUSES" | head -1 | awk '{print $1}')
    VALID_WAREHOUSE_ID=${VALID_WAREHOUSE_ID:-1}  # Default to 1 if empty
fi

# 7. Test creating a shipment (if Amazon mock is available)
echo -e "\n${BLUE}7. Testing shipment creation with Amazon:${NC}"
if [[ "$AMAZON_URL" == *"localhost"* ]] || [[ "$AMAZON_URL" == *"127.0.0.1"* ]]; then
    if curl -s --max-time 5 --head --fail "${AMAZON_URL}/health" > /dev/null; then
        echo -e "${GREEN}✅ Mock Amazon is available. Attempting to create a test shipment...${NC}"
        
        SHIPMENT_RESULT=$(curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "{\"package_id\":${TEST_PACKAGE_ID}, \"warehouse_id\":${VALID_WAREHOUSE_ID}, \"dest_x\":25, \"dest_y\":30}" \
            "${AMAZON_URL}/api/test/send-shipment")
        
        if [[ "$SHIPMENT_RESULT" == *"success"* ]]; then
            echo -e "${GREEN}✅ Test shipment created successfully!${NC}"
            echo "Package ID: ${TEST_PACKAGE_ID}, Warehouse ID: ${VALID_WAREHOUSE_ID}"
            echo "Check UPS tracking at: ${UPS_URL}/tracking?trackingNumber=${TEST_PACKAGE_ID}"
        else
            echo -e "${RED}❌ Failed to create test shipment${NC}"
            echo "Response: $SHIPMENT_RESULT"
        fi
    else
        echo -e "${YELLOW}⚠️ Mock Amazon is not available for testing shipment creation${NC}"
        echo "You might be connected to a real Amazon instance which doesn't support test endpoints."
    fi
else
    echo -e "${YELLOW}⚠️ Skipping shipment creation test for non-localhost Amazon${NC}"
    echo "For safety, we don't create test shipments on external Amazon instances."
fi

echo -e "\n${GREEN}Test completed: $(date)${NC}"
echo -e "${BLUE}Quick Tips:${NC}"
echo "- To view detailed Amazon logs: docker logs mock-amazon"
echo "- To view detailed UPS logs: docker logs ups-app"
echo "- To filter UPS logs for Amazon interactions: docker logs ups-app | grep -i amazon"
echo "- To run the demo script: ./demo-script.sh" 