#!/bin/bash

echo "UPS World Simulator Test Setup and Execution"
echo "==========================================="

# Function to check if World Simulator is running
check_world_simulator() {
    if docker ps | grep -q world-simulator_server_1; then
        echo "✓ World Simulator is running"
        return 0
    else
        echo "✗ World Simulator is not running"
        return 1
    fi
}

# Function to get current world ID
get_world_id() {
    # Try to get world ID from UPS logs
    WORLD_ID=$(docker logs ups-app 2>&1 | grep "Connected to existing world with ID" | tail -1 | awk '{print $NF}')
    
    # If not found, try to get it from the database
    if [ -z "$WORLD_ID" ]; then
        WORLD_ID=$(docker exec -i world-simulator_mydb_1 psql -U postgres -d worldSim -t -c "SELECT world_id FROM world ORDER BY world_id DESC LIMIT 1;" | tr -d ' ')
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
    
    docker exec -i world-simulator_mydb_1 psql -U postgres -d worldSim <<EOF
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
    docker exec -i world-simulator_mydb_1 psql -U postgres -d worldSim -c "SELECT world_id FROM world;"
    
    # Check warehouses
    echo ""
    echo "Warehouses:"
    docker exec -i world-simulator_mydb_1 psql -U postgres -d worldSim -c "SELECT wh_id, world_id, x, y FROM warehouse ORDER BY world_id, wh_id;"
    
    # Check trucks - Use the actual column names in your database
    echo ""
    echo "Trucks:"
    docker exec -i world-simulator_mydb_1 psql -U postgres -d worldSim -c "SELECT truck_id, world_id, x, y FROM truck ORDER BY world_id, truck_id LIMIT 10;"
}

# Function to run tests
run_tests() {
    echo ""
    echo "Running UPS Tests..."
    echo "-------------------"
    
    cd ups-delivery-system
    
    # Export environment variable to enable integration tests
    export WORLD_SIMULATOR_RUNNING=true
    
    # Run the existing WorldResponseHandlerTest
    mvn test -Dtest=WorldResponseHandlerTest
    
    # You can also run all tests related to world response handling
    mvn test -Dtest=WorldResponseHandler*
    
    cd ..
}

# Main execution
echo "Step 1: Checking World Simulator status"
if ! check_world_simulator; then
    echo "Please start the World Simulator first:"
    echo "  cd world-simulator"
    echo "  docker-compose up -d"
    exit 1
fi

echo ""
echo "Step 2: Getting current World ID"
WORLD_ID=$(get_world_id)
echo "Using World ID: $WORLD_ID"

echo ""
echo "Step 3: Creating test warehouses"
create_warehouses "$WORLD_ID"

echo ""
echo "Step 4: Checking database state"
check_database_state

echo ""
echo "Step 5: Running integration tests"
read -p "Press Enter to run tests or Ctrl+C to cancel..."
run_tests

echo ""
echo "Test execution complete!"