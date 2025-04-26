package com.ups.model.amazon;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class UPSGeneralError {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("seq_num")
    private Long seqNum;
    
    private Instant timestamp;
    
    @JsonProperty("error_code")
    private Integer errorCode;
    
    @JsonProperty("error_msg")
    private String errorMsg;
    
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
    
    public Integer getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMsg() {
        return errorMsg;
    }
    
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}