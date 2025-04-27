package com.ups.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entity for tracking all messages sent to and received from Amazon
 */
@Entity
@Table(name = "message_logs", indexes = {
    @Index(name = "idx_message_log_seq_num", columnList = "seqNum"),
    @Index(name = "idx_message_log_timestamp", columnList = "timestamp"),
    @Index(name = "idx_message_log_direction", columnList = "direction")
})
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long seqNum;
    private String messageType;
    
    // Direction: "OUTGOING" or "INCOMING"
    private String direction;
    
    // When the message was sent or received
    private Instant timestamp;
    
    // When the message was acknowledged
    private Instant acknowledged;
    
    // Optional error message
    private String errorMessage;
    
    // Default constructor
    public MessageLog() {
    }
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getSeqNum() {
        return seqNum;
    }
    
    public void setSeqNum(Long seqNum) {
        this.seqNum = seqNum;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public String getDirection() {
        return direction;
    }
    
    public void setDirection(String direction) {
        this.direction = direction;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public Instant getAcknowledged() {
        return acknowledged;
    }
    
    public void setAcknowledged(Instant acknowledged) {
        this.acknowledged = acknowledged;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    @Override
    public String toString() {
        return "MessageLog{" +
                "id=" + id +
                ", seqNum=" + seqNum +
                ", messageType='" + messageType + '\'' +
                ", direction='" + direction + '\'' +
                ", timestamp=" + timestamp +
                ", acknowledged=" + acknowledged +
                '}';
    }
}