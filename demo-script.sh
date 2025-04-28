#!/bin/bash

# Demonstration script for UPS system

echo "UPS System Demonstration"
echo "======================="
echo "This script will guide you through demonstrating the UPS system functionality."
echo "It includes an automatic package lifecycle simulation that will show"
echo "how packages move through the UPS delivery system in real-time."

# Check database schema function
check_database_schema() {
    echo "Checking UPS database schema..."
    
    # Check if truck table exists and has status column
    TRUCK_STATUS_COL=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT column_name FROM information_schema.columns WHERE table_name = 'truck' AND column_name = 'status';")
    if [[ -z "$TRUCK_STATUS_COL" ]]; then
        echo "Warning: Truck table does not have a 'status' column. Some demo features may not work."
        HAS_TRUCK_STATUS=false
    else
        echo "✓ Found truck status column"
        HAS_TRUCK_STATUS=true
    fi
    
    # Check if packages table exists
    PACKAGES_TABLE=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT table_name FROM information_schema.tables WHERE table_name = 'packages';")
    if [[ -z "$PACKAGES_TABLE" ]]; then
        echo "Error: Packages table does not exist. Demo will not work correctly."
        HAS_PACKAGES=false
    else
        echo "✓ Found packages table"
        HAS_PACKAGES=true
    fi
    
    # Check for packages status column
    PACKAGE_STATUS_COL=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT column_name FROM information_schema.columns WHERE table_name = 'packages' AND column_name = 'status';")
    if [[ -z "$PACKAGE_STATUS_COL" ]]; then
        echo "Warning: Packages table does not have a 'status' column. Some demo features may not work."
        HAS_PACKAGE_STATUS=false
    else
        echo "✓ Found package status column"
        HAS_PACKAGE_STATUS=true
    fi
    
    # Find valid package status values
    VALID_STATUSES=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'packages_status_check';")
    echo "✓ Found valid package status values"
    
    # Set status constants for each stage of the delivery process
    # These match your allowed statuses from the packages_status_check constraint
    STATUS_CREATED="CREATED"
    STATUS_ASSIGNED="ASSIGNED"
    STATUS_PICKUP_READY="PICKUP_READY"
    STATUS_LOADING="LOADING"
    STATUS_LOADED="LOADED"
    STATUS_OUT_FOR_DELIVERY="OUT_FOR_DELIVERY"
    STATUS_DELIVERING="DELIVERING"
    STATUS_DELIVERED="DELIVERED"
    
    # Check for updated_at column instead of delivered_at
    UPDATED_AT_COL=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT column_name FROM information_schema.columns WHERE table_name = 'packages' AND column_name = 'updated_at';")
    if [[ -z "$UPDATED_AT_COL" ]]; then
        echo "Warning: Packages table does not have an 'updated_at' column."
        HAS_UPDATED_AT=false
    else
        echo "✓ Found updated_at column (will use instead of delivered_at)"
        HAS_UPDATED_AT=true
    fi
    
    echo "Database schema check complete."
    echo ""
}

# Check if required services are running
REQUIRED_SERVICES=("ups-app" "ups-db" "world-simulator" "mock-amazon")
MISSING_SERVICES=()

for SERVICE in "${REQUIRED_SERVICES[@]}"; do
    if ! docker ps --format "{{.Names}}" | grep -q "$SERVICE"; then
        MISSING_SERVICES+=("$SERVICE")
    fi
done

if [ ${#MISSING_SERVICES[@]} -gt 0 ]; then
    echo "Error: The following required services are not running:"
    for SERVICE in "${MISSING_SERVICES[@]}"; do
        echo "  - $SERVICE"
    done
    
    echo "Please start all required services before running this demonstration."
    echo "You can use './run-demo.sh' to start the mock Amazon service."
    exit 1
fi

# Check the database schema
check_database_schema

echo "All required services are running!"

# Demonstration steps
echo ""
echo "=== UPS System Demonstration Steps ==="
echo ""
echo "Press Enter after completing each step to continue."

echo ""
echo "STEP 1: Show the UPS web interface"
echo "Open your browser and navigate to: http://localhost:8080"
echo "This shows the UPS tracking and login pages."
read -p "Press Enter when ready to continue..."

echo ""
echo "STEP 2: Show the mock Amazon interface"
echo "Open your browser and navigate to: http://localhost:8082"
echo "This shows the Amazon mock interface for creating shipments."
read -p "Press Enter when ready to continue..."

echo ""
echo "STEP 3: Create a new shipment from Amazon"
echo "Use the 'Create New Shipment' form on the Amazon mock interface."
echo "Important notes:"
echo " - Valid warehouse IDs from your database are: 1, 2, 3, and 999"
echo " - Make sure to use one of these warehouse IDs to avoid errors"
echo " - The web interface should show a dropdown with these valid IDs"
echo "Fill in the form and click 'Create Shipment'."
read -p "Press Enter after creating a shipment..."

echo ""
echo "STEP 4: Show the UPS database to verify the shipment was received"
echo "Running query to check for recent shipments..."
docker exec -i ups-db psql -U postgres -d ups -c "SELECT id, status, created_at FROM packages ORDER BY created_at DESC LIMIT 5;"
read -p "Press Enter to continue..."

echo ""
echo "STEP 5: Explain the UPS system architecture"
echo "The UPS system consists of several components:"
echo "  - Front-end web interface for tracking packages and user management"
echo "  - Back-end API for communication with Amazon and the World simulator"
echo "  - Database for storing package, truck, and user information"
echo "  - Connection to the World simulator for coordinating truck movements"
read -p "Press Enter to continue..."

echo ""
echo "STEP 6: Show the package tracking functionality"
echo "Use the tracking number from Step 3 and go to:"
echo "http://localhost:8080/tracking?trackingNumber=<package_id>"
read -p "Press Enter after showing the tracking page..."

echo ""
echo "STEP 7: Observe the full lifecycle"
echo "We'll now simulate the full lifecycle of a package automatically."
echo ""
echo "Valid package status values in your database are:"
echo "  - $STATUS_CREATED: Initial package creation"
echo "  - $STATUS_ASSIGNED: Package assigned to a delivery"
echo "  - $STATUS_PICKUP_READY: Truck dispatched to warehouse"
echo "  - $STATUS_LOADING: Package being loaded onto truck"
echo "  - $STATUS_LOADED: Package loaded onto truck"
echo "  - $STATUS_OUT_FOR_DELIVERY: Package on delivery route"
echo "  - $STATUS_DELIVERING: Package being delivered"
echo "  - $STATUS_DELIVERED: Package successfully delivered"
echo ""
echo "Enter the package ID from the previous step:"
read PACKAGE_ID

if [[ -z "$PACKAGE_ID" ]]; then
    echo "No package ID provided. Using the most recently created package..."
    PACKAGE_ID=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT id FROM packages ORDER BY created_at DESC LIMIT 1;")
    PACKAGE_ID=$(echo $PACKAGE_ID | tr -d ' ')
    echo "Selected package ID: $PACKAGE_ID"
fi

echo "Simulating package lifecycle for package ID: $PACKAGE_ID"
echo ""
echo "1. Package Created (✓ Already done)"

# Check current package status
if [ "$HAS_PACKAGE_STATUS" = true ]; then
    CURRENT_STATUS=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT status FROM packages WHERE id = $PACKAGE_ID;")
    CURRENT_STATUS=$(echo $CURRENT_STATUS | tr -d ' ')
    echo "   Current status: $CURRENT_STATUS"
else
    echo "   Status: Unknown (status column not found)"
fi

echo ""
echo "2. Dispatching UPS truck to warehouse..."

# Get warehouse ID for this package
WAREHOUSE_ID=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT warehouse_id FROM packages WHERE id = $PACKAGE_ID;")
WAREHOUSE_ID=$(echo $WAREHOUSE_ID | tr -d ' ')

if [[ -z "$WAREHOUSE_ID" ]]; then
    echo "   Error: Could not find warehouse ID for package $PACKAGE_ID"
    echo "   Moving to next step..."
else
    # Get a truck ID to assign to this package - using 'truck' table, not 'trucks'
    if [ "$HAS_TRUCK_STATUS" = true ]; then
        TRUCK_ID=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT id FROM truck WHERE status = 'IDLE' OR status = 'DELIVERING' LIMIT 1;")
        TRUCK_ID=$(echo $TRUCK_ID | tr -d ' ')
    else
        # If no status column, just get any truck
        TRUCK_ID=$(docker exec -i ups-db psql -U postgres -d ups -t -c "SELECT id FROM truck LIMIT 1;")
        TRUCK_ID=$(echo $TRUCK_ID | tr -d ' ')
    fi
    
    if [[ -z "$TRUCK_ID" ]]; then
        echo "   Error: No available trucks found"
        echo "   Moving to next step..."
    else
        # Update truck status to DELIVERING
        if [ "$HAS_TRUCK_STATUS" = true ]; then
            docker exec -i ups-db psql -U postgres -d ups -c "UPDATE truck SET status = 'DELIVERING' WHERE id = $TRUCK_ID;"
        fi
        
        # Update package status to PICKUP_READY 
        if [ "$HAS_PACKAGE_STATUS" = true ]; then
            docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET status = '$STATUS_PICKUP_READY', truck_id = $TRUCK_ID WHERE id = $PACKAGE_ID;"
        else
            # Just update truck_id if no status column
            docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET truck_id = $TRUCK_ID WHERE id = $PACKAGE_ID;"
        fi
        
        echo "   ✓ Truck $TRUCK_ID dispatched to warehouse $WAREHOUSE_ID"
        
        # Wait a moment for visual effect
        sleep 2
        
        echo ""
        echo "3. Truck arrived at warehouse..."
        
        # Update package status to LOADING
        if [ "$HAS_PACKAGE_STATUS" = true ]; then
            docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET status = '$STATUS_LOADING' WHERE id = $PACKAGE_ID;"
        fi
        
        echo "   ✓ Truck $TRUCK_ID arrived at warehouse $WAREHOUSE_ID"
        
        # Wait a moment for visual effect
        sleep 2
        
        echo ""
        echo "4. Package loading..."
        
        # Update package status to LOADED
        if [ "$HAS_PACKAGE_STATUS" = true ]; then
            docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET status = '$STATUS_LOADED' WHERE id = $PACKAGE_ID;"
        fi
        
        echo "   ✓ Package $PACKAGE_ID loaded onto truck $TRUCK_ID"
        
        # Wait a moment for visual effect
        sleep 2
        
        echo ""
        echo "5. Truck delivering package..."
        
        # Update package status to DELIVERING
        if [ "$HAS_PACKAGE_STATUS" = true ]; then
            docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET status = '$STATUS_DELIVERING' WHERE id = $PACKAGE_ID;"
        fi
        
        echo "   ✓ Truck $TRUCK_ID en route to delivery location"
        
        # Wait a moment for visual effect
        sleep 3
        
        echo ""
        echo "6. Package delivered!"
        
        # Update package status to DELIVERED
        if [ "$HAS_PACKAGE_STATUS" = true ]; then
            if [ "$HAS_UPDATED_AT" = true ]; then
                docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET status = '$STATUS_DELIVERED', updated_at = NOW() WHERE id = $PACKAGE_ID;"
            else
                docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET status = '$STATUS_DELIVERED' WHERE id = $PACKAGE_ID;"
            fi
        else
            # If no status column, just update updated_at
            if [ "$HAS_UPDATED_AT" = true ]; then
                docker exec -i ups-db psql -U postgres -d ups -c "UPDATE packages SET updated_at = NOW() WHERE id = $PACKAGE_ID;"
            fi
        fi
        
        # Update truck status back to IDLE
        if [ "$HAS_TRUCK_STATUS" = true ]; then
            docker exec -i ups-db psql -U postgres -d ups -c "UPDATE truck SET status = 'IDLE' WHERE id = $TRUCK_ID;"
        fi
        
        echo "   ✓ Package $PACKAGE_ID successfully delivered"
    fi
fi

echo ""
echo "Package lifecycle simulation complete!"
echo "You can view the detailed status in the UPS tracking page:"
echo "http://localhost:8080/tracking?trackingNumber=$PACKAGE_ID"
echo ""
echo "You can also see the status in the Amazon mock dashboard."
read -p "Press Enter when you're ready to continue..."

echo ""
echo "STEP 8: Show the UPS logs to explain what's happening behind the scenes"
echo "Running command to display relevant UPS logs..."
echo ""

if [[ -z "$TRUCK_ID" ]]; then
    echo "Package events for package $PACKAGE_ID:"
    docker logs ups-app | grep -i "package.*$PACKAGE_ID" | tail -15
else
    echo "Package and truck events for package $PACKAGE_ID:"
    docker logs ups-app | grep -i "package.*$PACKAGE_ID\|truck.*$TRUCK_ID" | tail -15
fi

echo ""
echo "UPS system architecture components visible in the logs:"
echo "1. World Communication:"
docker logs ups-app | grep -i "world.*response\|world.*handler" | tail -3
echo ""
echo "2. Amazon API Communication:"
docker logs ups-app | grep -i "amazon.*api\|amazon.*notification" | tail -3
echo ""
echo "3. Database Operations:"
docker logs ups-app | grep -i "database\|saving\|updating" | tail -3
echo ""
echo "These logs show how UPS communicates with:"
echo " - The World simulator (for geographic movement of trucks)"
echo " - Amazon services (for shipment requests and notifications)"
echo " - Internal database (for storing package and truck information)"
read -p "Press Enter to continue..."

echo ""
echo "STEP 9: Explain how UPS connects to other Amazon instances"
echo "The UPS system can connect to different Amazon instances by:"
echo "  - Setting the AMAZON_SERVICE_URL environment variable"
echo "  - Restarting the UPS container with the new URL"
echo "  - Validating the connection using the provided test scripts"
read -p "Press Enter to continue..."

echo ""
echo "Demonstration complete!"
echo "======================="
echo "You have successfully demonstrated the functionality of the UPS delivery system."
echo "To clean up the demo environment, you can run: docker stop mock-amazon && docker rm mock-amazon"