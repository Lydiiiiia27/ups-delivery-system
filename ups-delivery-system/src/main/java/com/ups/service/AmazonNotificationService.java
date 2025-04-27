package com.ups.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ups.model.MessageLog;
import com.ups.model.amazon.NotifyTruckArrived;
import com.ups.model.amazon.NotifyDeliveryComplete;
import com.ups.model.amazon.UpdateShipmentStatus;
import com.ups.model.entity.Package;
import com.ups.model.entity.Truck;
import com.ups.model.entity.Warehouse;
import com.ups.repository.MessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AmazonNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AmazonNotificationService.class);
    
    private final MessageTrackingService messageTrackingService;
    private final MessageLogRepository messageLogRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Cache for storing sent messages and their responses for idempotency
    private final Map<Long, ResponseEntity<?>> responseCache = new ConcurrentHashMap<>();
    
    // Maximum retry attempts
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    // Retry interval in milliseconds
    private static final long RETRY_INTERVAL = 5000; // 5 seconds
    
    @Value("${amazon.service.url:http://amazon-mock:8080}")
    private String amazonServiceUrl;
    
    @Autowired
    public AmazonNotificationService(
            MessageTrackingService messageTrackingService,
            MessageLogRepository messageLogRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.messageTrackingService = messageTrackingService;
        this.messageLogRepository = messageLogRepository;
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
     * @return ResponseEntity containing the response from Amazon
     */
    public ResponseEntity<?> notifyTruckArrival(Package pkg, Truck truck, Warehouse warehouse) {
        NotifyTruckArrived notification = new NotifyTruckArrived();
        notification.setMessageType("NotifyTruckArrived");
        notification.setSeqNum(messageTrackingService.getNextSeqNum());
        notification.setTimestamp(Instant.now());
        notification.setPackageId(pkg.getId());
        notification.setTruckId(truck.getId());
        notification.setWarehouseId(warehouse.getId());
        
        logger.info("Notifying Amazon about truck {} arrival at warehouse {} for package {}", 
                truck.getId(), warehouse.getId(), pkg.getId());
        
        return sendNotification(notification, "/api/ups/notifications/truck-arrived");
    }
    
    /**
     * Notify Amazon that a package has been delivered
     * @return ResponseEntity containing the response from Amazon
     */
    public ResponseEntity<?> notifyDeliveryComplete(Package pkg, Truck truck) {
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
        
        logger.info("Notifying Amazon about package {} delivery completion by truck {}", 
                pkg.getId(), truck.getId());
        
        return sendNotification(notification, "/api/ups/notifications/delivery-complete");
    }
    
    /**
     * Send a status update to Amazon about a package
     * @return ResponseEntity containing the response from Amazon
     */
    public ResponseEntity<?> sendStatusUpdate(Package pkg, Truck truck, String status, String details) {
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
        
        logger.info("Sending status update to Amazon for package {}: {}", pkg.getId(), status);
        
        return sendNotification(update, "/api/ups/notifications/status-update");
    }
    
    /**
     * Generic method to send notification to Amazon with manual retry mechanism
     * @return ResponseEntity containing the response from Amazon
     */
    private ResponseEntity<?> sendNotification(Object notification, String endpoint) {
        // Extract sequence number for tracking
        Long seqNum = extractSeqNum(notification);
        
        // Check if we already have a response for this sequence number
        if (responseCache.containsKey(seqNum)) {
            logger.info("Found cached response for message with sequence number: {}", seqNum);
            return responseCache.get(seqNum);
        }
        
        // Record the outgoing message
        String messageType = extractMessageType(notification);
        messageTrackingService.recordOutgoingMessage(seqNum, messageType);
        
        // Prepare HTTP request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        ResponseEntity<?> response = null;
        Exception lastException = null;
        
        // Manual retry logic
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String jsonBody = objectMapper.writeValueAsString(notification);
                HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
                
                // Send the notification
                String url = amazonServiceUrl + endpoint;
                
                // Use Object.class as the response type for flexibility
                response = restTemplate.postForEntity(url, request, Object.class);
                
                // If successful, break the retry loop
                if (response.getStatusCode().is2xxSuccessful()) {
                    // Cache the response
                    responseCache.put(seqNum, response);
                    
                    // Update the message log to mark as sent
                    markMessageAsSent(seqNum);
                    
                    logger.info("Successfully sent notification to Amazon: {} with seq num: {} (attempt {})", 
                            endpoint, seqNum, attempt + 1);
                    
                    return response;
                }
            } catch (Exception e) {
                lastException = e;
                logger.warn("Failed to send notification to Amazon: {} with seq num: {}, Error: {} (attempt {})", 
                        endpoint, seqNum, e.getMessage(), attempt + 1);
                
                // Wait before retrying
                try {
                    Thread.sleep(RETRY_INTERVAL);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // If we get here, all retry attempts failed
        logger.error("All {} retry attempts failed for notification to Amazon: {} with seq num: {}", 
                MAX_RETRY_ATTEMPTS, endpoint, seqNum);
        
        // Mark message as failed
        if (lastException != null) {
            markMessageAsFailed(seqNum, lastException.getMessage());
        }
        
        // Return a default response or throw an exception
        throw new RestClientException("Failed to send notification to Amazon after " + 
                MAX_RETRY_ATTEMPTS + " retry attempts", lastException);
    }
    
    /**
     * Extract sequence number from notification object
     */
    private Long extractSeqNum(Object notification) {
        if (notification instanceof NotifyTruckArrived) {
            return ((NotifyTruckArrived) notification).getSeqNum();
        } else if (notification instanceof NotifyDeliveryComplete) {
            return ((NotifyDeliveryComplete) notification).getSeqNum();
        } else if (notification instanceof UpdateShipmentStatus) {
            return ((UpdateShipmentStatus) notification).getSeqNum();
        }
        return 0L;
    }
    
    /**
     * Extract message type from notification object
     */
    private String extractMessageType(Object notification) {
        if (notification instanceof NotifyTruckArrived) {
            return ((NotifyTruckArrived) notification).getMessageType();
        } else if (notification instanceof NotifyDeliveryComplete) {
            return ((NotifyDeliveryComplete) notification).getMessageType();
        } else if (notification instanceof UpdateShipmentStatus) {
            return ((UpdateShipmentStatus) notification).getMessageType();
        }
        return "Unknown";
    }
    
    /**
     * Mark message as successfully sent
     */
    private void markMessageAsSent(Long seqNum) {
        MessageLog log = messageLogRepository.findBySeqNum(seqNum);
        if (log != null) {
            log.setAcknowledged(Instant.now());
            messageLogRepository.save(log);
        }
    }
    
    /**
     * Mark message as failed for retry
     */
    private void markMessageAsFailed(Long seqNum, String errorMessage) {
        MessageLog log = messageLogRepository.findBySeqNum(seqNum);
        if (log != null) {
            // You might want to add error message field to MessageLog entity
            messageLogRepository.save(log);
        }
    }
    
    /**
     * Scheduled task to retry failed notifications
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void retryFailedNotifications() {
        logger.info("Starting scheduled retry of failed notifications");
        
        // Get all unacknowledged messages
        List<MessageLog> unacknowledgedMessages = messageTrackingService.getUnacknowledgedMessages();
        
        if (unacknowledgedMessages.isEmpty()) {
            logger.info("No failed notifications to retry");
            return;
        }
        
        logger.info("Found {} failed notifications to retry", unacknowledgedMessages.size());
        
        // TODO: Implement the actual retry logic
        // This would involve recreating the notification objects and resending them
    }
    
    /**
     * Cleanup response cache periodically to prevent memory issues
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupResponseCache() {
        int cacheSize = responseCache.size();
        logger.info("Cleaning up response cache, current size: {}", cacheSize);
        
        if (cacheSize > 1000) {
            // Only keep cache entries for the last 1000 messages
            responseCache.clear();
            logger.info("Response cache cleared due to size exceeding limit");
        }
    }
}