package com.ups.controller;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.PackageLoadedRequest;
import com.ups.service.AmazonIntegrationService;
import com.ups.service.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller for integration testing the complete flow
 */
@RestController
@RequestMapping("/api/test")
@Profile("test")
public class IntegrationTestController {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestController.class);
    private static final AtomicLong sequenceNumber = new AtomicLong(1000);
    
    @Autowired
    private AmazonApiController amazonApiController;
    
    @Autowired
    private ShipmentService shipmentService;
    
    @Autowired
    private AmazonIntegrationService amazonIntegrationService;
    
    /**
     * Test the complete flow from shipment creation to delivery
     */
    @GetMapping("/complete-flow")
    public ResponseEntity<String> testCompleteFlow() {
        try {
            // 1. Create a shipment request (Amazon → UPS)
            CreateShipmentRequest createRequest = new CreateShipmentRequest();
            createRequest.setMessageType("CreateShipmentRequest");
            createRequest.setSeqNum(sequenceNumber.getAndIncrement());
            createRequest.setTimestamp(Instant.now());
            
            CreateShipmentRequest.ShipmentInfo shipmentInfo = new CreateShipmentRequest.ShipmentInfo();
            shipmentInfo.setPackageId(sequenceNumber.getAndIncrement());
            shipmentInfo.setWarehouseId(1);
            
            CreateShipmentRequest.Destination destination = new CreateShipmentRequest.Destination();
            destination.setX(50);
            destination.setY(50);
            shipmentInfo.setDestination(destination);
            
            CreateShipmentRequest.Item item = new CreateShipmentRequest.Item();
            item.setProductId(2001L);
            item.setDescription("Test Product");
            item.setCount(1);
            shipmentInfo.setItems(List.of(item));
            
            createRequest.setShipmentInfo(shipmentInfo);
            
            // Process the shipment request
            ResponseEntity<?> createResponse = amazonApiController.createShipment(createRequest);
            logger.info("Shipment created: {}", createResponse.getBody());
            
            // 2. Simulate package being loaded (Amazon confirms loading)
            Thread.sleep(2000); // Wait for truck to arrive at warehouse
            
            PackageLoadedRequest loadedRequest = new PackageLoadedRequest();
            loadedRequest.setMessageType("PackageLoadedRequest");
            loadedRequest.setSeqNum(sequenceNumber.getAndIncrement());
            loadedRequest.setTimestamp(Instant.now());
            loadedRequest.setPackageId(shipmentInfo.getPackageId());
            
            // Process the package loaded confirmation
            ResponseEntity<Void> loadedResponse = amazonApiController.handlePackageLoaded(loadedRequest);
            logger.info("Package loaded: {}", loadedResponse.getStatusCode());
            
            // 3. Wait for delivery to complete
            Thread.sleep(5000);
            
            return ResponseEntity.ok("Complete flow test executed successfully");
            
        } catch (Exception e) {
            logger.error("Error in complete flow test", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}