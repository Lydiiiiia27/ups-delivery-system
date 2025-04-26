package com.ups.helper;

import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.service.world.WorldConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("test")
public class WorldConnectorTestHelper {
    private static final Logger logger = LoggerFactory.getLogger(WorldConnectorTestHelper.class);
    
    @Autowired
    private WorldConnector worldConnector;
    
    /**
     * Connects to the world simulator with a new world for testing.
     * 
     * @param host The world simulator host
     * @param port The world simulator port
     * @param trucks The list of trucks to initialize
     * @return The new world ID
     */
    public Long setupTestWorld(String host, int port, List<Truck> trucks) {
        try {
            // Use the provided trucks list instead of calling createTestTrucks()
            WorldConnector connector = new WorldConnector(host, port, trucks);
            return connector.getWorldId();
        } catch (Exception e) {
            logger.error("Failed to set up test world: {}", e.getMessage());
            throw new RuntimeException("Failed to set up test world", e);
        }
    }
    
    /**
     * Creates a list of test trucks for testing.
     * 
     * @return List of test trucks
     */
    private List<Truck> createTestTrucks() {
        List<Truck> trucks = new ArrayList<>();
        
        // Create a test truck
        Truck truck = new Truck();
        truck.setId(1);
        truck.setX(0);
        truck.setY(0);
        truck.setStatus(TruckStatus.IDLE);
        trucks.add(truck);
        
        return trucks;
    }
    
    /**
     * Cleans up the test world.
     */
    public void cleanupTestWorld() {
        try {
            worldConnector.disconnect();
        } catch (Exception e) {
            logger.warn("Error during test world cleanup: {}", e.getMessage());
        }
    }
}