#!/bin/bash

# Start and setup the mock Amazon for UPS demo

echo "UPS Demo with Mock Amazon"
echo "========================="

# Ensure the mock-amazon directory exists with correct permissions
mkdir -p mock-amazon
touch mock-amazon/mock-amazon.py
chmod 644 mock-amazon/mock-amazon.py

# Check if docker is running
if ! docker ps -q > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker first."
    exit 1
fi

# Check if UPS containers are running
if ! docker ps -q --filter "name=ups-app" > /dev/null 2>&1; then
    echo "Error: UPS app container is not running. Please start the UPS system first."
    exit 1
fi

# Set the port for the mock Amazon to avoid conflicts
MOCK_PORT=8082

# Use the host.docker.internal hostname to access the host from inside Docker
# This works for Docker for Mac and Windows, and we'll handle Linux below
UPS_URL="http://host.docker.internal:8080"

# Check for Linux-specific Docker config
if [ "$(uname)" = "Linux" ]; then
    # On Linux, use the Docker Gateway IP to reach host
    DOCKER_GATEWAY_IP=$(docker network inspect bridge --format='{{range .IPAM.Config}}{{.Gateway}}{{end}}')
    if [ -n "$DOCKER_GATEWAY_IP" ]; then
        UPS_URL="http://$DOCKER_GATEWAY_IP:8080"
        echo "Using Docker gateway for UPS connection: $UPS_URL"
    else
        # Fallback - use the host network mode
        echo "Unable to determine Docker gateway IP, using host network mode"
        UPS_URL="http://172.17.0.1:8080"  # Common Docker gateway IP
    fi
fi

echo "Using UPS URL: $UPS_URL"

# Verify UPS service is accessible
echo "Verifying UPS service is accessible at ${UPS_URL}..."
curl -s --head --fail ${UPS_URL} > /dev/null || {
    echo "Warning: UPS service at ${UPS_URL} doesn't seem to be responding."
    echo "Please ensure UPS is running and accessible before proceeding."
    read -p "Continue anyway? (y/n): " continue_anyway
    if [[ ! "$continue_anyway" =~ ^[Yy]$ ]]; then
        echo "Aborting setup."
        exit 1
    fi
}

# Check if the mock Amazon container is already running
if docker ps -q --filter "name=mock-amazon" > /dev/null 2>&1; then
    echo "Stopping existing mock Amazon container..."
    docker stop mock-amazon
    docker rm mock-amazon
fi

echo "Starting enhanced mock Amazon for UPS demo..."

# Get the directory of the mock Amazon
MOCK_DIR="./mock-amazon"

# Make sure the directory exists
if [ ! -d "$MOCK_DIR" ]; then
    echo "Error: Mock Amazon directory not found. Please run from the project root."
    exit 1
fi

# Override the Flask app with our enhanced version
cat > mock-amazon/mock-amazon.py << 'EOF'
from flask import Flask, request, jsonify
import requests
import time
import threading
import json
import logging
import random
import os

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Configuration - get from environment variables or use defaults
UPS_URL = os.environ.get("UPS_URL", "http://host.docker.internal:8080")
PORT = int(os.environ.get("PORT", 8082))  # Default to 8082 if not set
WORLD_ID = 1  # Will be set when connected to world

logger.info(f"Starting with UPS_URL: {UPS_URL} on PORT: {PORT}")

# Track shipments and their status
shipments = {}

# Track valid warehouse IDs - hardcoded from database
valid_warehouses = [1, 2, 3, 999]  # These are the actual warehouses in the UPS world

# Demo mode settings
DEMO_MODE = True
AUTO_CREATE_SHIPMENTS = False  # Set to False to avoid automatic shipments
AUTO_SHIPMENT_INTERVAL = 30  # seconds between auto-created shipments

# Connect to the world simulator and get valid warehouse IDs
def connect_to_world():
    global WORLD_ID
    max_retries = 3
    retry_delay = 2
    
    for retry in range(max_retries):
        try:
            # This would normally connect to world simulator on port 23456
            # For our mock, we'll just use a hardcoded world ID
            WORLD_ID = 1  # Replace with actual world ID from your environment
            logger.info(f"Connected to world with ID: {WORLD_ID}")
            
            # Using hardcoded warehouse IDs (1, 2, 3, 999) from the UPS database
            logger.info(f"Using valid warehouse IDs: {valid_warehouses}")
                
            # If we got here without errors, break the retry loop
            return
                
        except Exception as e:
            logger.error(f"Failed to connect to world (attempt {retry+1}/{max_retries}): {e}")
            if retry < max_retries - 1:
                logger.info(f"Retrying in {retry_delay} seconds...")
                time.sleep(retry_delay)
                retry_delay *= 2  # Exponential backoff
            else:
                logger.error("Max retries exceeded. Using default configuration.")

# Get a random valid warehouse ID
def get_valid_warehouse():
    if not valid_warehouses:
        return 1  # Default to 1 if no valid warehouses
    return random.choice(valid_warehouses)

# Simulate sending a shipment request to UPS
@app.route('/api/test/send-shipment', methods=['POST'])
def send_shipment():
    data = request.json if request.is_json else {}
    
    # Generate package ID if not provided
    package_id = data.get('package_id', int(time.time()))
    warehouse_id = data.get('warehouse_id', get_valid_warehouse())
    dest_x = data.get('dest_x', 15)
    dest_y = data.get('dest_y', 25)
    
    result = send_shipment_internal({
        "package_id": package_id,
        "warehouse_id": warehouse_id,
        "dest_x": dest_x,
        "dest_y": dest_y
    })
    
    return jsonify(result)

# Handle truck arrival notification from UPS
@app.route('/api/ups/notifications/truck-arrived', methods=['POST'])
def truck_arrived():
    data = request.json
    logger.info(f"Received truck arrival notification: {data}")
    
    package_id = data.get('package_id')
    truck_id = data.get('truck_id')
    
    if package_id in shipments:
        shipments[package_id]["status"] = "TRUCK_ARRIVED"
        shipments[package_id]["truck_id"] = truck_id
        shipments[package_id]["lifecycle_stage"] = 2
        
        # Simulate loading the package
        threading.Thread(target=simulate_package_loading, 
                         args=(package_id, truck_id)).start()
    
    return jsonify({"success": True})

# Simulate package loading and sending notification to UPS
def simulate_package_loading(package_id, truck_id):
    # Wait a few seconds to simulate loading
    time.sleep(3)
    
    # Send package loaded notification to UPS
    loaded_notification = {
        "message_type": "PackageLoadedRequest",
        "seq_num": int(time.time()),
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "package_id": package_id,
        "truck_id": truck_id
    }
    
    try:
        logger.info(f"Sending package loaded notification for package {package_id}")
        response = requests.post(
            f"{UPS_URL}/api/packageloaded",
            json=loaded_notification,
            headers={"Content-Type": "application/json"}
        )
        
        if response.status_code == 200:
            shipments[package_id]["status"] = "LOADED"
            shipments[package_id]["lifecycle_stage"] = 3
            logger.info("Package loaded notification sent successfully")
        else:
            logger.error(f"Failed to send loaded notification: {response.text}")
    
    except Exception as e:
        logger.error(f"Exception while sending loaded notification: {e}")

# Handle delivery complete notification from UPS
@app.route('/api/ups/notifications/delivery-complete', methods=['POST'])
def delivery_complete():
    data = request.json
    logger.info(f"Received delivery complete notification: {data}")
    
    package_id = data.get('package_id')
    
    if package_id in shipments:
        shipments[package_id]["status"] = "DELIVERED"
        shipments[package_id]["lifecycle_stage"] = 4
        shipments[package_id]["delivered_at"] = time.time()
    
    return jsonify({"success": True})

# Handle status update notification from UPS
@app.route('/api/ups/notifications/status-update', methods=['POST'])
def status_update():
    data = request.json
    logger.info(f"Received status update notification: {data}")
    
    package_id = data.get('package_id')
    status = data.get('status')
    
    if package_id in shipments:
        shipments[package_id]["ups_status"] = status
    
    return jsonify({"success": True})

# Get all shipments (for testing)
@app.route('/api/test/shipments', methods=['GET'])
def get_shipments():
    return jsonify(shipments)

# Get warehouse status
@app.route('/api/test/warehouses', methods=['GET'])
def get_warehouses():
    return jsonify({
        "valid_warehouses": valid_warehouses,
        "count": len(valid_warehouses)
    })

# Status check endpoint
@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "UP",
        "connected_to_ups": UPS_URL,
        "world_id": WORLD_ID,
        "active_shipments": len(shipments),
        "valid_warehouses": valid_warehouses,
        "demo_mode": DEMO_MODE
    })

# Dashboard for demo
@app.route('/')
def dashboard():
    shipment_list = []
    for pid, data in shipments.items():
        status_emoji = "📦"  # Default - created
        if data.get("lifecycle_stage", 1) == 2:
            status_emoji = "🚚"  # Truck arrived
        elif data.get("lifecycle_stage", 1) == 3:
            status_emoji = "🔄"  # Loaded
        elif data.get("lifecycle_stage", 1) == 4:
            status_emoji = "✅"  # Delivered
            
        shipment_list.append({
            "id": pid,
            "status": data.get("status", "UNKNOWN"),
            "emoji": status_emoji,
            "created_at": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(data.get("created_at", 0))),
            "delivered_at": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(data.get("delivered_at", 0))) if "delivered_at" in data else "N/A"
        })
    
    shipment_list.sort(key=lambda x: x["id"], reverse=True)
    
    html = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <title>Amazon Mock - UPS Demo</title>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            body {{ font-family: Arial, sans-serif; margin: 20px; }}
            h1 {{ color: #232f3e; }}
            .container {{ max-width: 1000px; margin: 0 auto; }}
            .card {{ border: 1px solid #ddd; border-radius: 5px; padding: 15px; margin-bottom: 20px; }}
            .controls {{ margin-bottom: 20px; }}
            table {{ width: 100%; border-collapse: collapse; }}
            th, td {{ padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }}
            th {{ background-color: #f2f2f2; }}
            .btn {{ padding: 8px 12px; margin-right: 5px; background: #FF9900; border: none; border-radius: 3px; color: white; cursor: pointer; }}
            .status-created {{ color: blue; }}
            .status-truck_arrived {{ color: orange; }}
            .status-loaded {{ color: purple; }}
            .status-delivered {{ color: green; }}
            .warehouse-info {{ margin-top: 10px; background-color: #f8f8f8; padding: 10px; border-radius: 5px; }}
            .note {{ color: #666; font-size: 0.9em; margin-top: 5px; }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Amazon Mock - UPS Demo</h1>
            
            <div class="card">
                <h3>System Status</h3>
                <p>Connected to UPS: {UPS_URL}</p>
                <p>World ID: {WORLD_ID}</p>
                
                <h4>Valid Warehouse IDs:</h4>
                <div class="warehouse-info">
                    <table>
                        <tr>
                            <th>ID</th>
                            <th>Coordinates</th>
                        </tr>
                        <tr><td>1</td><td>(10, 10)</td></tr>
                        <tr><td>2</td><td>(20, 20)</td></tr>
                        <tr><td>3</td><td>(30, 30)</td></tr>
                        <tr><td>999</td><td>(20, 30)</td></tr>
                    </table>
                    <p class="note">Note: Only use these warehouse IDs when creating shipments to avoid errors.</p>
                </div>
                
                <p>Demo Mode: {'Enabled' if DEMO_MODE else 'Disabled'}</p>
            </div>
            
            <div class="card controls">
                <h3>Create New Shipment</h3>
                <form id="newShipmentForm">
                    <label>Package ID: <input type="number" id="packageId" value="{int(time.time())}" /></label>
                    <label>Warehouse ID: 
                        <select id="warehouseId">
                            <option value="1">1 - Coordinates (10, 10)</option>
                            <option value="2">2 - Coordinates (20, 20)</option>
                            <option value="3">3 - Coordinates (30, 30)</option>
                            <option value="999">999 - Coordinates (20, 30)</option>
                        </select>
                    </label>
                    <label>Destination X: <input type="number" id="destX" value="{random.randint(10, 50)}" /></label>
                    <label>Destination Y: <input type="number" id="destY" value="{random.randint(10, 50)}" /></label>
                    <button type="button" class="btn" onclick="createShipment()">Create Shipment</button>
                </form>
            </div>
            
            <div class="card">
                <h3>Shipments ({len(shipments)})</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Package ID</th>
                            <th>Status</th>
                            <th>Created</th>
                            <th>Delivered</th>
                        </tr>
                    </thead>
                    <tbody>
    """
    
    for s in shipment_list:
        status_class = f"status-{s['status'].lower()}"
        html += f"""
                        <tr>
                            <td>{s['id']}</td>
                            <td class="{status_class}">{s['emoji']} {s['status']}</td>
                            <td>{s['created_at']}</td>
                            <td>{s['delivered_at']}</td>
                        </tr>
        """
    
    html += """
                    </tbody>
                </table>
            </div>
        </div>
        
        <script>
            function createShipment() {
                const packageId = document.getElementById('packageId').value;
                const warehouseId = document.getElementById('warehouseId').value;
                const destX = document.getElementById('destX').value;
                const destY = document.getElementById('destY').value;
                
                fetch('/api/test/send-shipment', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        package_id: parseInt(packageId),
                        warehouse_id: parseInt(warehouseId),
                        dest_x: parseInt(destX),
                        dest_y: parseInt(destY)
                    }),
                })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        alert('Shipment created successfully!');
                        location.reload();
                    } else {
                        alert('Error: ' + data.error);
                    }
                })
                .catch(error => {
                    alert('Error: ' + error);
                });
            }
            
            // Refresh page every 15 seconds
            setTimeout(() => location.reload(), 15000);
        </script>
    </body>
    </html>
    """
    
    return html

# Start automatic shipment generation for demo mode
def auto_generate_shipments():
    while DEMO_MODE and AUTO_CREATE_SHIPMENTS:
        try:
            # Create a random shipment
            package_id = int(time.time())
            warehouse_id = get_valid_warehouse()
            dest_x = random.randint(10, 50)
            dest_y = random.randint(10, 50)
            
            # Use our own API endpoint instead of making an external request
            # This ensures we're using the correct host and port
            shipment_request = {
                "package_id": package_id,
                "warehouse_id": warehouse_id,
                "dest_x": dest_x,
                "dest_y": dest_y
            }
            
            # Call our own function directly instead of making an HTTP request
            send_shipment_internal(shipment_request)
            
            logger.info(f"Auto-generated shipment {package_id} to ({dest_x}, {dest_y})")
            
            # Wait for next auto-generation
            time.sleep(AUTO_SHIPMENT_INTERVAL)
        except Exception as e:
            logger.error(f"Error in auto-shipment generation: {e}")
            time.sleep(10)  # Wait a bit after an error

# Internal function to send shipment without HTTP request
def send_shipment_internal(data):
    package_id = data.get('package_id', int(time.time()))
    warehouse_id = data.get('warehouse_id', get_valid_warehouse())
    dest_x = data.get('dest_x', 15)
    dest_y = data.get('dest_y', 25)
    
    # Create shipment request
    shipment_request = {
        "message_type": "CreateShipmentRequest",
        "seq_num": int(time.time()),
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "shipment_info": {
            "package_id": package_id,
            "warehouse_id": warehouse_id,
            "destination": {"x": dest_x, "y": dest_y},
            "ups_account_name": "testuser",
            "items": [
                {
                    "product_id": 5001,
                    "description": "Test Product",
                    "count": 1
                }
            ]
        }
    }
    
    try:
        # Send request to UPS
        logger.info(f"Sending shipment request to UPS: {json.dumps(shipment_request)}")
        response = requests.post(
            f"{UPS_URL}/api/createshipment",
            json=shipment_request,
            headers={"Content-Type": "application/json"}
        )
        
        if response.status_code == 200:
            # Store shipment info
            shipments[package_id] = {
                "status": "CREATED",
                "response": response.json(),
                "created_at": time.time(),
                "lifecycle_stage": 1,  # For demo mode: 1=created, 2=truck_arrived, 3=loaded, 4=delivered
                "warehouse_id": warehouse_id,
                "destination": {"x": dest_x, "y": dest_y}
            }
            logger.info(f"Shipment created successfully: {response.json()}")
            return {"success": True, "package_id": package_id, "response": response.json()}
        else:
            logger.error(f"Failed to create shipment: {response.text}")
            return {"success": False, "error": f"UPS returned: {response.text}"}
    
    except Exception as e:
        logger.error(f"Exception while sending shipment: {e}")
        return {"success": False, "error": str(e)}

if __name__ == '__main__':
    # Connect to world simulator
    connect_to_world()
    
    # Start auto shipment generation in a background thread if enabled
    if DEMO_MODE and AUTO_CREATE_SHIPMENTS:
        threading.Thread(target=auto_generate_shipments, daemon=True).start()
    
    # Run the Flask app
    app.run(host='0.0.0.0', port=PORT, debug=True)
EOF

# Update the UPS_URL in the mock Amazon script to point to our actual UPS app
sed -i "s|UPS_URL = \"http://localhost:8080\"|UPS_URL = \"${UPS_URL}\"|g" mock-amazon/mock-amazon.py

# Also make sure UPS_URL is correctly updated in the entire script
sed -i "s|http://localhost:8080/api|${UPS_URL}/api|g" mock-amazon/mock-amazon.py

# Run the mock Amazon in Docker
echo "Building mock Amazon Docker image..."
cd "$MOCK_DIR" && docker build -t mock-amazon .

# Check if both UPS and mock-Amazon will be in the same Docker network
UPS_CONTAINER_NETWORK=$(docker inspect --format='{{range $net,$v := .NetworkSettings.Networks}}{{$net}} {{end}}' ups-app)
if [[ "$UPS_CONTAINER_NETWORK" == *"bridge"* ]]; then
    # If UPS is on the default bridge network, we can link directly to it by container name
    UPS_CONTAINER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ups-app)
    if [ -n "$UPS_CONTAINER_IP" ]; then
        echo "UPS container found on bridge network with IP: $UPS_CONTAINER_IP"
        UPS_URL="http://$UPS_CONTAINER_IP:8080"
    fi
fi

echo "Final UPS URL for container: $UPS_URL"

echo "Starting mock Amazon container on port $MOCK_PORT..."
# Use host network mode for easier communication with UPS
docker run -d --name mock-amazon --network host -e UPS_URL="$UPS_URL" -e PORT="$MOCK_PORT" mock-amazon

# Wait for the container to start
echo "Waiting for mock Amazon to start..."
sleep 5

echo "Mock Amazon is running!"
echo "Access the demo dashboard at: http://localhost:$MOCK_PORT"
echo "API endpoints available at: http://localhost:$MOCK_PORT/api/test/*"
echo ""
echo "Demonstration flow:"
echo "1. Access the dashboard and create a shipment manually or wait for auto-generated shipments"
echo "2. Watch the shipment progress through the UPS system"
echo "3. Check UPS tracking at: http://localhost:8080/tracking?trackingNumber=<package_id>"
echo ""
echo "Press Ctrl+C to stop the demo and clean up..."

# Keep script running until user presses Ctrl+C
trap "echo 'Stopping mock Amazon...'; docker stop mock-amazon && docker rm mock-amazon; echo 'Demo stopped.'" EXIT

# Monitor the logs but don't exit immediately if the container exits
while docker ps -q --filter "name=mock-amazon" > /dev/null 2>&1; do
    docker logs --tail 10 --follow mock-amazon || true
    sleep 2
done

echo "Mock Amazon container stopped unexpectedly. Please check for errors."