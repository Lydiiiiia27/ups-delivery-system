package com.ups.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ups.model.Location;
import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;
import com.ups.model.amazon.ChangeDestinationRequest;
import com.ups.model.amazon.ChangeDestinationResponse;
import com.ups.model.amazon.NotifyTruckArrived;
import com.ups.model.amazon.NotifyDeliveryComplete;
import com.ups.model.amazon.QueryShipmentStatusRequest;
import com.ups.model.amazon.QueryShipmentStatusResponse;
import com.ups.model.amazon.UpdateShipmentStatus;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.repository.PackageRepository;
import com.ups.service.ShipmentService;
import com.ups.service.MessageTrackingService;
import com.ups.service.world.Ups;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AmazonApiControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ShipmentService shipmentService;
    
    @MockBean
    private MessageTrackingService messageTrackingService;
    
    @MockBean
    private PackageRepository packageRepository;
    
    @MockBean
    private Ups ups;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private Package testPackage;
    private Truck testTruck;
    
    @BeforeEach
    public void setup() {
        testTruck = new Truck();
        testTruck.setId(55);
        testTruck.setStatus(TruckStatus.ARRIVE_WAREHOUSE);
        testTruck.setX(15);
        testTruck.setY(20);
        
        testPackage = new Package();
        testPackage.setId(1001L);
        testPackage.setDestinationX(30);
        testPackage.setDestinationY(40);
        testPackage.setStatus(PackageStatus.ASSIGNED);
        testPackage.setTruck(testTruck);
        
        // Setup MessageTrackingService mock
        when(messageTrackingService.getNextSeqNum()).thenReturn(202L);
        when(messageTrackingService.isMessageProcessed(anyLong())).thenReturn(false);
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
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
    
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testChangeDestination() throws Exception {
        // Create mock request
        ChangeDestinationRequest request = new ChangeDestinationRequest();
        request.setMessageType("ChangeDestinationRequest");
        request.setSeqNum(102L);
        request.setTimestamp(Instant.now());
        request.setPackageId(1001L);
        
        ChangeDestinationRequest.Destination newDestination = new ChangeDestinationRequest.Destination();
        newDestination.setX(50);
        newDestination.setY(60);
        request.setNewDestination(newDestination);
        
        // Mock repository
        when(packageRepository.findById(1001L)).thenReturn(Optional.of(testPackage));
        
        // Test API endpoint
        mockMvc.perform(post("/api/changedestination")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message_type").value("ChangeDestinationResponse"))
                .andExpect(jsonPath("$.status").value("UPDATED"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testQueryShipmentStatus() throws Exception {
        // Create mock request
        QueryShipmentStatusRequest request = new QueryShipmentStatusRequest();
        request.setMessageType("QueryShipmentStatusRequest");
        request.setSeqNum(103L);
        request.setTimestamp(Instant.now());
        request.setPackageId(1001L);
        
        // Test API endpoint
        mockMvc.perform(post("/api/queryshipmentstatus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message_type").value("QueryShipmentStatusResponse"))
                .andExpect(jsonPath("$.package_id").value(1001))
                .andExpect(jsonPath("$.current_status").exists());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testNotifyTruckArrived() throws Exception {
        // Create mock request
        NotifyTruckArrived notification = new NotifyTruckArrived();
        notification.setMessageType("NotifyTruckArrived");
        notification.setSeqNum(104L);
        notification.setTimestamp(Instant.now());
        notification.setPackageId(1001L);
        notification.setTruckId(55);
        notification.setWarehouseId(10);
        
        // Test API endpoint
        mockMvc.perform(post("/api/notifytruckarrived")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(notification)))
                .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testNotifyDeliveryComplete() throws Exception {
        // Create mock request
        NotifyDeliveryComplete notification = new NotifyDeliveryComplete();
        notification.setMessageType("NotifyDeliveryComplete");
        notification.setSeqNum(105L);
        notification.setTimestamp(Instant.now());
        notification.setPackageId(1001L);
        notification.setTruckId(55);
        
        NotifyDeliveryComplete.Location location = new NotifyDeliveryComplete.Location(30, 40);
        notification.setFinalLocation(location);
        
        // Test API endpoint
        mockMvc.perform(post("/api/notifydeliverycomplete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(notification)))
                .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    public void testUpdateShipmentStatus() throws Exception {
        // Create mock request
        UpdateShipmentStatus update = new UpdateShipmentStatus();
        update.setMessageType("UpdateShipmentStatus");
        update.setSeqNum(106L);
        update.setTimestamp(Instant.now());
        update.setPackageId(1001L);
        update.setTruckId(55);
        update.setStatus("IN_TRANSIT");
        update.setDetails("Package is on the way");
        
        UpdateShipmentStatus.Location location = new UpdateShipmentStatus.Location(15, 20);
        update.setCurrentLocation(location);
        
        // Test API endpoint
        mockMvc.perform(post("/api/updateshipmentstatus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }
}