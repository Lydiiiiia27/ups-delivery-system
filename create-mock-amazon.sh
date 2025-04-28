#!/bin/bash

# Create a directory for our mock Amazon service
mkdir -p mock-amazon
cd mock-amazon

# Create a simple mock Amazon service using Python and Flask
cat > mock-amazon.py << 'EOF'
from flask import Flask, request, jsonify
import requests
import time
import threading
import json
import logging

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Configuration - replace with your actual UPS hostname/IP
UPS_URL = "http://localhost:8080"
WORLD_ID = None  # Will be set when connected to world

# Track shipments and their status
shipments = {}

# Connect to the world simulator
def connect_to_world():
    global WORLD_ID
    try:
        # This would normally connect to world simulator on port 23456
        # For our mock, we'll just use a hardcoded world ID
        WORLD_ID = 1  # Replace with actual world ID from your environment
        logger.info(f"Connected to world with ID: {WORLD_ID}")
    except Exception as e:
        logger.error(f"Failed to connect to world: {e}")

# Simulate sending a shipment request to UPS
@app.route('/api/test/send-shipment', methods=['POST'])
def send_shipment():
    data = request.json if request.is_json else {}
    
    # Generate package ID if not provided
    package_id = data.get('package_id', int(time.time()))
    warehouse_id = data.get('warehouse_id', 1)
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
                "response": response.json()
            }
            logger.info(f"Shipment created successfully: {response.json()}")
            return jsonify({"success": True, "package_id": package_id, "response": response.json()})
        else:
            logger.error(f"Failed to create shipment: {response.text}")
            return jsonify({"success": False, "error": f"UPS returned: {response.text}"})
    
    except Exception as e:
        logger.error(f"Exception while sending shipment: {e}")
        return jsonify({"success": False, "error": str(e)})

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

if __name__ == '__main__':
    # Connect to world simulator
    connect_to_world()
    
    # Run the Flask app
    app.run(host='0.0.0.0', port=8080, debug=True)
EOF

# Create Dockerfile for mock Amazon service
cat > Dockerfile << 'EOF'
FROM python:3.10-slim

WORKDIR /app
COPY . /app/

RUN pip install flask requests

ENV FLASK_APP=mock-amazon.py
EXPOSE 8080

CMD ["python", "mock-amazon.py"]
EOF

# Create Docker Compose file for easy startup
cat > docker-compose.yml << 'EOF'
version: '3'
services:
  mock-amazon:
    build: .
    container_name: mock-amazon
    ports:
      - "8082:8080"
    networks:
      - ups-network
      - world-network

networks:
  ups-network:
    external: true
    name: erss-project-ys467-jt454_ups-network
  world-network:
    external: true
    name: erss-project-ys467-jt454_world-network
EOF

echo "Mock Amazon service created!"