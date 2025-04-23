package com.ups.controller;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;
import com.ups.service.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

@RestController
@RequestMapping("/api")
public class AmazonApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(AmazonApiController.class);
    private final ShipmentService shipmentService;
    
    @Autowired
    public AmazonApiController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }
    
    @PostMapping("/createshipment")
    public ResponseEntity<CreateShipmentResponse> createShipment(@RequestBody CreateShipmentRequest request) {
        logger.info("Received createshipment request: {}", request.getSeqNum());
        
        // Validate request
        if (!"CreateShipmentRequest".equals(request.getMessageType())) {
            logger.error("Invalid message type: {}", request.getMessageType());
            return ResponseEntity.badRequest().build();
        }
        
        try {
            // Process the shipment request
            CreateShipmentResponse response = shipmentService.processShipmentRequest(request);
            logger.info("Sending response with seq_num: {}", response.getSeqNum());
            
            // Return response
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing shipment request", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}