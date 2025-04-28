#!/bin/bash

echo "UPS Delivery System Direct API Test"
echo "=================================="
echo "Starting tests: $(date)"

# Base URL for the API
BASE_URL="http://localhost:8080"

# Login to get a session cookie
echo -e "\n1. Testing authentication/login:"
LOGIN_RESPONSE=$(curl -v -c cookies.txt -X POST $BASE_URL/login \
  -d "username=testuser&password=testpass" \
  -H "Content-Type: application/x-www-form-urlencoded" 2>&1)

# Extract the redirect status
REDIRECT=$(echo "$LOGIN_RESPONSE" | grep -o "302 Found" || echo "No redirect")

if [[ "$REDIRECT" == "302 Found" ]]; then
  echo "✅ Login endpoint works (redirects after login attempt)"
else
  echo "❌ Login endpoint not working as expected"
  echo "Response: $LOGIN_RESPONSE"
fi

# Test tracking functionality
echo -e "\n2. Testing package tracking:"
TRACKING_RESPONSE=$(curl -v -b cookies.txt "$BASE_URL/tracking?trackingNumber=1001" 2>&1)

# Check if we get a form or redirect (both are valid for this endpoint)
if echo "$TRACKING_RESPONSE" | grep -q "HTTP/1.1 200\|input.*trackingNumber\|HTTP/1.1 302"; then
  echo "✅ Tracking endpoint accessible"
else
  echo "❌ Tracking endpoint not working"
  echo "Response: $(echo "$TRACKING_RESPONSE" | grep -A5 'HTTP/1.1')"
fi

# Test direct API calls to backend
echo -e "\n3. Testing direct API communication:"

# Create a shipment request
echo "a. Testing createshipment API:"
SHIP_REQUEST='{
  "message_type": "CreateShipmentRequest",
  "seq_num": 12345,
  "timestamp": "2025-04-10T15:00:00Z",
  "shipment_info": {
    "package_id": 2001,
    "warehouse_id": 1,
    "destination": {"x": 15, "y": 25},
    "ups_account_name": "testuser",
    "items": [
      {
        "product_id": 5001,
        "description": "Test Product",
        "count": 1
      }
    ]
  }
}'

SHIP_RESPONSE=$(curl -v -X POST "$BASE_URL/api/createshipment" \
  -H "Content-Type: application/json" \
  -d "$SHIP_REQUEST" 2>&1)

if echo "$SHIP_RESPONSE" | grep -q "HTTP/1.1 200\|message_type.*CreateShipmentResponse"; then
  echo "✅ Shipment API accessible"
else
  echo "❌ Shipment API not working"
  echo "Response: $(echo "$SHIP_RESPONSE" | grep -A20 'HTTP/1.1')"
fi

# Check database for entities created
echo -e "\n4. Checking database for entities:"

echo "a. Checking packages table:"
PACKAGES=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT * FROM packages WHERE id = 2001;")
if [[ -n "$PACKAGES" ]]; then
  echo "✅ Package with ID 2001 found in database"
  echo "$PACKAGES"
else
  echo "❌ Package not found or not created in database"
fi

echo -e "\nb. Checking truck assignments:"
TRUCKS=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT t.id, t.status FROM trucks t JOIN packages p ON t.id = p.truck_id WHERE p.id = 2001;")
if [[ -n "$TRUCKS" ]]; then
  echo "✅ Truck assigned to package 2001"
  echo "$TRUCKS"
else
  echo "❌ No truck assignment found for package 2001"
fi

# Check UPS API logs
echo -e "\n5. Checking UPS API logs for activity:"
API_LOGS=$(docker logs ups-app 2>&1 | grep -A 10 "createshipment\|CreateShipment\|Amazon API" | tail -20)
echo "$API_LOGS"

echo -e "\nTests completed: $(date)"


# Check the UPS app structure
echo "Checking UPS application structure:"
docker exec ups-app ls -la /app

# Check the main startup logs
echo "UPS app startup logs:"
docker logs ups-app | head -50

# Check for specific error logs
echo "Checking error logs:"
docker logs ups-app | grep -i "error\|exception" | tail -20

# Check database structure
echo "Database tables:"
docker exec -i ups-db psql -U postgres -d ups -c "\dt"

# Check schema of key tables
echo "Packages table schema:"
docker exec -i ups-db psql -U postgres -d ups -c "\d packages"

# Check truck table 
echo "Trucks table schema:"
docker exec -i ups-db psql -U postgres -d ups -c "\d trucks"

# Check user data
echo "User data:"
docker exec -i ups-db psql -U postgres -d ups -c "SELECT id, username, email FROM users;"