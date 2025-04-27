package com.ups.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ups.model.amazon.NotifyTruckArrived;
import com.ups.model.amazon.NotifyDeliveryComplete;
import com.ups.model.amazon.UpdateShipmentStatus;
import com.ups.model.entity.Package;
import com.ups.model.entity.Truck;
import com.ups.model.entity.Warehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Instant;

@Service
public class AmazonNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AmazonNotificationService.class);
    
    private final MessageTrackingService messageTrackingService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${amazon.service.url:http://amazon-mock:8080}")
    private String amazonServiceUrl;
    
    @Autowired
    public AmazonNotificationService(
            MessageTrackingService messageTrackingService,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.messageTrackingService = messageTrackingService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        
        // Ensure the ObjectMapper can handle Java 8 date/time types if not already configured
        try {
            // Test if the ObjectMapper can serialize an Instant
            objectMapper.writeValueAsString(Instant.now());
        } catch (Exception e) {
            if (e.getMessage().contains("Java 8 date/time type")) {
                logger.info("Configuring ObjectMapper to handle Java 8 date/time types");
                // Register JavaTimeModule if not already registered
                try {
                    Class<?> javaTimeModule = Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
                    objectMapper.registerModule((com.fasterxml.jackson.databind.Module) javaTimeModule.getDeclaredConstructor().newInstance());
                } catch (Exception ex) {
                    logger.error("Failed to register JavaTimeModule: {}", ex.getMessage());
                }
            }
        }
    }
    
    @PostConstruct
    public void init() {
        // Check if we have an environment variable that overrides the property
        String envUrl = System.getenv("AMAZON_SERVICE_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            logger.info("Using Amazon service URL from environment: {}", envUrl);
            this.amazonServiceUrl = envUrl;
        } else {
            logger.info("Using Amazon service URL from properties: {}", amazonServiceUrl);
        }
    }
    
    /**
     * Notify Amazon that a truck has arrived at the warehouse
     */
    public void notifyTruckArrival(Package pkg, Truck truck, Warehouse warehouse) {
        NotifyTruckArrived notification = new NotifyTruckArrived();
        notification.setMessageType("NotifyTruckArrived");
        notification.setSeqNum(messageTrackingService.getNextSeqNum());
        notification.setTimestamp(Instant.now());
        notification.setPackageId(pkg.getId());
        notification.setTruckId(truck.getId());
        notification.setWarehouseId(warehouse.getId());
        
        sendNotification(notification, "/api/ups/notifications/truck-arrived");
    }
    
    /**
     * Notify Amazon that a package has been delivered
     */
    public void notifyDeliveryComplete(Package pkg, Truck truck) {
        NotifyDeliveryComplete notification = new NotifyDeliveryComplete();
        notification.setMessageType("NotifyDeliveryComplete");
        notification.setSeqNum(messageTrackingService.getNextSeqNum());
        notification.setTimestamp(Instant.now());
        notification.setPackageId(pkg.getId());
        notification.setTruckId(truck.getId());
        
        // Create and set the final location
        NotifyDeliveryComplete.Location location = new NotifyDeliveryComplete.Location(
                pkg.getDestinationX(), 
                pkg.getDestinationY()
        );
        notification.setFinalLocation(location);
        
        sendNotification(notification, "/api/ups/notifications/delivery-complete");
    }
    
    /**
     * Send a status update to Amazon about a package
     */
    public void sendStatusUpdate(Package pkg, Truck truck, String status, String details) {
        UpdateShipmentStatus update = new UpdateShipmentStatus();
        update.setMessageType("UpdateShipmentStatus");
        update.setSeqNum(messageTrackingService.getNextSeqNum());
        update.setTimestamp(Instant.now());
        update.setPackageId(pkg.getId());
        update.setTruckId(truck != null ? truck.getId() : null);
        update.setStatus(status);
        update.setDetails(details);
        
        if (truck != null) {
            UpdateShipmentStatus.Location location = new UpdateShipmentStatus.Location(
                    truck.getX(), 
                    truck.getY()
            );
            update.setCurrentLocation(location);
        }
        
        sendNotification(update, "/api/ups/notifications/status-update");
    }
    
    /**
     * Generic method to send notification to Amazon
     */
    private void sendNotification(Object notification, String endpoint) {
        try {
            // Record the outgoing message
            if (notification instanceof NotifyTruckArrived) {
                NotifyTruckArrived n = (NotifyTruckArrived) notification;
                messageTrackingService.recordOutgoingMessage(n.getSeqNum(), n.getMessageType());
            } else if (notification instanceof NotifyDeliveryComplete) {
                NotifyDeliveryComplete n = (NotifyDeliveryComplete) notification;
                messageTrackingService.recordOutgoingMessage(n.getSeqNum(), n.getMessageType());
            } else if (notification instanceof UpdateShipmentStatus) {
                UpdateShipmentStatus n = (UpdateShipmentStatus) notification;
                messageTrackingService.recordOutgoingMessage(n.getSeqNum(), n.getMessageType());
            }
            
            // Prepare HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String jsonBody = objectMapper.writeValueAsString(notification);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            
            // Send the notification
            String url = amazonServiceUrl + endpoint;
            restTemplate.postForEntity(url, request, Void.class);
            
            logger.info("Successfully sent notification to Amazon: {}", endpoint);
        } catch (Exception e) {
            logger.error("Failed to send notification to Amazon: {}", e.getMessage(), e);
            // TODO: Implement retry mechanism or persistence for failed notifications
            
        }
    }
}