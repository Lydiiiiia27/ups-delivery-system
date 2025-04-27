#!/bin/bash

echo "UPS World Simulator Test Setup and Execution"
echo "==========================================="

# Function to check if World Simulator is running
check_world_simulator() {
    if docker ps | grep -q world-simulator; then
        echo "✓ World Simulator is running"
        return 0
    else
        echo "✗ World Simulator is not running"
        return 1
    fi
}

# Function to check if Amazon mock service is running
check_amazon_mock() {
    if docker ps | grep -q amazon-mock; then
        echo "✓ Amazon Mock Service is running"
        return 0
    else
        echo "✗ Amazon Mock Service is not running"
        return 1
    fi
}

# Function to start Amazon mock service
start_amazon_mock() {
    echo "Starting Amazon Mock Service..."
    
    # First, make sure any existing container is removed
    docker rm -f amazon-mock 2>/dev/null || true
    
    # Create the mock data file in current directory
    create_amazon_mock_data
    
    # Get available networks
    echo "Available Docker networks:"
    docker network ls
    
    # Start the Mockoon container with the data file mounted and proper networks
    # Use world-network to ensure connectivity with simulator and UPS
    docker run -d --name amazon-mock \
        -p 8081:8080 \
        --network world-network \
        -v "$(pwd)/amazon-mock-data.json:/data.json" \
        mockoon/cli:latest \
        -d /data.json \
        -p 8080
    
    # Connect to ups-network as well if it exists
    if docker network ls | grep -q ups-network; then
        echo "Connecting amazon-mock to ups-network as well"
        docker network connect ups-network amazon-mock
    fi
    
    # Wait for a few seconds to ensure the service starts
    sleep 5
    
    if check_amazon_mock; then
        echo "Amazon Mock Service started successfully"
    else
        echo "Failed to start Amazon Mock Service"
        echo "Checking logs..."
        docker logs amazon-mock
        exit 1
    fi
}

# Function to get current world ID
get_world_id() {
    # Try to get world ID from UPS logs
    WORLD_ID=$(docker logs ups-app 2>&1 | grep "Connected to existing world with ID" | tail -1 | awk '{print $NF}')
    
    # If not found, try to get it from the database
    if [ -z "$WORLD_ID" ]; then
        WORLD_ID=$(docker exec -i world-db psql -U postgres -d worldSim -t -c "SELECT world_id FROM world ORDER BY world_id DESC LIMIT 1;" | tr -d ' ')
    fi
    
    # Default to 1 if still not found
    if [ -z "$WORLD_ID" ]; then
        WORLD_ID="1"
    fi
    
    echo "$WORLD_ID"
}

# Function to create warehouses
create_warehouses() {
    local world_id=$1
    echo "Creating warehouses in World ID: $world_id"
    
    docker exec -i world-db psql -U postgres -d worldSim <<EOF
-- Create warehouses for testing
INSERT INTO warehouse (wh_id, world_id, x, y)
VALUES 
    (1, $world_id, 10, 10),
    (2, $world_id, 20, 20),
    (3, $world_id, 30, 30)
ON CONFLICT DO NOTHING;

-- Show created warehouses
SELECT wh_id, world_id, x, y FROM warehouse WHERE world_id = $world_id;
EOF
}

# Function to check database state
check_database_state() {
    echo ""
    echo "Current Database State:"
    echo "----------------------"
    
    # Check worlds - Use the actual column names in your database
    echo "Worlds:"
    docker exec -i world-db psql -U postgres -d worldSim -c "SELECT world_id FROM world;"
    
    # Check warehouses
    echo ""
    echo "Warehouses:"
    docker exec -i world-db psql -U postgres -d worldSim -c "SELECT wh_id, world_id, x, y FROM warehouse ORDER BY world_id, wh_id;"
    
    # Check trucks - Use the actual column names in your database
    echo ""
    echo "Trucks:"
    docker exec -i world-db psql -U postgres -d worldSim -c "SELECT truck_id, world_id, x, y FROM truck ORDER BY world_id, truck_id LIMIT 10;"
}

# Function to create mock amazon-mock-data.json for the Amazon mock service
create_amazon_mock_data() {
    echo "Creating Amazon mock data configuration..."
    cat > amazon-mock-data.json << 'EOF'
{
  "uuid": "3cb244ad-adbd-4461-afa9-e16b19078dab",
  "lastMigration": 27,
  "name": "Amazon Mock API",
  "endpointPrefix": "",
  "latency": 0,
  "port": 8080,
  "hostname": "0.0.0.0",
  "routes": [
    {
      "uuid": "e4f7b26d-dcaf-4ad3-9e2f-dc5eda674ba9",
      "documentation": "Endpoint for truck arrival notifications",
      "method": "post",
      "endpoint": "api/ups/notifications/truck-arrived",
      "responses": [
        {
          "uuid": "40b36fd7-a7d4-4ca2-9ca2-5ec8090dac02",
          "body": "{ \"success\": true }",
          "latency": 0,
          "statusCode": 200,
          "label": "Success",
          "headers": [
            {
              "key": "Content-Type",
              "value": "application/json"
            }
          ],
          "filePath": "",
          "sendFileAsBody": false,
          "rules": [],
          "rulesOperator": "OR",
          "disableTemplating": false,
          "fallbackTo404": false,
          "default": true
        }
      ],
      "enabled": true,
      "responseMode": null
    },
    {
      "uuid": "b5a9c9e8-4c5d-4b3a-9d2e-8f1c6b4d5a3e",
      "documentation": "Endpoint for delivery completion notifications",
      "method": "post",
      "endpoint": "api/ups/notifications/delivery-complete",
      "responses": [
        {
          "uuid": "c7d8e9f0-a1b2-3c4d-5e6f-7a8b9c0d1e2f",
          "body": "{ \"success\": true }",
          "latency": 0,
          "statusCode": 200,
          "label": "Success",
          "headers": [
            {
              "key": "Content-Type",
              "value": "application/json"
            }
          ],
          "filePath": "",
          "sendFileAsBody": false,
          "rules": [],
          "rulesOperator": "OR",
          "disableTemplating": false,
          "fallbackTo404": false,
          "default": true
        }
      ],
      "enabled": true,
      "responseMode": null
    },
    {
      "uuid": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
      "documentation": "Endpoint for status update notifications",
      "method": "post",
      "endpoint": "api/ups/notifications/status-update",
      "responses": [
        {
          "uuid": "d4c3b2a1-f0e9-8d7c-6b5a-4e3d2c1b0a9f",
          "body": "{ \"success\": true }",
          "latency": 0,
          "statusCode": 200,
          "label": "Success",
          "headers": [
            {
              "key": "Content-Type",
              "value": "application/json"
            }
          ],
          "filePath": "",
          "sendFileAsBody": false,
          "rules": [],
          "rulesOperator": "OR",
          "disableTemplating": false,
          "fallbackTo404": false,
          "default": true
        }
      ],
      "enabled": true,
      "responseMode": null
    }
  ],
  "proxyMode": false,
  "proxyHost": "",
  "cors": true,
  "headers": [
    {
      "key": "Content-Type",
      "value": "application/json"
    }
  ],
  "proxyReqHeaders": [
    {
      "key": "",
      "value": ""
    }
  ],
  "proxyResHeaders": [
    {
      "key": "",
      "value": ""
    }
  ],
  "data": []
}
EOF
    echo "Mock data configuration created successfully"
}

# Function to run tests
run_tests() {
    echo ""
    echo "Running UPS Tests..."
    echo "-------------------"
    
    cd ups-delivery-system
    
    # Export environment variable to enable integration tests
    export WORLD_SIMULATOR_RUNNING=true
    export AMAZON_MOCK_RUNNING=true
    
    # Get the IP address of the Amazon mock container
    AMAZON_MOCK_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' amazon-mock)
    if [ -z "$AMAZON_MOCK_IP" ]; then
        echo "Could not get IP address for amazon-mock container"
        AMAZON_MOCK_IP="amazon-mock"  # Fallback to container name
    fi
    
    echo "Using Amazon Mock IP: $AMAZON_MOCK_IP"
    export AMAZON_SERVICE_URL="http://$AMAZON_MOCK_IP:8080"
    
    # Compile the project first to check for errors
    echo "Compiling project..."
    if ! mvn compile; then
        echo "Compilation failed. Please fix the errors above before running tests."
        cd ..
        return 1
    fi
    
    echo "Running integration tests..."
    
    # Run all integration tests
    set +e  # Don't exit on error
    
    # Run individual test(s) first
    echo "Running AmazonNotificationServiceTest..."
    mvn test -Dtest=AmazonNotificationServiceTest -DfailIfNoTests=false -Damazon.service.url=$AMAZON_SERVICE_URL
    
    # Add new tests here
    echo "Running WorldSimulatorIntegrationTest..."
    mvn test -Dtest=WorldSimulatorIntegrationTest -DfailIfNoTests=false
    
    # Run all integration tests (or specific category)
    echo "Running all integration tests..."
    mvn test -Dgroups=integration -Damazon.service.url=$AMAZON_SERVICE_URL
    TEST_RESULT=$?
    
    if [ $TEST_RESULT -eq 0 ]; then
        echo "Integration tests passed successfully!"
    else
        echo "Some integration tests failed. See above for errors."
    fi
    
    set -e  # Resume exit on error
    
    cd ..
    
    return $TEST_RESULT
}

# Function to clean up after tests
cleanup() {
    echo ""
    echo "Cleaning up..."
    echo "-------------"
    
    # Stop and remove Amazon mock service
    if check_amazon_mock; then
        docker stop amazon-mock
        docker rm amazon-mock
        echo "Amazon Mock Service stopped and removed"
    fi
    
    # Remove the mock data file
    rm -f amazon-mock-data.json
}

# Function to check if the whole environment is running (optional, can start it if needed)
check_environment() {
    echo "Checking if Docker environment is running..."
    
    if ! docker ps > /dev/null 2>&1; then
        echo "Docker is not running. Please start Docker first."
        exit 1
    fi
    
    # Check if ups-app is running
    if ! docker ps | grep -q ups-app; then
        echo "UPS application is not running."
        read -p "Do you want to start the entire environment? (y/n): " answer
        if [ "$answer" = "y" ]; then
            echo "Starting the entire environment..."
            docker-compose up -d
            echo "Waiting for services to initialize..."
            sleep 15
        else
            echo "Test environment requires ups-app to be running. Exiting."
            exit 1
        fi
    fi
}

# Main execution
echo "Step 1: Checking Docker environment"
check_environment

echo ""
echo "Step 2: Checking World Simulator status"
if ! check_world_simulator; then
    echo "World Simulator is not running. It should have been started as part of the environment."
    exit 1
fi

echo ""
echo "Step 3: Setting up Amazon Mock Service"
if ! check_amazon_mock; then
    start_amazon_mock
else
    echo "Amazon Mock Service is already running"
fi

echo ""
echo "Step 4: Getting current World ID"
WORLD_ID=$(get_world_id)
echo "Using World ID: $WORLD_ID"

echo ""
echo "Step 5: Creating test warehouses"
create_warehouses "$WORLD_ID"

echo ""
echo "Step 6: Checking database state"
check_database_state

echo ""
echo "Step 7: Running integration tests"
read -p "Press Enter to run tests or Ctrl+C to cancel..."
run_tests

echo ""
echo "Step 8: Cleanup"
read -p "Press Enter to clean up resources or Ctrl+C to keep them running..."
cleanup

echo ""
echo "Test execution complete!"