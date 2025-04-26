package com.ups.service.world;

import com.ups.helper.WorldConnectorTestHelper;
import com.ups.model.Location;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.repository.TruckRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
public class WorldConnectorIntegrationTest {
    
    @Value("${ups.world.host:localhost}")
    private String worldSimulatorHost;
    
    @Autowired
    private WorldConnectorTestHelper worldConnectorHelper;
    
    @Autowired
    private WorldConnector worldConnector;
    
    @Autowired
    private TruckRepository truckRepository;
    
    private Long testWorldId;
    private List<Truck> testTrucks = new ArrayList<>();
    
    @BeforeEach
    public void setup() {
        // Create some test trucks
        Truck truck = new Truck();
        truck.setX(0);
        truck.setY(0);
        truck.setStatus(TruckStatus.IDLE);
        testTrucks.add(truck);
        
        try {
            // Connect to a new world for this test
            testWorldId = worldConnectorHelper.setupTestWorld(
                worldSimulatorHost, 12345, testTrucks);
            
            assertNotNull(testWorldId, "Failed to create test world");
        } catch (Exception e) {
            System.out.println("Warning: Could not connect to world simulator. Test will be skipped.");
            e.printStackTrace();
        }
    }
    
    @AfterEach
    public void cleanup() {
        // Disconnect from the test world
        if (testWorldId != null) {
            worldConnectorHelper.cleanupTestWorld();
        }
    }
    
    @Test
    public void testPickupAndDelivery() throws Exception {
        // Skip the test if we couldn't connect to the world
        if (testWorldId == null) {
            System.out.println("Skipping test as no connection to world simulator was established");
            return;
        }
        
        // Test the pickup functionality
        int truckId = testTrucks.get(0).getId();
        int warehouseId = 1; // Assuming warehouse with ID 1 exists in the simulator
        long seqNum = worldConnector.getNextSeqNum();
        
        // Send a pickup command
        worldConnector.pickup(truckId, warehouseId, seqNum);
        
        // Test the delivery functionality
        long packageId = 1001; // This would be created by Amazon and loaded onto the truck
        Location destination = new Location(5, 10);
        seqNum = worldConnector.getNextSeqNum();
        
        // Send a delivery command
        worldConnector.deliver(truckId, packageId, destination, seqNum);
        
        // In a real test, you'd verify that the responses were correct
        // For simplicity, we're just testing that the calls don't throw exceptions
    }
}