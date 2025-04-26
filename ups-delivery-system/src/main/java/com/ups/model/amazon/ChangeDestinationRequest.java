package com.ups.model.amazon;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class ChangeDestinationRequest {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("seq_num")
    private Long seqNum;
    
    private Instant timestamp;
    
    @JsonProperty("package_id")
    private Long packageId;
    
    @JsonProperty("new_destination")
    private Destination newDestination;
    
    // Getters and setters
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public Long getSeqNum() {
        return seqNum;
    }
    
    public void setSeqNum(Long seqNum) {
        this.seqNum = seqNum;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public Long getPackageId() {
        return packageId;
    }
    
    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }
    
    public Destination getNewDestination() {
        return newDestination;
    }
    
    public void setNewDestination(Destination newDestination) {
        this.newDestination = newDestination;
    }
    
    public static class Destination {
        private Integer x;
        private Integer y;
        
        // Getters and setters
        public Integer getX() {
            return x;
        }
        
        public void setX(Integer x) {
            this.x = x;
        }
        
        public Integer getY() {
            return y;
        }
        
        public void setY(Integer y) {
            this.y = y;
        }
    }
}