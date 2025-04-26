package com.ups.service.impl;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;
import com.ups.service.ShipmentService;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class ShipmentServiceImpl implements ShipmentService {
    
    private static long nextSeqNum = 200;
    
    @Override
    public CreateShipmentResponse processShipmentRequest(CreateShipmentRequest request) {
        CreateShipmentResponse response = new CreateShipmentResponse();
        response.setMessageType("CreateShipmentResponse");
        response.setSeqNum(++nextSeqNum);
        response.setAck(request.getSeqNum());
        response.setTimestamp(Instant.now());
        
        // For now, always accept the shipment with a mock truck ID
        response.setStatus("ACCEPTED");
        response.setTruckId(55);
        
        return response;
    }
}