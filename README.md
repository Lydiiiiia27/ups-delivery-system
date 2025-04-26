# UPS Delivery System

This project implements a UPS delivery system that interfaces with Amazon's shipping API and a World simulator.

## Getting Started

### Prerequisites
- Java 17
- Maven
- PostgreSQL (optional, H2 in-memory database available for development)
- World simulator running

### Installation and Setup

1. Clone the repository
```bash
git clone https://gitlab.oit.duke.edu/jt454/erss-project-ys467-jt454.git
cd erss-project-ys467-jt454/ups-delivery-system
```

2. Build the project
```bash
mvn clean install
```

3. Run the application
```bash
mvn spring-boot:run
```

The server will start on port 8080 by default.

## API Documentation

### Create Shipment

#### Example Request

Save the JSON to a file (e.g., shipment.json):

```json
{
  "message_type": "CreateShipmentRequest",
  "seq_num": 101,
  "timestamp": "2023-04-10T15:00:00Z",
  "shipment_info": {
    "package_id": 1001,
    "warehouse_id": 10,
    "destination": {"x": 3, "y": 5},
    "ups_account_name": "testuser",
    "items": [
      {
        "product_id": 2001,
        "description": "Test Product",
        "count": 2
      }
    ]
  }
}
```

#### Using curl

Send the request using curl with a file:
```bash
curl -X POST -H "Content-Type: application/json" -d @shipment.json http://vcm-46935.vm.duke.edu:8080/api/createshipment
```

Or as a single command with the JSON inline:
```bash
curl -X POST -H "Content-Type: application/json" -d '{"message_type":"CreateShipmentRequest","seq_num":101,"timestamp":"2023-04-10T15:00:00Z","shipment_info":{"package_id":1001,"warehouse_id":10,"destination":{"x":3,"y":5},"ups_account_name":"testuser","items":[{"product_id":2001,"description":"Test Product","count":2}]}}' http://vcm-46935.vm.duke.edu:8080/api/createshipment
```

#### Using Postman

1. Download and install Postman from [postman.com](https://www.postman.com/downloads/)
2. Create a new request with:
   - Method: POST
   - URL: http://vcm-46935.vm.duke.edu:8080/api/createshipment
   - Body: Select "raw" and "JSON", then paste your JSON
3. Click "Send"

A successful response will look like:
```json
{
  "message_type": "CreateShipmentResponse",
  "seq_num": 201,
  "timestamp": "2023-04-10T15:01:00Z",
  "status": "ACCEPTED",
  "truck_id": 55
}
```

## Implementation Roadmap

### World Communication (Member 1)

- Ensure the World simulator is running in your environment
- Test the connection to the World simulator
- Implement handlers for World responses

### Amazon API & Web Interface (Member 2)

- Complete the ShipmentServiceImpl class
- Implement database operations for shipment tracking
- Connect the web UI with the backend

### Next Steps

- Complete actual implementation of ShipmentServiceImpl to:
  - Create a real package entry in the database
  - Associate it with a user (if the UPS account name exists)
  - Select an available truck
  - Communicate with the World simulator to send the truck for pickup

- Test the web interface to verify that:
  - New shipments appear in the dashboard for logged-in users
  - Package tracking works for the created packages

- Implement the remaining API endpoints for:
  - ChangeDestinationRequest
  - QueryShipmentStatusRequest
  - Handling incoming notifications from Amazon

## Project Status

The basic API communication with Amazon is functional. The current implementation:
- Has Spring Boot application running properly
- Includes AmazonApiController that handles incoming requests
- Has a mock ShipmentServiceImpl that generates responses
- Correctly configured API endpoints

Implementation is ongoing for database integration and World simulator communication.
