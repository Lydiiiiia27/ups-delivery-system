package com.ups.repository;

import com.ups.model.MessageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for accessing MessageLog entities
 */
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
    
    /**
     * Find a message log by sequence number
     * @param seqNum The sequence number
     * @return The message log with the specified sequence number, or null if not found
     */
    MessageLog findBySeqNum(Long seqNum);
    
    /**
     * Find message logs without acknowledgement in the specified direction
     * @param direction The message direction ("INCOMING" or "OUTGOING")
     * @return A list of message logs
     */
    List<MessageLog> findByAcknowledgedIsNullAndDirection(String direction);
    
    /**
     * Find message logs by message type
     * @param messageType The message type
     * @return A list of message logs
     */
    List<MessageLog> findByMessageType(String messageType);
    
    /**
     * Find message logs older than the specified timestamp
     * @param timestamp The cutoff timestamp
     * @return A list of message logs
     */
    List<MessageLog> findByTimestampBefore(Instant timestamp);
    
    /**
     * Find IDs of unacknowledged messages in the specified direction
     * @param direction The message direction ("INCOMING" or "OUTGOING")
     * @return A list of message IDs
     */
    @Query("SELECT m.id FROM MessageLog m WHERE m.acknowledged IS NULL AND m.direction = :direction")
    List<String> findUnacknowledgedMessageIds(@Param("direction") String direction);
}