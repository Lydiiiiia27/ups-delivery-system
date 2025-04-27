package com.ups.repository;

import com.ups.model.MessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for accessing MessageLog entities
 */
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
    
    /**
     * Find a message log by sequence number
     */
    MessageLog findBySeqNum(Long seqNum);
    
    /**
     * Find all message logs that have not been acknowledged
     */
    List<MessageLog> findByAcknowledgedIsNullAndDirection(String direction);
    
    /**
     * Find all message logs of a specific type
     */
    List<MessageLog> findByMessageType(String messageType);
} 