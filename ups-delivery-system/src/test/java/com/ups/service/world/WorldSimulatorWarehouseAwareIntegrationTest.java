package com.ups.service.world;

import com.ups.model.Location;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.model.entity.Warehouse;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import com.ups.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "WORLD_SIMULATOR_RUNNING", matches = "true")
public class WorldSimulatorWarehouseAwareIntegrationTest {
    
    @Autowired
    private Ups ups;
    
    @Autowired
    private TruckRepository truckRepository;
    
    @Autowired
    private PackageRepository packageRepository;
    
    @Autowired
    private WarehouseRepository warehouseRepository;
    
    @Test
    public void testFindExistingWarehouseAndPickup() throws Exception {
        System.out.println("Testing with existing warehouse...");
        
        // First, let's try to find any existing warehouses
        System.out.println("Attempting to find existing warehouses...");
        
        // Create a warehouse record in our database (assuming it exists in World Simulator)
        Warehouse testWarehouse = new Warehouse();
        testWarehouse.setId(1); // Using ID 1 as it's commonly available
        testWarehouse.setX(10);
        testWarehouse.setY(10);
        testWarehouse = warehouseRepository.save(testWarehouse);
        
        // Create a test truck
        Truck testTruck = new Truck();
        testTruck.setX(0);
        testTruck.setY(0);
        testTruck.setStatus(TruckStatus.IDLE);
        testTruck = truckRepository.save(testTruck);
        
        System.out.println("Created truck with ID: " + testTruck.getId());
        System.out.println("Using warehouse with ID: " + testWarehouse.getId());
        
        // Send truck to warehouse
        ups.sendTruckToPickup(testTruck.getId(), testWarehouse.getId());
        
        // Wait and check status
        boolean truckMoved = false;
        for (int i = 0; i < 30; i++) {
            TimeUnit.SECONDS.sleep(1);
            
            Truck updatedTruck = truckRepository.findById(testTruck.getId()).orElseThrow();
            System.out.println("Attempt " + (i+1) + " - Truck status: " + updatedTruck.getStatus() + 
                             " at location (" + updatedTruck.getX() + "," + updatedTruck.getY() + ")");
            
            if (updatedTruck.getStatus() != TruckStatus.IDLE || 
                updatedTruck.getX() != 0 || updatedTruck.getY() != 0) {
                truckMoved = true;
                System.out.println("Truck is moving!");
            }
            
            if (updatedTruck.getStatus() == TruckStatus.ARRIVE_WAREHOUSE) {
                System.out.println("Truck successfully arrived at warehouse!");
                return;
            }
        }
        
        if (!truckMoved) {
            System.out.println("DIAGNOSIS: Truck never moved from original position");
            System.out.println("Possible issues:");
            System.out.println("1. Warehouse doesn't exist in World Simulator");
            System.out.println("2. World ID mismatch");
            System.out.println("3. Connection issues with World Simulator");
        }
        
        fail("Truck did not complete journey within timeout period");
    }
    
    @Test
    public void testCreateAndUseWarehouse() throws Exception {
        System.out.println("Testing with manually created warehouse...");
        
        // Try with warehouse ID 10 (less likely to conflict)
        Warehouse testWarehouse = new Warehouse();
        testWarehouse.setId(10);
        testWarehouse.setX(5);
        testWarehouse.setY(5);
        testWarehouse = warehouseRepository.save(testWarehouse);
        
        // Create a test truck
        Truck testTruck = new Truck();
        testTruck.setX(0);
        testTruck.setY(0);
        testTruck.setStatus(TruckStatus.IDLE);
        testTruck = truckRepository.save(testTruck);
        
        System.out.println("Created truck with ID: " + testTruck.getId());
        System.out.println("Using warehouse with ID: " + testWarehouse.getId() + 
                          " at location (" + testWarehouse.getX() + "," + testWarehouse.getY() + ")");
        
        // Send truck to warehouse
        ups.sendTruckToPickup(testTruck.getId(), testWarehouse.getId());
        
        // Monitor progress
        for (int i = 0; i < 20; i++) {
            TimeUnit.SECONDS.sleep(1);
            
            Truck updatedTruck = truckRepository.findById(testTruck.getId()).orElseThrow();
            System.out.println("Attempt " + (i+1) + " - Truck status: " + updatedTruck.getStatus() + 
                             " at location (" + updatedTruck.getX() + "," + updatedTruck.getY() + ")");
            
            if (updatedTruck.getStatus() == TruckStatus.ARRIVE_WAREHOUSE) {
                assertEquals(testWarehouse.getX(), updatedTruck.getX(), 
                            "Truck should be at warehouse X coordinate");
                assertEquals(testWarehouse.getY(), updatedTruck.getY(), 
                            "Truck should be at warehouse Y coordinate");
                System.out.println("Truck successfully arrived at warehouse!");
                return;
            }
        }
        
        fail("Truck did not arrive at warehouse within timeout period");
    }
}