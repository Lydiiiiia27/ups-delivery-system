package com.ups.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ups.model.amazon.NotifyDeliveryComplete;
import com.ups.model.amazon.NotifyTruckArrived;
import com.ups.model.amazon.UpdateShipmentStatus;
import com.ups.model.entity.Package;
import com.ups.model.entity.Truck;
import com.ups.model.entity.Warehouse;
import com.ups.repository.MessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
public class AmazonNotificationServiceTest {

    private AmazonNotificationService amazonNotificationService;

    @Mock
    private MessageTrackingService messageTrackingService;
    
    @Mock
    private MessageLogRepository messageLogRepository;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Configure ObjectMapper to handle Java 8 date/time types
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        amazonNotificationService = new AmazonNotificationService(
                messageTrackingService,
                messageLogRepository,
                restTemplate,
                objectMapper
        );
        
        // Get Amazon URL from environment variable if available
        String amazonUrl = System.getenv("AMAZON_SERVICE_URL");
        if (amazonUrl == null || amazonUrl.isEmpty()) {
            amazonUrl = "http://amazon-mock:8080"; // default fallback
        }
        
        ReflectionTestUtils.setField(amazonNotificationService, "amazonServiceUrl", amazonUrl);
        System.out.println("Using Amazon service URL: " + amazonUrl);
    }

    @Test
    void testNotifyTruckArrival() {
        // Arrange
        Package pkg = new Package();
        pkg.setId(123L);

        Truck truck = new Truck();
        truck.setId(456);
        truck.setX(10);
        truck.setY(10);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(789);
        warehouse.setX(10);
        warehouse.setY(10);
        
        when(messageTrackingService.getNextSeqNum()).thenReturn(1001L);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
            .thenReturn(ResponseEntity.ok().build());

        // Act
        amazonNotificationService.notifyTruckArrival(pkg, truck, warehouse);

        // Assert
        verify(messageTrackingService).recordOutgoingMessage(eq(1001L), eq("NotifyTruckArrived"));
    }

    @Test
    void testNotifyDeliveryComplete() {
        // Arrange
        Package pkg = new Package();
        pkg.setId(123L);
        pkg.setDestinationX(50);
        pkg.setDestinationY(50);

        Truck truck = new Truck();
        truck.setId(456);
        truck.setX(50);
        truck.setY(50);
        
        when(messageTrackingService.getNextSeqNum()).thenReturn(1002L);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
            .thenReturn(ResponseEntity.ok().build());

        // Act
        amazonNotificationService.notifyDeliveryComplete(pkg, truck);

        // Assert
        verify(messageTrackingService).recordOutgoingMessage(eq(1002L), eq("NotifyDeliveryComplete"));
    }

    @Test
    void testSendStatusUpdate() {
        // Arrange
        Package pkg = new Package();
        pkg.setId(123L);

        Truck truck = new Truck();
        truck.setId(456);
        truck.setX(30);
        truck.setY(30);
        
        when(messageTrackingService.getNextSeqNum()).thenReturn(1003L);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
            .thenReturn(ResponseEntity.ok().build());

        // Act
        amazonNotificationService.sendStatusUpdate(pkg, truck, "DELIVERING", "Package on its way");

        // Assert
        verify(messageTrackingService).recordOutgoingMessage(eq(1003L), eq("UpdateShipmentStatus"));
    }

    @Test
    void testSendStatusUpdateWithoutTruck() {
        // Arrange
        Package pkg = new Package();
        pkg.setId(123L);
        
        when(messageTrackingService.getNextSeqNum()).thenReturn(1004L);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
            .thenReturn(ResponseEntity.ok().build());

        // Act
        amazonNotificationService.sendStatusUpdate(pkg, null, "CREATED", "New package created");

        // Assert
        verify(messageTrackingService).recordOutgoingMessage(eq(1004L), eq("UpdateShipmentStatus"));
    }

    @Test
    void testHandleNetworkError() {
        // Arrange
        Package pkg = new Package();
        pkg.setId(123L);

        Truck truck = new Truck();
        truck.setId(456);
        
        when(messageTrackingService.getNextSeqNum()).thenReturn(1005L);

        doThrow(new RuntimeException("Network error"))
            .when(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(Object.class));

        // Act - should not throw exception due to our retry mechanism
        try {
            amazonNotificationService.sendStatusUpdate(pkg, truck, "ERROR", "Test error");
            fail("Should have thrown exception after retry attempts");
        } catch (Exception e) {
            // Expected exception after retries
            assertTrue(e.getMessage().contains("Failed to send notification"));
        }

        // Assert - message tracking should still be called even if notification fails
        verify(messageTrackingService).recordOutgoingMessage(eq(1005L), eq("UpdateShipmentStatus"));
    }
}