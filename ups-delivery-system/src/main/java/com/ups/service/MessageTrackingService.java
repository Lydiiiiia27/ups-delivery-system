package com.ups.service;

import com.ups.model.MessageLog;
import com.ups.repository.MessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking messages sent to and received from Amazon
 */
@Service
public class MessageTrackingService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageTrackingService.class);
    
    private final MessageLogRepository messageLogRepository;
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    
    // Cache for processed messages to avoid duplicate processing
    private final Map<Long, Boolean> processedMessages = new ConcurrentHashMap<>();
    
    @Autowired
    public MessageTrackingService(MessageLogRepository messageLogRepository) {
        this.messageLogRepository = messageLogRepository;
    }
    
    /**
     * Get the next sequence number for outgoing messages
     * @return The next sequence number
     */
    public long getNextSeqNum() {
        return sequenceNumber.incrementAndGet();
    }
    
    /**
     * Record an outgoing message
     * @param seqNum The sequence number of the message
     * @param messageType The type of message
     */
    public void recordOutgoingMessage(long seqNum, String messageType) {
        MessageLog log = new MessageLog();
        log.setSeqNum(seqNum);
        log.setMessageType(messageType);
        log.setDirection("OUTGOING");
        log.setTimestamp(Instant.now());
        
        messageLogRepository.save(log);
        logger.debug("Recorded outgoing message: type={}, seq={}", messageType, seqNum);
    }
    
    /**
     * Record an incoming message
     * @param seqNum The sequence number of the message
     * @param messageType The type of message
     */
    public void recordIncomingMessage(long seqNum, String messageType) {
        MessageLog log = new MessageLog();
        log.setSeqNum(seqNum);
        log.setMessageType(messageType);
        log.setDirection("INCOMING");
        log.setTimestamp(Instant.now());
        
        messageLogRepository.save(log);
        logger.debug("Recorded incoming message: type={}, seq={}", messageType, seqNum);
    }
    
    /**
     * Acknowledge a message
     * @param seqNum The sequence number of the message to acknowledge
     * @return true if the message was found and acknowledged, false otherwise
     */
    public boolean acknowledgeMessage(long seqNum) {
        MessageLog log = messageLogRepository.findBySeqNum(seqNum);
        if (log != null) {
            log.setAcknowledged(Instant.now());
            messageLogRepository.save(log);
            logger.debug("Acknowledged message with seq_num: {}", seqNum);
            return true;
        }
        
        logger.warn("Attempted to acknowledge unknown message with seq_num: {}", seqNum);
        return false;
    }
    
    /**
     * Get unacknowledged messages
     * @return List of unacknowledged messages
     */
    public List<MessageLog> getUnacknowledgedMessages() {
        return messageLogRepository.findByAcknowledgedIsNullAndDirection("OUTGOING");
    }
    
    /**
     * Check if a message has already been processed
     * @param seqNum The sequence number to check
     * @return true if the message has been processed, false otherwise
     */
    public boolean isMessageProcessed(Long seqNum) {
        return processedMessages.containsKey(seqNum);
    }
    
    /**
     * Mark a message as processed
     * @param seqNum The sequence number of the processed message
     * @param messageType The type of message
     */
    public void markMessageProcessed(Long seqNum, String messageType) {
        processedMessages.put(seqNum, Boolean.TRUE);
        logger.debug("Marked message as processed: {} with seq_num: {}", messageType, seqNum);
        
        // Also record this as an incoming message
        recordIncomingMessage(seqNum, messageType);
    }
    
    /**
     * Clean up old message logs
     */
    @Scheduled(cron = "0 0 0 * * *") // Run at midnight every day
    public void cleanupOldLogs() {
        // Delete logs older than 7 days
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<MessageLog> oldLogs = messageLogRepository.findByTimestampBefore(cutoff);
        
        if (!oldLogs.isEmpty()) {
            messageLogRepository.deleteAll(oldLogs);
            logger.info("Cleaned up {} old message logs", oldLogs.size());
        }
    }
    
    /**
     * Clean up the processed messages cache
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupProcessedMessagesCache() {
        int size = processedMessages.size();
        if (size > 10000) {
            logger.info("Cleaning up processed messages cache, size before: {}", size);
            processedMessages.clear();
            logger.info("Processed messages cache cleared");
        }
    }
}