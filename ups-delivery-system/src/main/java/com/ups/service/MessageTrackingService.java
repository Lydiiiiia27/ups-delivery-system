package com.ups.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to track message sequence numbers and handle acknowledgments
 * for the Amazon-UPS communication protocol.
 */
@Service
public class MessageTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(MessageTrackingService.class);
    
    // Starting sequence number for outgoing messages
    private static final long INITIAL_SEQ_NUM = 1000;
    
    // Generator for sequence numbers
    private final AtomicLong seqNumGenerator = new AtomicLong(INITIAL_SEQ_NUM);
    
    // Map to track outgoing messages that haven't been acknowledged
    private final Map<Long, MessageRecord> pendingAcks = new ConcurrentHashMap<>();
    
    // Map to track received message sequence numbers to avoid processing duplicates
    private final Map<Long, Boolean> processedMessages = new ConcurrentHashMap<>();
    
    /**
     * Get the next sequence number for an outgoing message.
     * 
     * @return the next sequence number
     */
    public long getNextSeqNum() {
        return seqNumGenerator.getAndIncrement();
    }
    
    /**
     * Record an outgoing message waiting for acknowledgment.
     * 
     * @param seqNum the sequence number of the message
     * @param messageType the type of message sent
     * @return the recorded message
     */
    public MessageRecord recordOutgoingMessage(long seqNum, String messageType) {
        MessageRecord record = new MessageRecord(seqNum, messageType, Instant.now());
        pendingAcks.put(seqNum, record);
        logger.debug("Recorded outgoing message: {} with seq_num: {}", messageType, seqNum);
        return record;
    }
    
    /**
     * Process an acknowledgment received from Amazon.
     * 
     * @param ackNum the acknowledgment number
     * @return true if the acknowledgment was for a pending message, false otherwise
     */
    public boolean processAcknowledgment(long ackNum) {
        MessageRecord record = pendingAcks.remove(ackNum);
        if (record != null) {
            logger.debug("Received acknowledgment for message: {} with seq_num: {}", 
                    record.messageType, ackNum);
            return true;
        } else {
            logger.warn("Received acknowledgment for unknown seq_num: {}", ackNum);
            return false;
        }
    }
    
    /**
     * Check if a received message has already been processed.
     * 
     * @param seqNum the sequence number to check
     * @return true if the message has already been processed, false otherwise
     */
    public boolean isMessageProcessed(long seqNum) {
        return processedMessages.containsKey(seqNum);
    }
    
    /**
     * Mark a received message as processed.
     * 
     * @param seqNum the sequence number of the processed message
     * @param messageType the type of message processed
     */
    public void markMessageProcessed(long seqNum, String messageType) {
        processedMessages.put(seqNum, Boolean.TRUE);
        logger.debug("Marked message as processed: {} with seq_num: {}", messageType, seqNum);
    }
    
    /**
     * Get all pending messages that haven't been acknowledged.
     * 
     * @return a map of sequence numbers to message records
     */
    public Map<Long, MessageRecord> getPendingAcks() {
        return new ConcurrentHashMap<>(pendingAcks);
    }
    
    /**
     * Clean up old processed message records to prevent memory leaks.
     * Should be called periodically by a scheduled task.
     * 
     * @param maxAgeMinutes the maximum age of records to keep in minutes
     */
    public void cleanupOldRecords(int maxAgeMinutes) {
        Instant cutoff = Instant.now().minusSeconds(maxAgeMinutes * 60L);
        
        // Clean up old pending acknowledgments
        pendingAcks.entrySet().removeIf(entry -> 
            entry.getValue().timestamp.isBefore(cutoff));
        
        // For now, we're keeping processed message records indefinitely
        // In a production system, you might want to clean these up as well
        // or use a time-based cache like Caffeine
    }
    
    /**
     * Record class to store information about outgoing messages.
     */
    public static class MessageRecord {
        private final long seqNum;
        private final String messageType;
        private final Instant timestamp;
        
        public MessageRecord(long seqNum, String messageType, Instant timestamp) {
            this.seqNum = seqNum;
            this.messageType = messageType;
            this.timestamp = timestamp;
        }
        
        public long getSeqNum() {
            return seqNum;
        }
        
        public String getMessageType() {
            return messageType;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }
}