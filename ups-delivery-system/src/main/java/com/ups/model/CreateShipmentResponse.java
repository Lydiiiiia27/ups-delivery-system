package com.ups.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateShipmentResponse {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("seq_num")
    private Long seqNum;
    
    private Long ack;
    
    private Instant timestamp;
    
    private String status;
    
    @JsonProperty("truck_id")
    private Integer truckId;
    
    private String error;
    
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getTruckId() {
        return truckId;
    }
    
    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
}