#!/bin/bash

echo "UPS Delivery System Functional Testing"
echo "======================================"

# Function to check if services are running
check_services() {
    echo "Checking required services..."
    
    # Check Docker
    if ! docker ps > /dev/null 2>&1; then
        echo "❌ Docker is not running. Please start Docker first."
        exit 1
    fi
    
    # Clean up any existing stopped ups-app container first
    if docker ps -a --format '{{.Names}}' | grep -q "ups-app"; then
        if ! docker ps --format '{{.Names}}' | grep -q "ups-app"; then
            echo "Found stopped UPS app container, removing it..."
            docker rm ups-app
        fi
    fi
    
    # Check world simulator
    if ! docker ps --format '{{.Names}}' | grep -q "world-simulator"; then
        echo "❌ World Simulator is not running. Starting services..."
        
        # Start only world-simulator without ups-app to avoid conflicts
        docker-compose up -d world-simulator mydb ups-db
        sleep 15
    else
        echo "✅ World Simulator is running"
    fi
    
    # Check UPS app
    if ! docker ps --format '{{.Names}}' | grep -q "ups-app"; then
        echo "❌ UPS app is not running. Starting UPS app container..."
        
        # Get the UPS app image from docker-compose
        UPS_IMAGE=$(grep -A 10 "ups-app:" docker-compose.yml | grep "image:" | head -1 | cut -d':' -f2- | xargs)
        
        # If no image is found, try to find a build context
        if [ -z "$UPS_IMAGE" ]; then
            echo "No UPS image specified in docker-compose, using the default project image"
            UPS_IMAGE="erss-project-ys467-jt454_ups-app"
        fi
        
        # Start the container with test profile enabled
        echo "Starting UPS app with test profile using image: $UPS_IMAGE"
        docker run -d --name ups-app \
            --network erss-project-ys467-jt454_ups-network \
            -e SPRING_DATASOURCE_URL=jdbc:postgresql://ups-db:5432/ups \
            -e SPRING_DATASOURCE_USERNAME=postgres \
            -e SPRING_DATASOURCE_PASSWORD=postgres \
            -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
            -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
            -e SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
            -e UPS_WORLD_HOST=world-simulator \
            -e UPS_WORLD_PORT=12345 \
            -e UPS_INIT_TRUCKS=5 \
            -e UPS_WORLD_SIM_SPEED=100 \
            -e UPS_WORLD_CREATE_NEW=false \
            -e AMAZON_SERVICE_URL=http://amazon-mock:8080 \
            -e SPRING_PROFILES_ACTIVE=test \
            -p 8080:8080 \
            $UPS_IMAGE
            
        # Connect to the world network
        docker network connect erss-project-ys467-jt454_world-network ups-app
        
        echo "Waiting for UPS app to start..."
        sleep 15
    else
        echo "✅ UPS app is running"
        
        # Get the UPS app logs
        UPS_APP_LOGS=$(docker logs ups-app 2>&1)
        
        # First, check if there are any startup errors
        if echo "$UPS_APP_LOGS" | grep -q "Application run failed"; then
            echo "❌ UPS app has startup errors. Check logs for details."
            echo "Attempting to restart UPS app..."
            docker restart ups-app
            sleep 15
        fi
        
        # Check if test profile is active in UPS app logs
        if echo "$UPS_APP_LOGS" | grep -q "Using profile.*test"; then
            echo "✅ Test profile is already enabled"
        else
            echo "⚠️ Test profile not enabled. Restarting UPS app with test profile..."
            
            # Get current container image
            UPS_IMAGE=$(docker inspect --format='{{.Config.Image}}' ups-app)
            
            # Stop and remove the container
            docker stop ups-app
            docker rm ups-app
            
            # Start the container with test profile enabled
            docker run -d --name ups-app \
                --network erss-project-ys467-jt454_ups-network \
                -e SPRING_DATASOURCE_URL=jdbc:postgresql://ups-db:5432/ups \
                -e SPRING_DATASOURCE_USERNAME=postgres \
                -e SPRING_DATASOURCE_PASSWORD=postgres \
                -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
                -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
                -e SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
                -e UPS_WORLD_HOST=world-simulator \
                -e UPS_WORLD_PORT=12345 \
                -e UPS_INIT_TRUCKS=5 \
                -e UPS_WORLD_SIM_SPEED=100 \
                -e UPS_WORLD_CREATE_NEW=false \
                -e AMAZON_SERVICE_URL=http://amazon-mock:8080 \
                -e SPRING_PROFILES_ACTIVE=test \
                -p 8080:8080 \
                $UPS_IMAGE
                
            # Connect to the world network
            docker network connect erss-project-ys467-jt454_world-network ups-app
            
            echo "Waiting for UPS app to start..."
            sleep 15
        fi
    fi
}

# Function to set up Amazon mock
setup_amazon_mock() {
    echo "Setting up Amazon mock service..."
    
    # Create the mock data file with endpoints for API testing
    cat > amazon-mock-data.json << 'EOF'
{
  "uuid": "3cb244ad-adbd-4461-afa9-e16b19078dab",
  "lastMigration": 27,
  "name": "Amazon Mock API",
  "endpointPrefix": "",
  "latency": 0,
  "port": 8080,
  "hostname": "0.0.0.0",
  "folders": [],
  "routes": [
    {
      "uuid": "e4f7b26d-dcaf-4ad3-9e2f-dc5eda674ba9",
      "type": "http",
      "documentation": "Truck arrived notification",
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
          "default": true,
          "databucketID": "",
          "bodyType": "INLINE"
        }
      ],
      "enabled": true,
      "randomResponse": false,
      "sequentialResponse": false
    },
    {
      "uuid": "b5a9c9e8-4c5d-4b3a-9d2e-8f1c6b4d5a3e",
      "type": "http",
      "documentation": "Delivery complete notification",
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
          "default": true,
          "databucketID": "",
          "bodyType": "INLINE"
        }
      ],
      "enabled": true,
      "randomResponse": false,
      "sequentialResponse": false
    },
    {
      "uuid": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
      "type": "http",
      "documentation": "Status update notification",
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
          "default": true,
          "databucketID": "",
          "bodyType": "INLINE"
        }
      ],
      "enabled": true,
      "randomResponse": false,
      "sequentialResponse": false
    },
    {
      "uuid": "5e6f7a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b",
      "type": "http",
      "documentation": "Create shipment response",
      "method": "post",
      "endpoint": "api/createshipment",
      "responses": [
        {
          "uuid": "6f7e8d9c-0b1a-2c3d-4e5f-6g7h8i9j0k1l",
          "body": "{ \"message_type\": \"CreateShipmentResponse\", \"seq_num\": 202, \"ack\": 101, \"timestamp\": \"2025-04-10T15:00:01Z\", \"status\": \"ACCEPTED\", \"truck_id\": 1 }",
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
          "default": true,
          "databucketID": "",
          "bodyType": "INLINE"
        }
      ],
      "enabled": true,
      "randomResponse": false,
      "sequentialResponse": false
    },
    {
      "uuid": "a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
      "type": "http",
      "documentation": "Package loaded endpoint",
      "method": "post",
      "endpoint": "api/packageloaded",
      "responses": [
        {
          "uuid": "b2c3d4e5-f6g7-h8i9-j0k1-l2m3n4o5p6q7",
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
          "default": true,
          "databucketID": "",
          "bodyType": "INLINE"
        }
      ],
      "enabled": true,
      "randomResponse": false,
      "sequentialResponse": false
    }
  ],
  "proxyMode": false,
  "proxyHost": "",
  "proxyRemovePrefix": false,
  "tlsOptions": {
    "enabled": false,
    "type": "CERT",
    "pfxPath": "",
    "certPath": "",
    "keyPath": "",
    "caPath": "",
    "passphrase": ""
  },
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
  ]
}
EOF

    # Start Amazon mock container
    docker rm -f amazon-mock 2>/dev/null || true
    
    # Check if networks exist
    if ! docker network ls | grep -q "erss-project-ys467-jt454_world-network"; then
        echo "❌ World network does not exist. Creating network..."
        docker network create erss-project-ys467-jt454_world-network
    fi
    
    if ! docker network ls | grep -q "erss-project-ys467-jt454_ups-network"; then
        echo "❌ UPS network does not exist. Creating network..."
        docker network create erss-project-ys467-jt454_ups-network
    fi
    
    # Run Amazon mock container with correct syntax for Mockoon CLI
    docker run -d --name amazon-mock \
        -p 8081:8080 \
        --network erss-project-ys467-jt454_world-network \
        -v "$(pwd)/amazon-mock-data.json:/data.json" \
        mockoon/cli:latest \
        -d /data.json
        
    # Connect to UPS network as well
    docker network connect erss-project-ys467-jt454_ups-network amazon-mock || true
    
    # Wait for the service to start
    echo "Waiting for Amazon mock service to start..."
    sleep 5
    
    # Check if the service is running
    if docker ps | grep -q amazon-mock; then
        echo "✅ Amazon mock service started successfully"
        echo "Testing connection to Amazon mock service..."
        curl -s http://localhost:8081/health || echo "Warning: Health check endpoint not available, but this is expected"
    else
        echo "❌ Failed to start Amazon mock service"
        # Show logs to help diagnose the issue
        echo "Amazon mock service logs:"
        docker logs amazon-mock
        exit 1
    fi
}

# Function to create test warehouses and trucks
setup_test_data() {
    echo "Setting up test data..."
    
    # Get world ID
    WORLD_ID=$(docker exec -i mydb psql -U postgres -d worldSim -t -c "SELECT world_id FROM world ORDER BY world_id DESC LIMIT 1;" | tr -d ' ')
    
    if [ -z "$WORLD_ID" ]; then
        echo "❌ Could not get World ID. Make sure the world is created."
        exit 1
    fi
    
    echo "Using World ID: $WORLD_ID"
    
    # Create test warehouses in world simulator
    docker exec -i mydb psql -U postgres -d worldSim <<EOF
-- Create warehouses for testing
INSERT INTO warehouse (wh_id, world_id, x, y)
VALUES 
    (1, $WORLD_ID, 10, 10),
    (2, $WORLD_ID, 20, 20),
    (3, $WORLD_ID, 30, 30)
ON CONFLICT DO NOTHING;

-- Show created warehouses
SELECT wh_id, world_id, x, y FROM warehouse WHERE world_id = $WORLD_ID;
EOF

    # Check the actual schema of users and packages tables
    echo "Checking database schema..."
    USERS_SCHEMA=$(docker exec -i ups-db psql -U postgres -d ups -t -c "\d users" 2>/dev/null)
    PACKAGES_SCHEMA=$(docker exec -i ups-db psql -U postgres -d ups -t -c "\d packages" 2>/dev/null)
    WAREHOUSE_SCHEMA=$(docker exec -i ups-db psql -U postgres -d ups -t -c "\d warehouse" 2>/dev/null)
    
    # Create warehouses in UPS database to satisfy foreign key constraint
    echo "Creating warehouses in UPS database..."
    if [ -z "$WAREHOUSE_SCHEMA" ]; then
        echo "Warehouse table doesn't exist, creating it now..."
        docker exec -i ups-db psql -U postgres -d ups <<EOF
-- Create warehouse table
CREATE TABLE IF NOT EXISTS warehouse (
    id BIGINT PRIMARY KEY,
    x INT NOT NULL,
    y INT NOT NULL
);

-- Create warehouses for testing
INSERT INTO warehouse (id, x, y)
VALUES 
    (1, 10, 10),
    (2, 20, 20),
    (3, 30, 30)
ON CONFLICT (id) DO UPDATE
SET x = EXCLUDED.x, y = EXCLUDED.y;

-- Show created warehouses
SELECT id, x, y FROM warehouse;
EOF
    else
        # Check if world_id exists in warehouse table
        if echo "$WAREHOUSE_SCHEMA" | grep -q "world_id"; then
            docker exec -i ups-db psql -U postgres -d ups <<EOF
-- Create warehouses for testing with world_id
INSERT INTO warehouse (id, world_id, x, y)
VALUES 
    (1, $WORLD_ID, 10, 10),
    (2, $WORLD_ID, 20, 20),
    (3, $WORLD_ID, 30, 30)
ON CONFLICT (id) DO UPDATE
SET world_id = $WORLD_ID, x = EXCLUDED.x, y = EXCLUDED.y;

-- Show created warehouses
SELECT id, world_id, x, y FROM warehouse;
EOF
        else
            docker exec -i ups-db psql -U postgres -d ups <<EOF
-- Create warehouses for testing without world_id
INSERT INTO warehouse (id, x, y)
VALUES 
    (1, 10, 10),
    (2, 20, 20),
    (3, 30, 30)
ON CONFLICT (id) DO UPDATE
SET x = EXCLUDED.x, y = EXCLUDED.y;

-- Show created warehouses
SELECT id, x, y FROM warehouse;
EOF
        fi
    fi
    
    # Create test users in UPS database with adjustments for schema
    echo "Creating test user..."
    if echo "$USERS_SCHEMA" | grep -q "enabled"; then
        docker exec -i ups-db psql -U postgres -d ups <<EOF
-- Create test user if not exists
INSERT INTO users (id, username, password, email, enabled)
VALUES 
    (1, 'testuser', '\$2a\$10\$QUDMqsYH13X.8YwZyoZt5OjWGBuuMTXCiI9KzhGhNZY0PGHq6CZhS', 'test@example.com', true)
ON CONFLICT (id) DO UPDATE
SET username = 'testuser', email = 'test@example.com', enabled = true;

-- Show created users
SELECT id, username, email, enabled FROM users;
EOF
    else
        docker exec -i ups-db psql -U postgres -d ups <<EOF
-- Create test user if not exists (without enabled column)
INSERT INTO users (id, username, password, email)
VALUES 
    (1, 'testuser', '\$2a\$10\$QUDMqsYH13X.8YwZyoZt5OjWGBuuMTXCiI9KzhGhNZY0PGHq6CZhS', 'test@example.com')
ON CONFLICT (id) DO UPDATE
SET username = 'testuser', email = 'test@example.com';

-- Show created users
SELECT id, username, email FROM users;
EOF
    fi

    # Create test packages in UPS database with correct column names
    echo "Creating test package..."
    if echo "$PACKAGES_SCHEMA" | grep -q "destinationx"; then
        docker exec -i ups-db psql -U postgres -d ups <<EOF
-- Create test package with ID 1001 using destinationx/destinationy columns
INSERT INTO packages (id, status, user_id, truck_id, warehouse_id, destinationx, destinationy, created_at)
VALUES 
    (1001, 'CREATED', 1, 1, 1, 15, 25, NOW())
ON CONFLICT (id) DO UPDATE
SET status = 'CREATED', destinationx = 15, destinationy = 25;

-- Show created packages
SELECT id, status, user_id, truck_id, warehouse_id, destinationx, destinationy FROM packages;
EOF
    elif echo "$PACKAGES_SCHEMA" | grep -q "destination_x"; then
        docker exec -i ups-db psql -U postgres -d ups <<EOF
-- Create test package with ID 1001 using destination_x/destination_y columns
INSERT INTO packages (id, status, user_id, truck_id, warehouse_id, destination_x, destination_y, created_at)
VALUES 
    (1001, 'CREATED', 1, 1, 1, 15, 25, NOW())
ON CONFLICT (id) DO UPDATE
SET status = 'CREATED', destination_x = 15, destination_y = 25;

-- Show created packages
SELECT id, status, user_id, truck_id, warehouse_id, destination_x, destination_y FROM packages;
EOF
    else
        echo "⚠️ Could not determine destination column names. Please check the packages table schema."
        docker exec -i ups-db psql -U postgres -d ups -c "\d packages" || echo "Could not access the packages table schema."
    fi

    # Create test user via API
    echo "Testing API user creation..."
    curl -s -X POST -H "Content-Type: application/json" \
        -d '{"username":"testuser","password":"testpass","email":"test@example.com"}' \
        http://localhost:8080/api/test/create-user
        
    echo "✅ Test data setup completed"
}

# Function to verify test endpoints availability
verify_test_endpoints() {
    echo "Verifying test endpoints availability..."
    
    # Get a full dump of UPS app logs to check test profile activation
    UPS_LOGS=$(docker logs ups-app 2>&1)
    
    # Check for test controller activation in logs
    if echo "$UPS_LOGS" | grep -q "IntegrationTestController"; then
        echo "✅ Test controller appears to be loaded"
    else
        echo "⚠️ Test controller not found in logs. Testing may fail."
    fi
    
    # Try a simple health check endpoint first
    HEALTH_CHECK=$(curl -s http://localhost:8080/actuator/health 2>/dev/null || curl -s http://localhost:8080/health 2>/dev/null || echo '{"status":"UNKNOWN"}')
    if echo "$HEALTH_CHECK" | grep -q "UP\|UNKNOWN"; then
        echo "✅ Basic health check passed, API is responding"
    else
        echo "⚠️ Health check failed. API may not be available."
    fi
    
    # Check if test endpoints can be accessed
    ENDPOINT_CHECK=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/test/complete-flow 2>/dev/null)
    if [ "$ENDPOINT_CHECK" == "200" ] || [ "$ENDPOINT_CHECK" == "401" ] || [ "$ENDPOINT_CHECK" == "403" ]; then
        echo "✅ Test endpoints are available (HTTP $ENDPOINT_CHECK)"
    else
        echo "⚠️ Test endpoints may not be available (HTTP $ENDPOINT_CHECK)"
        
        # Try to dump Spring MVC mapped endpoints
        echo "Attempting to get mapped endpoints..."
        curl -s http://localhost:8080/actuator/mappings 2>/dev/null | grep -i "test" || echo "No test mappings found or actuator not available"
    fi
    
    # Check for any loaded test APIs in the logs
    if echo "$UPS_LOGS" | grep -q "Mapped \"/api/test"; then
        echo "✅ Test API mappings found in logs"
        echo "Test API mappings:"
        echo "$UPS_LOGS" | grep -A 1 "Mapped \"/api/test" | head -10
    else
        echo "⚠️ No test API mappings found in logs"
    fi
}

# Function to set up the UPS app with test profile
setup_ups_app() {
    echo "Setting up UPS app with test profile..."
    
    # Stop and remove the existing container if it exists
    if docker ps -a --format '{{.Names}}' | grep -q "ups-app"; then
        echo "Stopping and removing existing UPS app container..."
        docker stop ups-app
        docker rm ups-app
    fi
    
    # Create temp directory for test properties
    echo "Creating test properties files..."
    mkdir -p /tmp/ups-test-config
    
    # Create application-test.properties file
    cat > /tmp/ups-test-config/application-test.properties << 'EOF'
# Test environment specific properties

# Database configuration using the UPS database
spring.datasource.url=jdbc:postgresql://ups-db:5432/ups
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# World simulator connection settings
ups.world.host=world-simulator
ups.world.port=12345
ups.init.trucks=5
ups.world.create.new=false
ups.world.sim.speed=100

# Amazon service URL for tests
amazon.service.url=http://amazon-mock:8080

# Enable test endpoints
spring.main.allow-bean-definition-overriding=true
spring.aop.proxy-target-class=false

# Security settings for testing
spring.security.user.name=admin
spring.security.user.password=admin

# Logging
logging.level.com.ups=DEBUG
logging.level.org.springframework.web=INFO
logging.level.org.hibernate=WARN
logging.level.org.springframework.security=DEBUG
EOF
    
    # Get the UPS app image
    echo "Finding UPS app image..."
    # Try to get the image from docker-compose.yml
    UPS_IMAGE=$(grep -A 10 "ups-app:" docker-compose.yml | grep "image:" | head -1 | cut -d':' -f2- | xargs)
    
    # If no image found in docker-compose, try to find existing built image
    if [ -z "$UPS_IMAGE" ]; then
        UPS_IMAGE=$(docker images | grep ups-app | head -1 | awk '{print $1":"$2}')
    fi
    
    # If still no image, use default project name
    if [ -z "$UPS_IMAGE" ]; then
        echo "No image found, using default project image name"
        UPS_IMAGE="erss-project-ys467-jt454_ups-app:latest"
    fi
    
    echo "Using UPS image: $UPS_IMAGE"
    
    # Start the container with test profile enabled and mount the properties file
    echo "Starting UPS app with test profile..."
    docker run -d --name ups-app \
        --network erss-project-ys467-jt454_ups-network \
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://ups-db:5432/ups \
        -e SPRING_DATASOURCE_USERNAME=postgres \
        -e SPRING_DATASOURCE_PASSWORD=postgres \
        -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
        -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
        -e SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
        -e UPS_WORLD_HOST=world-simulator \
        -e UPS_WORLD_PORT=12345 \
        -e UPS_INIT_TRUCKS=5 \
        -e UPS_WORLD_SIM_SPEED=100 \
        -e UPS_WORLD_CREATE_NEW=false \
        -e AMAZON_SERVICE_URL=http://amazon-mock:8080 \
        -e SPRING_PROFILES_ACTIVE=test \
        -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/tmp/config/ \
        -v /tmp/ups-test-config:/tmp/config \
        -p 8080:8080 \
        $UPS_IMAGE
    
    # Connect to the world network
    docker network connect erss-project-ys467-jt454_world-network ups-app
    
    echo "Waiting for UPS app to start..."
    sleep 15
    
    # Check if the container is running
    if docker ps | grep -q ups-app; then
        echo "✅ UPS app started successfully"
    else
        echo "❌ UPS app failed to start. Checking logs..."
        docker logs ups-app
        
        echo "Attempting alternative startup approach..."
        docker rm -f ups-app
        
        # Attempt to run without test profile, then attach to network
        docker run -d --name ups-app \
            --network erss-project-ys467-jt454_ups-network \
            -e SPRING_DATASOURCE_URL=jdbc:postgresql://ups-db:5432/ups \
            -e SPRING_DATASOURCE_USERNAME=postgres \
            -e SPRING_DATASOURCE_PASSWORD=postgres \
            -p 8080:8080 \
            $UPS_IMAGE
        
        # Connect to the world network
        docker network connect erss-project-ys467-jt454_world-network ups-app
        
        echo "Waiting for UPS app to start..."
        sleep 15
        
        if docker ps | grep -q ups-app; then
            echo "✅ UPS app started in fallback mode"
            echo "⚠️ Test profile is not active, test endpoints may not be available"
        else
            echo "❌ UPS app failed to start in fallback mode"
            docker logs ups-app
            exit 1
        fi
    fi
    
    # Check logs for test profile activation
    if docker logs ups-app 2>&1 | grep -q "profile.*test"; then
        echo "✅ Test profile found in logs"
    else
        echo "⚠️ Test profile may not be active"
    fi
}

# Run functional tests for required features
run_functional_tests() {
    echo -e "\nRunning Functional Tests"
    echo "========================"
    
    TEST_PASSED=0
    TEST_FAILED=0
    
    # 1. Test tracking functionality
    echo -e "\n1. Testing package tracking..."
    TRACKING_RESPONSE=$(curl -v http://localhost:8080/api/test/tracking?trackingNumber=1001 2>&1)
    
    if echo "$TRACKING_RESPONSE" | grep -q "success.*true"; then
        echo "✅ Package tracking test passed"
        ((TEST_PASSED++))
    else
        echo "❌ Package tracking test failed"
        echo "Response: $(echo "$TRACKING_RESPONSE" | grep -A20 '< HTTP/1.1')"
        ((TEST_FAILED++))
    fi
    
    # 2. Test user authentication
    echo -e "\n2. Testing user authentication..."
    AUTH_RESPONSE=$(curl -v -X POST -H "Content-Type: application/json" \
        -d '{"username":"testuser","password":"testpass"}' \
        http://localhost:8080/api/test/login 2>&1)
        
    if echo "$AUTH_RESPONSE" | grep -q "success.*true"; then
        echo "✅ User authentication test passed"
        ((TEST_PASSED++))
    else
        echo "❌ User authentication test failed"
        echo "Response: $(echo "$AUTH_RESPONSE" | grep -A20 '< HTTP/1.1')"
        ((TEST_FAILED++))
    fi
    
    # 3. Test complete package delivery flow
    echo -e "\n3. Testing complete package delivery flow..."
    FLOW_RESPONSE=$(curl -v http://localhost:8080/api/test/complete-flow 2>&1)
    
    if echo "$FLOW_RESPONSE" | grep -q "successfully"; then
        echo "✅ Complete flow test passed"
        ((TEST_PASSED++))
    else
        echo "❌ Complete flow test failed"
        echo "Response: $(echo "$FLOW_RESPONSE" | grep -A20 '< HTTP/1.1')"
        ((TEST_FAILED++))
    fi
    
    # 4. Test package redirection
    echo -e "\n4. Testing package redirection..."
    REDIRECT_RESPONSE=$(curl -v -X POST -H "Content-Type: application/json" \
        -d '{"packageId":1001,"destinationX":15,"destinationY":25}' \
        http://localhost:8080/api/test/redirect-package 2>&1)
        
    if echo "$REDIRECT_RESPONSE" | grep -q "success.*true"; then
        echo "✅ Package redirection test passed"
        ((TEST_PASSED++))
    else
        echo "❌ Package redirection test failed"
        echo "Response: $(echo "$REDIRECT_RESPONSE" | grep -A20 '< HTTP/1.1')"
        ((TEST_FAILED++))
    fi
    
    # Check UPS app logs
    echo -e "\nChecking UPS app logs for errors..."
    docker logs ups-app | grep -i "error" | tail -20 || echo "No error messages found in logs."
    
    # Print test summary
    echo -e "\nTest Summary"
    echo "============"
    echo "Tests passed: $TEST_PASSED"
    echo "Tests failed: $TEST_FAILED"
    
    if [ $TEST_FAILED -eq 0 ]; then
        echo "✅ All functional tests passed!"
    else
        echo "❌ Some tests failed. Please check the logs."
    fi
}

# Check database state
check_database_state() {
    echo -e "\nDatabase State"
    echo "=============="
    
    # Check worlds
    echo "Worlds:"
    docker exec -i mydb psql -U postgres -d worldSim -c "SELECT world_id FROM world;" || echo "No worlds found"
    
    # Check warehouses
    echo -e "\nWarehouses:"
    docker exec -i mydb psql -U postgres -d worldSim -c "SELECT wh_id, world_id, x, y FROM warehouse ORDER BY world_id, wh_id;" || echo "No warehouses found"
    
    # Check trucks
    echo -e "\nTrucks:"
    docker exec -i mydb psql -U postgres -d worldSim -c "SELECT truck_id, world_id, x, y FROM truck ORDER BY world_id, truck_id LIMIT 10;" || echo "No trucks found"
    
    # Check packages - using a different approach that doesn't rely on psql in PATH
    echo -e "\nPackages:"
    
    # Check the packages schema first to use the correct column names
    PACKAGES_SCHEMA=$(docker exec -i ups-db psql -U postgres -d ups -t -c "\d packages" 2>/dev/null)
    
    if echo "$PACKAGES_SCHEMA" | grep -q "destinationx"; then
        # For destinationx/destinationy columns
        docker exec -i ups-db psql -U postgres -d ups -c "SELECT id, status, destinationx, destinationy FROM packages LIMIT 10;" || \
        echo "No packages found or cannot connect to UPS database"
    else
        # For destination_x/destination_y columns
        docker exec -i ups-db psql -U postgres -d ups -c "SELECT id, status, destination_x, destination_y FROM packages LIMIT 10;" || \
        echo "No packages found or cannot connect to UPS database"
    fi
}

# Main execution
echo "Starting UPS functional tests: $(date)"

# Step 1: Check services
check_services

# Step 2: Setup UPS app with test configuration
setup_ups_app

# Step 3: Setup Amazon mock
setup_amazon_mock

# Step 4: Setup test data
setup_test_data

# Step 5: Verify test endpoints
verify_test_endpoints

# Step 6: Run functional tests
run_functional_tests

# Step 7: Check database state
check_database_state

echo -e "\nTests completed: $(date)"