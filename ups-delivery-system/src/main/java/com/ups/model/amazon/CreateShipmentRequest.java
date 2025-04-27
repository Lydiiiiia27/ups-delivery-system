// ups-delivery-system/src/main/java/com/ups/model/amazon/CreateShipmentRequest.java
package com.ups.model.amazon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateShipmentRequest {
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("seq_num")
    private Long seqNum;
    
    private Instant timestamp;
    
    @JsonProperty("shipment_info")
    private ShipmentInfo shipmentInfo;
    
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
    
    public ShipmentInfo getShipmentInfo() {
        return shipmentInfo;
    }
    
    public void setShipmentInfo(ShipmentInfo shipmentInfo) {
        this.shipmentInfo = shipmentInfo;
    }
    
    public static class ShipmentInfo {
        @JsonProperty("package_id")
        private Long packageId;
        
        @JsonProperty("warehouse_id")
        private Integer warehouseId;
        
        private Destination destination;
        
        @JsonProperty("ups_account_name")
        private String upsAccountName;
        
        private List<Item> items;
        
        // Getters and setters
        public Long getPackageId() {
            return packageId;
        }
        
        public void setPackageId(Long packageId) {
            this.packageId = packageId;
        }
        
        public Integer getWarehouseId() {
            return warehouseId;
        }
        
        public void setWarehouseId(Integer warehouseId) {
            this.warehouseId = warehouseId;
        }
        
        public Destination getDestination() {
            return destination;
        }
        
        public void setDestination(Destination destination) {
            this.destination = destination;
        }
        
        public String getUpsAccountName() {
            return upsAccountName;
        }
        
        public void setUpsAccountName(String upsAccountName) {
            this.upsAccountName = upsAccountName;
        }
        
        public List<Item> getItems() {
            return items;
        }
        
        public void setItems(List<Item> items) {
            this.items = items;
        }
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
    
    public static class Item {
        @JsonProperty("product_id")
        private Long productId;
        
        private String description;
        
        private Integer count;
        
        // Getters and setters
        public Long getProductId() {
            return productId;
        }
        
        public void setProductId(Long productId) {
            this.productId = productId;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public Integer getCount() {
            return count;
        }
        
        public void setCount(Integer count) {
            this.count = count;
        }
    }
}