package com.ups.service.world;

import com.ups.WorldUpsProto;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.model.entity.Warehouse;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import com.ups.repository.WarehouseRepository;
import com.ups.service.AmazonNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class WorldResponseHandlerIntegrationTest {

    private WorldResponseHandler responseHandler;

    @Mock
    private TruckRepository truckRepository;

    @Mock
    private PackageRepository packageRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private AmazonNotificationService amazonNotificationService;

    private Package testPackage;
    private Truck testTruck;
    private Warehouse testWarehouse;

    @BeforeEach
    void setUp() {
        responseHandler = new WorldResponseHandler(
                truckRepository,
                packageRepository,
                warehouseRepository,
                amazonNotificationService
        );

        // Setup test entities
        testTruck = new Truck();
        testTruck.setId(1);
        testTruck.setX(10);
        testTruck.setY(10);
        testTruck.setStatus(TruckStatus.TRAVELING);

        testPackage = new Package();
        testPackage.setId(101L);
        testPackage.setStatus(PackageStatus.ASSIGNED);
        testPackage.setTruck(testTruck);
        testPackage.setDestinationX(20);
        testPackage.setDestinationY(20);

        testWarehouse = new Warehouse();
        testWarehouse.setId(201);
        testWarehouse.setX(10);
        testWarehouse.setY(10);

        // Mock repository responses
        when(truckRepository.findById(testTruck.getId())).thenReturn(Optional.of(testTruck));
        when(packageRepository.findById(testPackage.getId())).thenReturn(Optional.of(testPackage));
        when(warehouseRepository.findAll()).thenReturn(List.of(testWarehouse));
    }

    @Test
    void testTruckArrivalNotification() {
        // Arrange
        WorldUpsProto.UFinished completion = WorldUpsProto.UFinished.newBuilder()
                .setTruckid(testTruck.getId())
                .setStatus("arrive warehouse")
                .setX(testWarehouse.getX())
                .setY(testWarehouse.getY())
                .build();

        WorldUpsProto.UResponses responses = WorldUpsProto.UResponses.newBuilder()
                .addCompletions(completion)
                .build();

        List<Package> assignedPackages = new ArrayList<>();
        assignedPackages.add(testPackage);
        when(packageRepository.findByTruckAndStatus(any(Truck.class), eq(PackageStatus.ASSIGNED)))
                .thenReturn(assignedPackages);

        // Act
        responseHandler.queueResponse(responses);
        responseHandler.processResponses(); // This is usually run in a separate thread

        // Assert
        verify(truckRepository).findById(testTruck.getId());
        verify(packageRepository).findByTruckAndStatus(any(Truck.class), eq(PackageStatus.ASSIGNED));
        verify(amazonNotificationService).notifyTruckArrival(testPackage, testTruck, testWarehouse);
        
        // Verify package status update
        verify(testPackage).setStatus(PackageStatus.PICKUP_READY);
        verify(packageRepository).save(testPackage);
    }

    @Test
    void testDeliveryNotification() {
        // Arrange
        WorldUpsProto.UDeliveryMade delivery = WorldUpsProto.UDeliveryMade.newBuilder()
                .setPackageid(testPackage.getId())
                .setTruckid(testTruck.getId())
                .build();

        WorldUpsProto.UResponses responses = WorldUpsProto.UResponses.newBuilder()
                .addDelivered(delivery)
                .build();

        // Act
        responseHandler.queueResponse(responses);
        responseHandler.processResponses(); // This is usually run in a separate thread

        // Assert
        verify(packageRepository).findById(testPackage.getId());
        verify(truckRepository).findById(testTruck.getId());
        verify(amazonNotificationService).notifyDeliveryComplete(testPackage, testTruck);
        verify(amazonNotificationService).sendStatusUpdate(
                eq(testPackage), 
                eq(testTruck), 
                eq("DELIVERED"), 
                anyString()
        );
        
        // Verify package status update
        verify(testPackage).setStatus(PackageStatus.DELIVERED);
        verify(packageRepository).save(testPackage);
    }

    @Test
    void testTruckStatusUpdateNotification() {
        // Arrange
        WorldUpsProto.UTruck truckStatus = WorldUpsProto.UTruck.newBuilder()
                .setTruckid(testTruck.getId())
                .setStatus("delivering")
                .setX(15)
                .setY(15)
                .build();

        WorldUpsProto.UResponses responses = WorldUpsProto.UResponses.newBuilder()
                .addTruckstatus(truckStatus)
                .build();

        List<Package> truckPackages = new ArrayList<>();
        truckPackages.add(testPackage);
        when(packageRepository.findByTruck(any(Truck.class))).thenReturn(truckPackages);

        // Act
        responseHandler.queueResponse(responses);
        responseHandler.processResponses(); // This is usually run in a separate thread

        // Assert
        verify(truckRepository).findById(testTruck.getId());
        verify(packageRepository).findByTruck(any(Truck.class));
        verify(amazonNotificationService).sendStatusUpdate(
                eq(testPackage), 
                eq(testTruck), 
                eq("DELIVERING"), 
                anyString()
        );
        
        // Verify truck status update
        verify(testTruck).setStatus(TruckStatus.DELIVERING);
        verify(truckRepository).save(testTruck);
    }

    @Test
    void testErrorNotification() {
        // Arrange
        WorldUpsProto.UErr error = WorldUpsProto.UErr.newBuilder()
                .setErr("Test error")
                .setOriginseqnum(123)
                .build();

        WorldUpsProto.UResponses responses = WorldUpsProto.UResponses.newBuilder()
                .addError(error)
                .build();

        List<Package> allPackages = new ArrayList<>();
        allPackages.add(testPackage);
        when(packageRepository.findAll()).thenReturn(allPackages);

        // Act
        responseHandler.queueResponse(responses);
        responseHandler.processResponses(); // This is usually run in a separate thread

        // Assert
        verify(packageRepository).findAll();
        verify(amazonNotificationService).sendStatusUpdate(
                eq(testPackage), 
                eq(testTruck), 
                eq("ERROR"), 
                anyString()
        );
    }

    @Test
    void testSimulationFinishedNotification() {
        // Arrange
        WorldUpsProto.UResponses responses = WorldUpsProto.UResponses.newBuilder()
                .setFinished(true)
                .build();

        List<Package> allPackages = new ArrayList<>();
        allPackages.add(testPackage);
        when(packageRepository.findAll()).thenReturn(allPackages);

        // Act
        responseHandler.queueResponse(responses);
        responseHandler.processResponses(); // This is usually run in a separate thread

        // Assert
        verify(packageRepository).findAll();
        verify(amazonNotificationService).sendStatusUpdate(
                eq(testPackage), 
                eq(testTruck), 
                eq("FAILED"), 
                anyString()
        );
        
        // Verify package status update
        verify(testPackage).setStatus(PackageStatus.FAILED);
        verify(packageRepository).save(testPackage);
    }
} 