#!/bin/bash

echo "UPS World Simulator Test Setup and Execution"
echo "==========================================="

# Function to check if World Simulator is running
check_world_simulator() {
    if docker ps --format '{{.Names}}' | grep -q "^world-simulator$"; then
        echo "✓ World Simulator is running"
        return 0
    else
        echo "✗ World Simulator is not running"
        return 1
    fi
}

# Function to check if Amazon mock service is running
check_amazon_mock() {
    if docker ps --format '{{.Names}}' | grep -q "^amazon-mock$"; then
        echo "✓ Amazon Mock Service is running"
        return 0
    else
        echo "✗ Amazon Mock Service is not running"
        return 1
    fi
}

# Function to check if UPS app is running
check_ups_app() {
    if docker ps --format '{{.Names}}' | grep -q "^ups-app$"; then
        echo "✓ UPS Application is running"
        return 0
    else
        echo "✗ UPS Application is not running"
        return 1
    fi
}

# Function to check container health
check_container_health() {
    local container_name=$1
    local max_attempts=30
    local attempt=1
    
    echo "Checking health of $container_name..."
    
    while [ $attempt -le $max_attempts ]; do
        if docker ps --format '{{.Names}}' | grep -q "^$container_name$"; then
            local status=$(docker inspect --format='{{.State.Status}}' $container_name 2>/dev/null)
            if [ "$status" = "running" ]; then
                # For Mockoon, we need to check if it's actually responding
                if [ "$container_name" = "amazon-mock" ]; then
                    if curl -s http://localhost:8081/api/ups/notifications/truck-arrived > /dev/null; then
                        echo "✓ $container_name is healthy and running"
                        return 0
                    fi
                else
                    echo "✓ $container_name is healthy and running"
                    return 0
                fi
            fi
        fi
        
        echo "Attempt $attempt/$max_attempts: $container_name not ready yet..."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo "✗ $container_name failed to become healthy"
    return 1
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
    
    # Use the exact network names from your environment
    WORLD_NETWORK="erss-project-ys467-jt454_world-network"
    UPS_NETWORK="erss-project-ys467-jt454_ups-network"
    
    echo "Using network: $WORLD_NETWORK"
    
    # Start the Mockoon container with the data file mounted and proper networks
    # Use world-network to ensure connectivity with simulator and UPS
    docker run -d --name amazon-mock \
        -p 8081:8080 \
        --network $WORLD_NETWORK \
        -v "$(pwd)/amazon-mock-data.json:/data.json" \
        mockoon/cli:latest \
        -d /data.json \
        -p 8080
    
    # Connect to ups-network as well
    echo "Connecting amazon-mock to $UPS_NETWORK as well"
    docker network connect $UPS_NETWORK amazon-mock
    
    # Wait for the service to become healthy
    if check_container_health "amazon-mock"; then
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
    WORLD_ID=$(docker logs ups-app 2>&1 | grep "Connected to" | grep "world ID" | tail -1 | sed -n 's/.*world ID: \([0-9]\+\).*/\1/p')
    
    # If not found, try to get it from the database
    if [ -z "$WORLD_ID" ]; then
        WORLD_ID=$(docker exec -i mydb psql -U postgres -d worldSim -t -c "SELECT world_id FROM world ORDER BY world_id DESC LIMIT 1;" | tr -d ' ')
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
    
    docker exec -i mydb psql -U postgres -d worldSim <<EOF
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
    docker exec -i mydb psql -U postgres -d worldSim -c "SELECT world_id FROM world;" || echo "No worlds found or table doesn't exist"
    
    # Check warehouses
    echo ""
    echo "Warehouses:"
    docker exec -i mydb psql -U postgres -d worldSim -c "SELECT wh_id, world_id, x, y FROM warehouse ORDER BY world_id, wh_id;" || echo "No warehouses found or table doesn't exist"
    
    # Check trucks - Use the actual column names in your database
    echo ""
    echo "Trucks:"
    docker exec -i mydb psql -U postgres -d worldSim -c "SELECT truck_id, world_id, x, y FROM truck ORDER BY world_id, truck_id LIMIT 10;" || echo "No trucks found or table doesn't exist"
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
    # Try different methods to get the IP
    AMAZON_MOCK_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' amazon-mock | head -1)
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

# Function to check if the whole environment is running
check_environment() {
    echo "Checking if Docker environment is running..."
    
    if ! docker ps > /dev/null 2>&1; then
        echo "Docker is not running. Please start Docker first."
        exit 1
    fi
    
    # Check all required services
    local services_to_check=("ups-app" "world-simulator" "mydb" "ups-db")
    local all_services_running=true
    
    for service in "${services_to_check[@]}"; do
        if ! docker ps --format '{{.Names}}' | grep -q "^$service$"; then
            echo "$service is not running."
            all_services_running=false
        fi
    done
    
    if [ "$all_services_running" = false ]; then
        read -p "Some services are not running. Do you want to start the entire environment? (y/n): " answer
        if [ "$answer" = "y" ]; then
            echo "Stopping all existing containers..."
            docker-compose down
            
            echo "Starting the entire environment..."
            docker-compose up -d
            
            echo "Waiting for services to initialize..."
            for service in "${services_to_check[@]}"; do
                check_container_health "$service"
            done
            
            echo "Waiting additional time for services to fully initialize..."
            sleep 10
        else
            echo "Test environment requires all services to be running. Exiting."
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
    echo "Trying to restart Docker services..."
    docker-compose down
    docker-compose up -d
    sleep 15
    if ! check_world_simulator; then
        echo "World Simulator is still not running. Please check Docker logs."
        exit 1
    fi
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