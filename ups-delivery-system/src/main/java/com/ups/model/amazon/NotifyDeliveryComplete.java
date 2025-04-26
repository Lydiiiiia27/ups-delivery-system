package com.ups.model.amazon;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class NotifyDeliveryComplete {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("seq_num")
    private Long seqNum;
    
    private Instant timestamp;
    
    @JsonProperty("package_id")
    private Long packageId;
    
    @JsonProperty("truck_id")
    private Integer truckId;
    
    @JsonProperty("final_location")
    private Location finalLocation;
    
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
    
    public Integer getTruckId() {
        return truckId;
    }
    
    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }
    
    public Location getFinalLocation() {
        return finalLocation;
    }
    
    public void setFinalLocation(Location finalLocation) {
        this.finalLocation = finalLocation;
    }
    
    public static class Location {
        private Integer x;
        private Integer y;
        
        public Location() {
        }
        
        public Location(Integer x, Integer y) {
            this.x = x;
            this.y = y;
        }
        
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