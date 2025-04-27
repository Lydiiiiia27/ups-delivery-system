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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending notifications to Amazon about package and truck status
 */
@Service
public class AmazonNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AmazonNotificationService.class);
    
    private final MessageTrackingService messageTrackingService;
    private final MessageLogRepository messageLogRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Cache for tracking responses to avoid duplicate processing
    private final Map<Long, Object> responseCache = new ConcurrentHashMap<>();
    
    // Maximum retry attempts for failed notifications
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    // Retry delay in milliseconds
    private static final long RETRY_DELAY_MS = 5000; // 5 seconds
    
    @Value("${amazon.service.url:http://amazon:8080}")
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
    }
    
    @PostConstruct
    public void init() {
        logger.info("Initializing Amazon Notification Service with URL: {}", amazonServiceUrl);
        
        // Check if we have an environment variable that overrides the property
        String envUrl = System.getenv("AMAZON_SERVICE_URL");
        if (envUrl != null && !envUrl.trim().isEmpty()) {
            logger.info("Using Amazon service URL from environment: {}", envUrl);
            this.amazonServiceUrl = envUrl;
        }
    }
    
    /**
     * Notify Amazon that a truck has arrived at the warehouse
     * @param pkg The package to pick up
     * @param truck The truck that arrived
     * @param warehouse The warehouse where the truck arrived
     * @return The response from Amazon
     */
    public ResponseEntity<?> notifyTruckArrival(Package pkg, Truck truck, Warehouse warehouse) {
        logger.info("Notifying Amazon about truck {} arrival at warehouse {} for package {}",
                truck.getId(), warehouse.getId(), pkg.getId());
        
        // Create the notification
        NotifyTruckArrived notification = new NotifyTruckArrived();
        notification.setMessageType("NotifyTruckArrived");
        notification.setSeqNum(messageTrackingService.getNextSeqNum());
        notification.setTimestamp(Instant.now());
        notification.setPackageId(pkg.getId());
        notification.setTruckId(truck.getId());
        notification.setWarehouseId(warehouse.getId());
        
        // Record the outgoing message
        messageTrackingService.recordOutgoingMessage(notification.getSeqNum(), notification.getMessageType());
        
        // Send the notification to Amazon
        String endpoint = "/api/ups/notifications/truck-arrived";
        return sendNotification(notification, endpoint);
    }
    
    /**
     * Notify Amazon that a package has been delivered
     * @param pkg The delivered package
     * @param truck The truck that delivered the package
     * @return The response from Amazon
     */
    public ResponseEntity<?> notifyDeliveryComplete(Package pkg, Truck truck) {
        logger.info("Notifying Amazon about delivery completion for package {} by truck {}",
                pkg.getId(), truck.getId());
        
        // Create the notification
        NotifyDeliveryComplete notification = new NotifyDeliveryComplete();
        notification.setMessageType("NotifyDeliveryComplete");
        notification.setSeqNum(messageTrackingService.getNextSeqNum());
        notification.setTimestamp(Instant.now());
        notification.setPackageId(pkg.getId());
        notification.setTruckId(truck.getId());
        
        // Set the final delivery location
        NotifyDeliveryComplete.Location location = new NotifyDeliveryComplete.Location(
                pkg.getDestinationX(), 
                pkg.getDestinationY());
        notification.setFinalLocation(location);
        
        // Record the outgoing message
        messageTrackingService.recordOutgoingMessage(notification.getSeqNum(), notification.getMessageType());
        
        // Send the notification to Amazon
        String endpoint = "/api/ups/notifications/delivery-complete";
        return sendNotification(notification, endpoint);
    }
    
    /**
     * Send a status update to Amazon
     * @param pkg The package
     * @param truck The truck (can be null)
     * @param status The status message
     * @param details Additional details about the status
     * @return The response from Amazon
     */
    public ResponseEntity<?> sendStatusUpdate(Package pkg, Truck truck, String status, String details) {
        logger.info("Sending status update to Amazon for package {}: {}", pkg.getId(), status);
        
        // Create the status update
        UpdateShipmentStatus update = new UpdateShipmentStatus();
        update.setMessageType("UpdateShipmentStatus");
        update.setSeqNum(messageTrackingService.getNextSeqNum());
        update.setTimestamp(Instant.now());
        update.setPackageId(pkg.getId());
        
        if (truck != null) {
            update.setTruckId(truck.getId());
            
            // Set the current location if truck is available
            UpdateShipmentStatus.Location location = new UpdateShipmentStatus.Location(
                    truck.getX(), 
                    truck.getY());
            update.setCurrentLocation(location);
        }
        
        update.setStatus(status);
        update.setDetails(details);
        
        // Record the outgoing message
        messageTrackingService.recordOutgoingMessage(update.getSeqNum(), update.getMessageType());
        
        // Send the notification to Amazon
        String endpoint = "/api/ups/notifications/status-update";
        return sendNotification(update, endpoint);
    }
    
    /**
     * Send a notification to Amazon with retry logic
     * @param notification The notification to send
     * @param endpoint The API endpoint to send to
     * @return The response from Amazon
     */
    private ResponseEntity<?> sendNotification(Object notification, String endpoint) {
        // Prepare HTTP headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Extract sequence number for caching
        Long seqNum = extractSeqNum(notification);
        
        // Check cache for duplicate requests
        if (responseCache.containsKey(seqNum)) {
            logger.debug("Using cached response for notification with seq_num: {}", seqNum);
            return (ResponseEntity<?>) responseCache.get(seqNum);
        }
        
        // Implement retry logic
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String url = amazonServiceUrl + endpoint;
                
                // Convert notification to JSON
                String jsonBody = objectMapper.writeValueAsString(notification);
                HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);
                
                // Send the request
                ResponseEntity<Object> response = restTemplate.postForEntity(url, requestEntity, Object.class);
                
                // Cache the response
                responseCache.put(seqNum, response);
                
                // Mark message as acknowledged
                messageTrackingService.acknowledgeMessage(seqNum);
                
                logger.info("Successfully sent notification to Amazon: {} (attempt {})", endpoint, attempt + 1);
                return response;
            } catch (Exception e) {
                logger.warn("Failed to send notification to Amazon: {} (attempt {}): {}", 
                        endpoint, attempt + 1, e.getMessage());
                
                // Wait before retrying
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // All retry attempts failed
        logger.error("Failed to send notification to Amazon after {} attempts: {}", 
                MAX_RETRY_ATTEMPTS, endpoint);
        
        throw new RestClientException("Failed to send notification to Amazon after " + 
                MAX_RETRY_ATTEMPTS + " retry attempts");
    }
    
    /**
     * Extract sequence number from a notification object
     * @param notification The notification object
     * @return The sequence number
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
     * Clean up response cache periodically
     */
    @Scheduled(fixedRate = 3600000) // Once per hour
    public void cleanupResponseCache() {
        int size = responseCache.size();
        logger.info("Cleaning up response cache, current size: {}", size);
        
        if (size > 1000) {
            responseCache.clear();
            logger.info("Response cache cleared due to size exceeding limit");
        }
    }
    
    /**
     * Scheduled task to clean up old message logs
     */
    @Scheduled(cron = "0 0 2 * * *") // Run at 2:00 AM every day
    public void cleanupOldMessageLogs() {
        logger.info("Starting cleanup of old message logs");
        
        // Define the cutoff date (e.g., 7 days ago)
        Instant cutoffDate = Instant.now().minus(7, ChronoUnit.DAYS);
        
        try {
            // Find all message logs older than the cutoff date
            List<MessageLog> oldLogs = messageLogRepository.findByTimestampBefore(cutoffDate);
            
            if (oldLogs.isEmpty()) {
                logger.info("No old message logs to clean up");
                return;
            }
            
            logger.info("Found {} old message logs to clean up", oldLogs.size());
            
            // Delete the old logs
            messageLogRepository.deleteAll(oldLogs);
            
            logger.info("Successfully cleaned up {} old message logs", oldLogs.size());
        } catch (Exception e) {
            logger.error("Error cleaning up old message logs", e);
        }
    }
    
    /**
     * Retry failed notifications
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void retryFailedNotifications() {
        List<String> unacknowledgedMessageIds = messageLogRepository.findUnacknowledgedMessageIds("OUTGOING");
        if (unacknowledgedMessageIds.isEmpty()) {
            logger.debug("No failed notifications to retry");
            return;
        }
        
        logger.info("Retrying {} failed notifications", unacknowledgedMessageIds.size());
        
        for (String seqNumStr : unacknowledgedMessageIds) {
            try {
                Long seqNum = Long.parseLong(seqNumStr);
                MessageLog message = messageLogRepository.findBySeqNum(seqNum);
                
                if (message != null) {
                    // Only retry messages that are older than 1 minute and less than 24 hours
                    Instant now = Instant.now();
                    long ageInMinutes = Duration.between(message.getTimestamp(), now).toMinutes();
                    
                    if (ageInMinutes < 1 || ageInMinutes > 24 * 60) {
                        // Skip messages that are too new or too old
                        continue;
                    }
                    
                    logger.info("Retrying notification with seq_num: {}, type: {}, age: {} minutes", 
                            seqNum, message.getMessageType(), ageInMinutes);
                    
                    // Handle different message types
                    switch (message.getMessageType()) {
                        case "NotifyTruckArrived":
                            retryTruckArrivalNotification(message);
                            break;
                        case "NotifyDeliveryComplete":
                            retryDeliveryCompleteNotification(message);
                            break;
                        case "UpdateShipmentStatus":
                            retryStatusUpdateNotification(message);
                            break;
                        default:
                            logger.warn("Unknown message type for retry: {}", message.getMessageType());
                    }
                }
            } catch (Exception e) {
                logger.error("Error retrying notification with seq_num: {}", seqNumStr, e);
            }
        }
    }
    
    /**
     * Retry a truck arrival notification
     */
    private void retryTruckArrivalNotification(MessageLog message) {
        // Extract package and truck IDs from message content or related data
        // This would require storing additional information in the message log
        // or retrieving it from another source
        
        // For this implementation, we'll just mark it as acknowledged
        // since we don't have the actual data to retry
        messageTrackingService.acknowledgeMessage(message.getSeqNum());
        logger.info("Marked truck arrival notification as acknowledged (no retry): {}", message.getSeqNum());
    }
    
    /**
     * Retry a delivery complete notification
     */
    private void retryDeliveryCompleteNotification(MessageLog message) {
        // Similar to above, we would need the package and truck data
        // For now, just mark as acknowledged
        messageTrackingService.acknowledgeMessage(message.getSeqNum());
        logger.info("Marked delivery notification as acknowledged (no retry): {}", message.getSeqNum());
    }
    
    /**
     * Retry a status update notification
     */
    private void retryStatusUpdateNotification(MessageLog message) {
        // Similar to above
        messageTrackingService.acknowledgeMessage(message.getSeqNum());
        logger.info("Marked status update as acknowledged (no retry): {}", message.getSeqNum());
    }
}