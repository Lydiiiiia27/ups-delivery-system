package com.ups.service;

import com.ups.model.MessageLog;
import com.ups.repository.MessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    
    // Map to track processed messages (to avoid duplicate processing)
    private final Map<Long, Boolean> processedMessages = new ConcurrentHashMap<>();
    
    @Autowired
    public MessageTrackingService(MessageLogRepository messageLogRepository) {
        this.messageLogRepository = messageLogRepository;
    }
    
    /**
     * Get the next sequential message number
     */
    public long getNextSeqNum() {
        return sequenceNumber.incrementAndGet();
    }
    
    /**
     * Record an outgoing message to Amazon
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
     * Record an incoming message from Amazon
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
     * Mark a message as acknowledged
     */
    public boolean acknowledgeMessage(long seqNum) {
        MessageLog log = messageLogRepository.findBySeqNum(seqNum);
        if (log != null) {
            log.setAcknowledged(Instant.now());
            messageLogRepository.save(log);
            logger.debug("Acknowledged message with seqNum: {}", seqNum);
            return true;
        }
        
        logger.warn("Tried to acknowledge unknown message with seqNum: {}", seqNum);
        return false;
    }
    
    /**
     * Get all unacknowledged outgoing messages
     */
    public List<MessageLog> getUnacknowledgedMessages() {
        return messageLogRepository.findByAcknowledgedIsNullAndDirection("OUTGOING");
    }
    
    /**
     * Check if a message has already been processed to avoid duplicate processing
     * @param seqNum The sequence number to check
     * @return true if the message has already been processed
     */
    public boolean isMessageProcessed(Long seqNum) {
        return processedMessages.containsKey(seqNum);
    }
    
    /**
     * Mark a message as processed to avoid duplicate processing
     * @param seqNum The sequence number of the processed message
     * @param messageType The type of message processed
     */
    public void markMessageProcessed(Long seqNum, String messageType) {
        processedMessages.put(seqNum, Boolean.TRUE);
        logger.debug("Marked message as processed: {} with seq_num: {}", messageType, seqNum);
        
        // Also record this as an incoming message for tracking
        recordIncomingMessage(seqNum, messageType);
    }
    
    /**
     * Clean up old processed message records to prevent memory leaks.
     * Should be called periodically by a scheduled task.
     * 
     * @param maxAgeMinutes the maximum age of records to keep in minutes
     */
    public void cleanupOldRecords(int maxAgeMinutes) {
        Instant cutoff = Instant.now().minusSeconds(maxAgeMinutes * 60L);
        
        // Find old records in the repository and delete them
        List<MessageLog> oldLogs = messageLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp().isBefore(cutoff) && log.getAcknowledged() != null)
                .toList();
        
        if (!oldLogs.isEmpty()) {
            messageLogRepository.deleteAll(oldLogs);
            logger.info("Cleaned up {} old message logs", oldLogs.size());
        }
    }
}