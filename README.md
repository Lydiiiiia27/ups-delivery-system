# UPS Delivery System

## Overview

This project implements a delivery tracking system that interfaces with Amazon and a World simulator. It allows for real-time tracking of packages from warehouse pickup to final delivery.

## System Architecture

The UPS Delivery System follows a microservices architecture with several key components:

### Core Components

1. **Web Interface**
   - User management system
   - Package tracking interface
   - Dashboard for authenticated users
   - Redirect package functionality

2. **API Layer**
   - RESTful endpoints for Amazon integration
   - World Simulator communication
   - Message tracking and acknowledgment system

3. **Business Logic**
   - Package lifecycle management
   - Truck assignment and coordination
   - Warehouse integration
   - Delivery optimization

4. **Data Layer**
   - PostgreSQL database for permanent storage
   - Entity models (Packages, Trucks, Users, etc.)
   - Transaction management

5. **Integration Services**
   - Amazon notification service
   - World Simulator connector
   - Response handlers

## Tech Stack

- **Backend**: Java 17 with Spring Boot 3.1.x
- **Database**: PostgreSQL for production, H2 for testing
- **ORM**: Hibernate via Spring Data JPA
- **Frontend**: Thymeleaf templates with Bootstrap 5
- **Communication**: Google Protocol Buffers for World Simulator
- **Security**: Spring Security for authentication and authorization
- **API**: RESTful JSON API via Spring MVC
- **Build Tool**: Maven
- **Containerization**: Docker and Docker Compose
- **Testing**: JUnit 5, Mockito

## Core Entities

- **Package**: Represents a shipment with status, destination, and items
- **Truck**: Represents a delivery vehicle with location and status
- **User**: Represents a UPS account holder
- **Warehouse**: Represents product storage locations
- **PackageItem**: Represents items within a package

## Package Lifecycle

1. **CREATED**: Package is initially created from Amazon request
2. **ASSIGNED**: Package is assigned to a truck
3. **PICKUP_READY**: Truck has arrived at warehouse for pickup
4. **LOADING**: Package is being loaded onto truck
5. **LOADED**: Package is loaded and ready for transport
6. **OUT_FOR_DELIVERY**: Truck is en route to delivery location
7. **DELIVERING**: Package is actively being delivered
8. **DELIVERED**: Package has been successfully delivered

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 17 (for development)
- Maven (for development)

### Running with Docker

1. Clone the repository:
```bash
git clone https://gitlab.oit.duke.edu/jt454/erss-project-ys467-jt454.git
cd erss-project-ys467-jt454
```

2. Start the services:
```bash
docker-compose up -d
```

3. Access the UPS interface:
   - Web interface: http://localhost:8080
   - Tracking page: http://localhost:8080/tracking

### Development Setup

1. Install Java 17 and Maven
2. Build the project:
```bash
mvn clean install
```

3. Run tests:
```bash
mvn test
```

4. Start the development server:
```bash
mvn spring-boot:run
```

## Amazon Integration

### Required API Endpoints

The UPS system expects the following endpoints on the Amazon side:

- **Truck Arrival Notification**: `/api/ups/notifications/truck-arrived`
- **Delivery Completion Notification**: `/api/ups/notifications/delivery-complete`
- **Status Update Notification**: `/api/ups/notifications/status-update`

### API Communication Protocol

The UPS system implements the protocol defined in `FinalProject_Protocols.pdf`. Key message flows include:

1. **Amazon → UPS**:
   - CreateShipmentRequest
   - ChangeDestinationRequest
   - QueryShipmentStatusRequest
   - PackageLoadedRequest

2. **UPS → Amazon**:
   - NotifyTruckArrived
   - NotifyDeliveryComplete
   - UpdateShipmentStatus
   - UPSGeneralError

## World Simulator Integration

The UPS system communicates with the World Simulator using Google Protocol Buffers as defined in `world_ups-1.proto`. Key message flows include:

1. **UPS → World**:
   - UConnect (initial connection)
   - UGoPickup (send truck to warehouse)
   - UGoDeliver (deliver package)
   - UQuery (check truck status)

2. **World → UPS**:
   - UConnected (connection confirmation)
   - UFinished (truck arrived at destination)
   - UDeliveryMade (package delivered)
   - UTruck (truck status updates)

## Testing

### Mock Amazon Service

For testing the Amazon integration, a mock Amazon service is provided:

1. Start the mock service:
```bash
./run-demo.sh
```

2. Access the mock Amazon dashboard:
```
http://localhost:8082
```

3. Create test shipments through the interface

### Test Scripts

Several test scripts are provided:

- `test-ups-function.sh`: Tests basic UPS functionality
- `test-amazon-connection.sh`: Tests Amazon API connectivity
- `demo-script.sh`: Guided demonstration of the complete system

### Integration Tests

Run integration tests with the World Simulator:

```bash
mvn test -Dtest=WorldSimulatorLiveIntegrationTest -DWORLD_SIMULATOR_RUNNING=true
```

## Configuration

The UPS system can be configured through:

1. **Environment Variables**:
   - `AMAZON_SERVICE_URL`: URL of the Amazon service
   - `UPS_WORLD_HOST`: Hostname of the World Simulator
   - `UPS_WORLD_PORT`: Port of the World Simulator
   - `UPS_WORLD_CREATE_NEW`: Whether to create a new world (true/false)

2. **Application Properties**:
   - Edit `src/main/resources/application.properties` for basic settings
   - Environment-specific properties in `application-{env}.properties`

## Troubleshooting

### Common Issues

1. **Connection Errors**:
   - Ensure the World Simulator is running
   - Check Amazon service connectivity with `./test-amazon-connection.sh`

2. **Warehouse ID Errors**:
   - Use only warehouse IDs that exist in the World Simulator
   - Restart UPS system to initialize warehouses

3. **Package Status Issues**:
   - Check database for current package status
   - Verify Amazon has sent proper notifications

### Logs

- UPS application logs: `docker logs ups-app`
- Database logs: `docker logs ups-db`
- World Simulator logs: `docker logs world-simulator`
- Mock Amazon logs: `docker logs mock-amazon`

## Component Details

### WorldConnector

Handles connection to the World Simulator and sending commands:
- Connects to a specific World ID or creates a new world
- Sends truck pickup and delivery commands
- Queries truck status
- Handles message acknowledgment

### WorldResponseHandler

Processes responses from the World Simulator:
- Updates truck location and status
- Updates package status
- Sends notifications to Amazon
- Handles error responses

### AmazonNotificationService

Sends notifications to Amazon about package and truck status:
- Truck arrival notifications
- Delivery completion notifications
- Status update notifications
- Error handling and retries

### ShipmentService

Processes shipment requests from Amazon:
- Creates new packages
- Assigns trucks to packages
- Updates package status
- Coordinates with WorldConnector for truck dispatching

