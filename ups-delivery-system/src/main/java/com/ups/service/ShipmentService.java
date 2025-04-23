package com.ups.service;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;

public interface ShipmentService {
    /**
     * Process a shipment request from Amazon
     * 
     * @param request the shipment request from Amazon
     * @return the response to send back to Amazon
     */
    CreateShipmentResponse processShipmentRequest(CreateShipmentRequest request);
}