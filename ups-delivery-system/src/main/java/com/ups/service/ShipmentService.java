// ups-delivery-system/src/main/java/com/ups/service/ShipmentService.java
package com.ups.service;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;

/**
 * Service interface for handling shipment-related operations
 */
public interface ShipmentService {
    
    /**
     * Process a shipment request from Amazon
     * @param request The shipment request
     * @return The response to send back to Amazon
     */
    CreateShipmentResponse processShipmentRequest(CreateShipmentRequest request);
}