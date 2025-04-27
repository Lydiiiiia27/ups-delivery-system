package com.ups.service.world;

import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.repository.TruckRepository;
import com.ups.repository.PackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class WorldResponseHandlingIntegrationTest {
    
    @Autowired
    private WorldResponseHandler responseHandler;
    
    @Autowired
    private WorldResponseListener responseListener;
    
    @Autowired
    private TruckRepository truckRepository;
    
    @Autowired
    private PackageRepository packageRepository;
    
    @Autowired
    private Ups ups;
    
    @Test
    public void testResponseHandlingSystem() throws Exception {
        // Create a test truck
        Truck truck = new Truck();
        truck.setX(0);
        truck.setY(0);
        truck.setStatus(TruckStatus.IDLE);
        truck = truckRepository.save(truck);
        
        // Verify the truck was created
        assertNotNull(truck.getId());
        
        // Send truck to a warehouse
        ups.sendTruckToPickup(truck.getId(), 1);
        
        // Wait for the response to be processed
        TimeUnit.SECONDS.sleep(5);
        
        // Check if the truck status has been updated
        Truck updatedTruck = truckRepository.findById(truck.getId()).orElse(null);
        assertNotNull(updatedTruck);
        
        // The status should be TRAVELING initially
        assertEquals(TruckStatus.TRAVELING, updatedTruck.getStatus());
        
        // Note: In a real integration test with the World Simulator running,
        // we would wait longer and check for ARRIVE_WAREHOUSE status
    }
}