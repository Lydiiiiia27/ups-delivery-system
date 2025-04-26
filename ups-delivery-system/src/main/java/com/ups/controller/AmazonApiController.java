package com.ups.controller;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;
import com.ups.model.amazon.ChangeDestinationRequest;
import com.ups.model.amazon.ChangeDestinationResponse;
import com.ups.model.amazon.NotifyTruckArrived;
import com.ups.model.amazon.NotifyDeliveryComplete;
import com.ups.model.amazon.QueryShipmentStatusRequest;
import com.ups.model.amazon.QueryShipmentStatusResponse;
import com.ups.model.amazon.UpdateShipmentStatus;
import com.ups.model.amazon.UPSGeneralError;
import com.ups.service.ShipmentService;
import com.ups.service.MessageTrackingService;

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
    private final MessageTrackingService messageTrackingService;
    
    @Autowired
    public AmazonApiController(ShipmentService shipmentService, MessageTrackingService messageTrackingService) {
        this.shipmentService = shipmentService;
        this.messageTrackingService = messageTrackingService;
    }
    
    @PostMapping("/createshipment")
    public ResponseEntity<CreateShipmentResponse> createShipment(@RequestBody CreateShipmentRequest request) {
        logger.info("Received createshipment request: {}", request.getSeqNum());
        
        // Validate request
        if (!"CreateShipmentRequest".equals(request.getMessageType())) {
            logger.error("Invalid message type: {}", request.getMessageType());
            return ResponseEntity.badRequest().build();
        }
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(request.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", request.getSeqNum());
            // TODO: Return the cached response
            return ResponseEntity.ok().build();
        }
        
        try {
            // Process the shipment request
            CreateShipmentResponse response = shipmentService.processShipmentRequest(request);
            
            // Mark the message as processed
            messageTrackingService.markMessageProcessed(request.getSeqNum(), request.getMessageType());
            
            // Track the outgoing message
            messageTrackingService.recordOutgoingMessage(response.getSeqNum(), response.getMessageType());
            
            logger.info("Sending response with seq_num: {}", response.getSeqNum());
            
            // Return response
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing shipment request", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/changedestination")
    public ResponseEntity<ChangeDestinationResponse> changeDestination(@RequestBody ChangeDestinationRequest request) {
        logger.info("Received changedestination request for package: {}", request.getPackageId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(request.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", request.getSeqNum());
            // TODO: Return the cached response
            return ResponseEntity.ok().build();
        }
        
        // Create response
        ChangeDestinationResponse response = new ChangeDestinationResponse();
        response.setMessageType("ChangeDestinationResponse");
        response.setSeqNum(messageTrackingService.getNextSeqNum());
        response.setAck(request.getSeqNum());
        response.setTimestamp(Instant.now());
        response.setStatus("UPDATED");
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(request.getSeqNum(), request.getMessageType());
        
        // Track the outgoing message
        messageTrackingService.recordOutgoingMessage(response.getSeqNum(), response.getMessageType());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/queryshipmentstatus")
    public ResponseEntity<QueryShipmentStatusResponse> queryShipmentStatus(@RequestBody QueryShipmentStatusRequest request) {
        logger.info("Received queryshipmentstatus request for package: {}", request.getPackageId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(request.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", request.getSeqNum());
            // TODO: Return the cached response
            return ResponseEntity.ok().build();
        }
        
        // Create response with mock data
        QueryShipmentStatusResponse response = new QueryShipmentStatusResponse();
        response.setMessageType("QueryShipmentStatusResponse");
        response.setSeqNum(messageTrackingService.getNextSeqNum());
        response.setAck(request.getSeqNum());
        response.setTimestamp(Instant.now());
        response.setPackageId(request.getPackageId());
        response.setTruckId(55); // Mock data
        response.setCurrentStatus("IN_TRANSIT"); // Mock data
        
        // Create a mock location
        QueryShipmentStatusResponse.Location location = new QueryShipmentStatusResponse.Location(10, 20);
        response.setCurrentLocation(location);
        
        // Set an expected delivery time (20 minutes from now)
        response.setExpectedDeliveryTime(Instant.now().plusSeconds(20 * 60));
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(request.getSeqNum(), request.getMessageType());
        
        // Track the outgoing message
        messageTrackingService.recordOutgoingMessage(response.getSeqNum(), response.getMessageType());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/notifytruckarrived")
    public ResponseEntity<Void> notifyTruckArrived(@RequestBody NotifyTruckArrived notification) {
        logger.info("Received truck arrived notification for package: {}, truck: {}", 
                notification.getPackageId(), notification.getTruckId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(notification.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", notification.getSeqNum());
            return ResponseEntity.ok().build();
        }
        
        // Process truck arrived notification
        // TODO: Update package status in database
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(notification.getSeqNum(), notification.getMessageType());
        
        // No response is expected for notifications
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/notifydeliverycomplete")
    public ResponseEntity<Void> notifyDeliveryComplete(@RequestBody NotifyDeliveryComplete notification) {
        logger.info("Received delivery complete notification for package: {}, truck: {}", 
                notification.getPackageId(), notification.getTruckId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(notification.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", notification.getSeqNum());
            return ResponseEntity.ok().build();
        }
        
        // Process delivery complete notification
        // TODO: Update package status in database
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(notification.getSeqNum(), notification.getMessageType());
        
        // No response is expected for notifications
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/updateshipmentstatus")
    public ResponseEntity<Void> updateShipmentStatus(@RequestBody UpdateShipmentStatus update) {
        logger.info("Received shipment status update for package: {}, status: {}", 
                update.getPackageId(), update.getStatus());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(update.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", update.getSeqNum());
            return ResponseEntity.ok().build();
        }
        
        // Process shipment status update
        // TODO: Update package status in database and notify users if applicable
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(update.getSeqNum(), update.getMessageType());
        
        // No response is expected for updates
        return ResponseEntity.ok().build();
    }
    
    // Helper method to generate a general error response
    private UPSGeneralError createErrorResponse(long seqNum, int errorCode, String errorMsg) {
        UPSGeneralError error = new UPSGeneralError();
        error.setMessageType("UPSGeneralError");
        error.setSeqNum(messageTrackingService.getNextSeqNum());
        error.setTimestamp(Instant.now());
        error.setErrorCode(errorCode);
        error.setErrorMsg(errorMsg);
        
        // Track the outgoing message
        messageTrackingService.recordOutgoingMessage(error.getSeqNum(), error.getMessageType());
        
        return error;
    }
}