package com.ups.controller;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.PackageLoadedRequest;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.User;
import com.ups.repository.PackageRepository;
import com.ups.repository.UserRepository;
import com.ups.service.AmazonIntegrationService;
import com.ups.service.ShipmentService;
import com.ups.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PackageRepository packageRepository;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
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

    @PostMapping("/create-user")
    public ResponseEntity<Map<String, Object>> createTestUser() {
        try {
            // Create a test user if it doesn't exist
            if (!userRepository.existsByUsername("testuser")) {
                User user = new User();
                user.setUsername("testuser");
                user.setPassword(passwordEncoder.encode("testpass"));
                user.setEmail("test@example.com");
                userRepository.save(user);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test user created or already exists");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/tracking")
    public ResponseEntity<Map<String, Object>> testTracking(@RequestParam("trackingNumber") Long trackingNumber) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<Package> pkg = packageRepository.findById(trackingNumber);
            
            if (pkg.isPresent()) {
                response.put("success", true);
                response.put("packageId", pkg.get().getId());
                response.put("status", pkg.get().getStatus().toString());
            } else {
                response.put("success", false);
                response.put("message", "Package not found");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> testLogin(@RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = credentials.get("username");
            String password = credentials.get("password");
            
            boolean authenticated = userService.authenticate(username, password);
            
            response.put("success", authenticated);
            if (!authenticated) {
                response.put("message", "Invalid credentials");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/redirect-package")
    public ResponseEntity<Map<String, Object>> testRedirectPackage(@RequestBody Map<String, Object> redirectRequest) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Long packageId = ((Number) redirectRequest.get("packageId")).longValue();
            Integer destinationX = ((Number) redirectRequest.get("destinationX")).intValue();
            Integer destinationY = ((Number) redirectRequest.get("destinationY")).intValue();
            
            Optional<Package> packageOpt = packageRepository.findById(packageId);
            
            if (packageOpt.isPresent()) {
                Package pkg = packageOpt.get();
                
                // Only allow redirection if not yet delivering
                if (pkg.getStatus() != PackageStatus.DELIVERING && pkg.getStatus() != PackageStatus.DELIVERED) {
                    pkg.setDestinationX(destinationX);
                    pkg.setDestinationY(destinationY);
                    packageRepository.save(pkg);
                    
                    response.put("success", true);
                    response.put("message", "Package redirected successfully");
                } else {
                    response.put("success", false);
                    response.put("message", "Package already out for delivery or delivered");
                }
            } else {
                response.put("success", false);
                response.put("message", "Package not found");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}