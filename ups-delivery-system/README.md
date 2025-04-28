# UPS Delivery System

This is a UPS delivery system that interfaces with Amazon's shipping API and a World simulator.

## Connecting to this UPS instance

### For Amazon teams

1. Set your Amazon instance to connect to our UPS API at: `http://vcm-46935.vm.duke.edu:8080/api`

2. Required API endpoints:
   - `POST /api/createshipment` - Create a new shipment
   - `POST /api/changedestination` - Change a package's destination
   - `POST /api/queryshipmentstatus` - Get a package's status
   - `POST /api/packageloaded` - Notify UPS that a package is loaded

3. Testing connection:
   - Send a test shipment request to verify connectivity
   - Check package tracking at `http://vcm-46935.vm.duke.edu:8080/tracking?trackingNumber=<id>`

### For UPS teams

To connect to another Amazon instance:

1. Run the connection script:
```
./connect-to-amazon.sh <amazon-host>
```

2. Test the connection:
```
./test-amazon-connection.sh
```

3. Make all scripts executable:
```
chmod +x *.sh
```

## Configuration

The system can be configured using the following methods (in order of priority):

1. Environment variables:
   - `AMAZON_SERVICE_URL`: The URL of the Amazon service

2. Spring application properties:
   - Edit `src/main/resources/application.properties` to change default settings

## API Documentation

For detailed API documentation, refer to the protocol specification document.

## Common Issues and Resolution

### Warehouse ID Errors

If you see errors like `warehouse id/product_id/package id/truck id in certain world and warehouse does not exist`, it means:

1. The warehouse ID being used doesn't exist in the world simulator
2. To fix this:
   - Restart the UPS system to initialize warehouses in the world
   - Check valid warehouse IDs using the mock Amazon dashboard
   - Use only valid warehouse IDs when creating shipments

### Connection Issues

If the mock Amazon can't connect to UPS:

1. Ensure the UPS service is running: `docker ps | grep ups-app`
2. Check the UPS logs for errors: `docker logs ups-app`
3. Verify the UPS service is accessible: `curl -s http://localhost:8080/health`
4. Make sure the port mappings are correct in `docker-compose.yml`

## Development

### Building the project
```
mvn clean package
```

### Running locally
```
java -jar target/ups-delivery-system-1.0-SNAPSHOT.jar
```

### Running with Docker
```
docker-compose up -d
``` 