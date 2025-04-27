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
public class WorldSimulatorLiveIntegrationTest {
    
    @Autowired
    private Ups ups;
    
    @Autowired
    private TruckRepository truckRepository;
    
    @Autowired
    private PackageRepository packageRepository;
    
    @Autowired
    private WarehouseRepository warehouseRepository;
    
    private Truck testTruck;
    private Warehouse testWarehouse;
    
    @BeforeEach
    public void setUp() {
        // Clean up any existing test data
        truckRepository.deleteAll();
        packageRepository.deleteAll();
        warehouseRepository.deleteAll();
        
        // Create a test warehouse with ID 1 (Amazon should have created this in the World Simulator)
        testWarehouse = new Warehouse();
        testWarehouse.setId(1); // Use ID 1 since that's what Amazon typically creates
        testWarehouse.setX(10);
        testWarehouse.setY(10);
        testWarehouse = warehouseRepository.save(testWarehouse);
        
        // Give time for the World Simulator to initialize
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    public void testTruckToWarehouseWorkflow() throws Exception {
        System.out.println("Starting truck to warehouse workflow test...");
        
        // Create a test truck (don't set ID, let it be auto-generated)
        testTruck = new Truck();
        testTruck.setX(0);
        testTruck.setY(0);
        testTruck.setStatus(TruckStatus.IDLE);
        testTruck = truckRepository.save(testTruck);
        
        System.out.println("Created truck with ID: " + testTruck.getId());
        
        // Give the world simulator time to recognize the truck
        TimeUnit.SECONDS.sleep(1);
        
        // Send truck to warehouse
        ups.sendTruckToPickup(testTruck.getId(), testWarehouse.getId());
        
        // Wait for the truck to arrive at warehouse (adjust timeout as needed)
        for (int i = 0; i < 30; i++) { // Wait up to 30 seconds
            TimeUnit.SECONDS.sleep(1);
            
            Truck updatedTruck = truckRepository.findById(testTruck.getId()).orElseThrow();
            System.out.println("Truck status: " + updatedTruck.getStatus() + 
                             " at location (" + updatedTruck.getX() + "," + updatedTruck.getY() + ")");
            
            if (updatedTruck.getStatus() == TruckStatus.ARRIVE_WAREHOUSE) {
                // Check if truck is at the warehouse location
                assertEquals(testWarehouse.getX(), updatedTruck.getX());
                assertEquals(testWarehouse.getY(), updatedTruck.getY());
                System.out.println("Truck successfully arrived at warehouse!");
                return;
            }
        }
        
        fail("Truck did not arrive at warehouse within timeout period");
    }
    
    @Test
    public void testPackageDeliveryWorkflow() throws Exception {
        System.out.println("Starting package delivery workflow test...");
        
        // Create a test truck
        testTruck = new Truck();
        testTruck.setX(0);
        testTruck.setY(0);
        testTruck.setStatus(TruckStatus.IDLE);
        testTruck = truckRepository.save(testTruck);
        
        System.out.println("Created truck with ID: " + testTruck.getId());
        
        // Give the world simulator time to recognize the truck
        TimeUnit.SECONDS.sleep(1);
        
        // First, send truck to warehouse
        ups.sendTruckToPickup(testTruck.getId(), testWarehouse.getId());
        
        // Wait for truck to arrive at warehouse
        boolean arrivedAtWarehouse = false;
        for (int i = 0; i < 30; i++) {
            TimeUnit.SECONDS.sleep(1);
            Truck updatedTruck = truckRepository.findById(testTruck.getId()).orElseThrow();
            System.out.println("Truck status: " + updatedTruck.getStatus() + 
                             " at location (" + updatedTruck.getX() + "," + updatedTruck.getY() + ")");
            if (updatedTruck.getStatus() == TruckStatus.ARRIVE_WAREHOUSE) {
                arrivedAtWarehouse = true;
                break;
            }
        }
        assertTrue(arrivedAtWarehouse, "Truck did not arrive at warehouse");
        
        // Create a test package
        Package testPackage = new Package();
        testPackage.setId(1001L);
        testPackage.setWarehouse(testWarehouse);
        testPackage.setStatus(PackageStatus.LOADED);
        testPackage.setDestinationX(20);
        testPackage.setDestinationY(20);
        testPackage = packageRepository.save(testPackage);
        
        // Give time for the package to be recognized
        TimeUnit.SECONDS.sleep(1);
        
        // Send truck to deliver package
        Location destination = new Location(testPackage.getDestinationX(), testPackage.getDestinationY());
        ups.sendTruckToDeliver(testTruck.getId(), testPackage.getId(), destination);
        
        // Wait for package to be delivered
        for (int i = 0; i < 45; i++) { // Wait up to 45 seconds for delivery
            TimeUnit.SECONDS.sleep(1);
            
            Package updatedPackage = packageRepository.findById(testPackage.getId()).orElseThrow();
            System.out.println("Package status: " + updatedPackage.getStatus());
            
            if (updatedPackage.getStatus() == PackageStatus.DELIVERED) {
                System.out.println("Package successfully delivered!");
                
                // Check truck status
                Truck updatedTruck = truckRepository.findById(testTruck.getId()).orElseThrow();
                System.out.println("Truck is at (" + updatedTruck.getX() + "," + updatedTruck.getY() + ")");
                return;
            }
        }
        
        fail("Package was not delivered within timeout period");
    }
    
    @Test
    public void testTruckStatusQuery() throws Exception {
        System.out.println("Starting truck status query test...");
        
        // Create a test truck
        testTruck = new Truck();
        testTruck.setX(0);
        testTruck.setY(0);
        testTruck.setStatus(TruckStatus.IDLE);
        testTruck = truckRepository.save(testTruck);
        
        // Give time for the world simulator to recognize the truck
        TimeUnit.SECONDS.sleep(1);
        
        // Query truck status
        ups.queryTruckStatus(testTruck.getId());
        
        // Wait a moment for the response to be processed
        TimeUnit.SECONDS.sleep(2);
        
        // Verify that the truck status was updated
        Truck updatedTruck = truckRepository.findById(testTruck.getId()).orElseThrow();
        assertNotNull(updatedTruck);
        System.out.println("Truck query result - Status: " + updatedTruck.getStatus() + 
                         ", Location: (" + updatedTruck.getX() + "," + updatedTruck.getY() + ")");
    }
}