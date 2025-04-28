#!/bin/bash

echo "UPS Functionality Test"
echo "====================="
echo "Starting tests: $(date)"

# Configuration
UPS_URL="http://localhost:8080"
AMAZON_MOCK_URL="http://localhost:8081"
TEST_PACKAGE_ID=3001

# 1. Test web interface accessibility
echo -e "\n1. Testing UPS web interface:"
WEB_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" $UPS_URL)

if [[ "$WEB_RESPONSE" == "200" || "$WEB_RESPONSE" == "302" ]]; then
    echo "✅ UPS web interface is accessible ($WEB_RESPONSE)"
else
    echo "❌ UPS web interface is not accessible ($WEB_RESPONSE)"
    echo "Try accessing manually: $UPS_URL"
fi

# 2. Create shipment using mock Amazon
echo -e "\n2. Creating test shipment via mock Amazon:"
SHIPMENT_REQUEST='{
    "package_id": '$TEST_PACKAGE_ID',
    "warehouse_id": 1,
    "dest_x": 20,
    "dest_y": 30
}'

SHIPMENT_RESPONSE=$(curl -s -X POST "$AMAZON_MOCK_URL/api/test/send-shipment" \
    -H "Content-Type: application/json" \
    -d "$SHIPMENT_REQUEST")

if echo "$SHIPMENT_RESPONSE" | grep -q "success.*true"; then
    echo "✅ Shipment created successfully"
    echo "Response: $SHIPMENT_RESPONSE"
else
    echo "❌ Failed to create shipment"
    echo "Response: $SHIPMENT_RESPONSE"
fi

# 3. Wait for UPS to process the shipment
echo -e "\n3. Waiting for UPS to process the shipment (15 seconds)..."
sleep 15

# 4. Check shipment status in database
echo -e "\n4. Checking shipment in UPS database:"
PACKAGE_DB=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT id, status FROM packages WHERE id = $TEST_PACKAGE_ID;")

if [[ -n "$PACKAGE_DB" ]]; then
    echo "✅ Package found in UPS database"
    echo "DB Record: $PACKAGE_DB"
else
    echo "❌ Package not found in UPS database"
fi

# 5. Check shipment status in mock Amazon
echo -e "\n5. Checking shipment status in mock Amazon:"
AMAZON_STATUS=$(curl -s "$AMAZON_MOCK_URL/api/test/shipments" | grep -o "\"$TEST_PACKAGE_ID\".*\"status\":.*}")

if [[ -n "$AMAZON_STATUS" ]]; then
    echo "✅ Shipment status tracked in Amazon"
    echo "Status: $AMAZON_STATUS"
else
    echo "❌ Shipment status not found in Amazon"
fi

# 6. Check tracking web interface
echo -e "\n6. Testing package tracking web interface:"
TRACKING_RESPONSE=$(curl -s -L "$UPS_URL/tracking?trackingNumber=$TEST_PACKAGE_ID")

if echo "$TRACKING_RESPONSE" | grep -q "$TEST_PACKAGE_ID\|Tracking"; then
    echo "✅ Package tracking page accessible"
else
    echo "❌ Package tracking page not accessible or doesn't show package info"
    echo "Try tracking manually: $UPS_URL/tracking"
fi

# 7. Check UPS logs for truck assignment and delivery
echo -e "\n7. Checking UPS logs for package workflow:"
UPS_LOGS=$(docker logs ups-app | grep -A 15 "$TEST_PACKAGE_ID\|sendTruckTo\|deliver\|truck.*assigned" | tail -20)
echo "$UPS_LOGS"

echo -e "\nTest Summary"
echo "============"
echo "- UPS Web Interface: $(if [[ "$WEB_RESPONSE" == "200" || "$WEB_RESPONSE" == "302" ]]; then echo "✅"; else echo "❌"; fi)"
echo "- API Communication: $(if echo "$SHIPMENT_RESPONSE" | grep -q "success.*true"; then echo "✅"; else echo "❌"; fi)"
echo "- Database Storage: $(if [[ -n "$PACKAGE_DB" ]]; then echo "✅"; else echo "❌"; fi)"
echo "- Status Tracking: $(if [[ -n "$AMAZON_STATUS" ]]; then echo "✅"; else echo "❌"; fi)"
echo "- Tracking Interface: $(if echo "$TRACKING_RESPONSE" | grep -q "$TEST_PACKAGE_ID\|Tracking"; then echo "✅"; else echo "❌"; fi)"

echo -e "\nTests completed: $(date)"