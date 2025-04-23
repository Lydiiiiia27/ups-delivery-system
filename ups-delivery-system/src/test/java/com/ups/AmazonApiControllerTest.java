package com.ups.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;
import com.ups.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AmazonApiController.class)
public class AmazonApiControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ShipmentService shipmentService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    public void testCreateShipment() throws Exception {
        // Create mock request
        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setMessageType("CreateShipmentRequest");
        request.setSeqNum(101L);
        request.setTimestamp(Instant.now());
        
        CreateShipmentRequest.ShipmentInfo shipmentInfo = new CreateShipmentRequest.ShipmentInfo();
        shipmentInfo.setPackageId(1001L);
        shipmentInfo.setWarehouseId(10);
        
        CreateShipmentRequest.Destination destination = new CreateShipmentRequest.Destination();
        destination.setX(3);
        destination.setY(5);
        shipmentInfo.setDestination(destination);
        
        request.setShipmentInfo(shipmentInfo);
        
        // Create mock response
        CreateShipmentResponse response = new CreateShipmentResponse();
        response.setMessageType("CreateShipmentResponse");
        response.setSeqNum(202L);
        response.setAck(101L);
        response.setTimestamp(Instant.now());
        response.setStatus("ACCEPTED");
        response.setTruckId(55);
        
        // Mock service
        when(shipmentService.processShipmentRequest(any(CreateShipmentRequest.class))).thenReturn(response);
        
        // Test API endpoint
        mockMvc.perform(post("/api/createshipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message_type").value("CreateShipmentResponse"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.truck_id").value(55));
    }
}