package com.ups.model.amazon;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class UpdateShipmentStatus {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("seq_num")
    private Long seqNum;
    
    private Instant timestamp;
    
    @JsonProperty("package_id")
    private Long packageId;
    
    @JsonProperty("truck_id")
    private Integer truckId;
    
    private String status;
    
    private String details;
    
    @JsonProperty("current_location")
    private Location currentLocation;
    
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public Location getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
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