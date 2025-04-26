package com.ups.model.amazon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryShipmentStatusResponse {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("seq_num")
    private Long seqNum;
    
    private Long ack;
    
    private Instant timestamp;
    
    @JsonProperty("package_id")
    private Long packageId;
    
    @JsonProperty("truck_id")
    private Integer truckId;
    
    @JsonProperty("current_status")
    private String currentStatus;
    
    @JsonProperty("current_location")
    private Location currentLocation;
    
    @JsonProperty("expected_delivery_time")
    private Instant expectedDeliveryTime;
    
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
    
    public Long getAck() {
        return ack;
    }
    
    public void setAck(Long ack) {
        this.ack = ack;
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
    
    public String getCurrentStatus() {
        return currentStatus;
    }
    
    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }
    
    public Location getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public Instant getExpectedDeliveryTime() {
        return expectedDeliveryTime;
    }
    
    public void setExpectedDeliveryTime(Instant expectedDeliveryTime) {
        this.expectedDeliveryTime = expectedDeliveryTime;
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