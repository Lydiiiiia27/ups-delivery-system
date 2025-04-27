package com.ups.service;

import com.ups.model.CreateShipmentRequest;
import com.ups.model.CreateShipmentResponse;

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