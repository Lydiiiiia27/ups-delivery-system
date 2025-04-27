package com.ups.service.world;

import com.ups.WorldUpsProto;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import com.ups.repository.WarehouseRepository;
import com.ups.service.AmazonNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorldResponseHandlerTest {
    
    @Mock
    private TruckRepository truckRepository;
    
    @Mock
    private PackageRepository packageRepository;
    
    @Mock
    private WarehouseRepository warehouseRepository;
    
    @Mock
    private AmazonNotificationService amazonNotificationService;
    
    private WorldResponseHandler responseHandler;
    private ExecutorService executor;
    
    @BeforeEach
    public void setUp() {
        responseHandler = new WorldResponseHandler(
            truckRepository, 
            packageRepository, 
            warehouseRepository, 
            amazonNotificationService
        );
        executor = Executors.newSingleThreadExecutor();
        // Start the response processor in a separate thread
        executor.submit(() -> responseHandler.processResponses());
    }
    
    @Test
    public void testProcessCompletion_ArriveWarehouse() throws Exception {
        // Create a truck
        Truck truck = new Truck();
        truck.setId(1);
        truck.setStatus(TruckStatus.TRAVELING);
        
        // Mock repository
        when(truckRepository.findById(1)).thenReturn(Optional.of(truck));
        
        // Create a completion response
        WorldUpsProto.UFinished.Builder completionBuilder = WorldUpsProto.UFinished.newBuilder();
        completionBuilder.setTruckid(1);
        completionBuilder.setX(10);
        completionBuilder.setY(20);
        completionBuilder.setStatus("arrive warehouse");
        completionBuilder.setSeqnum(1);
        
        WorldUpsProto.UResponses.Builder responseBuilder = WorldUpsProto.UResponses.newBuilder();
        responseBuilder.addCompletions(completionBuilder.build());
        
        // Process the response
        responseHandler.queueResponse(responseBuilder.build());
        
        // Wait for processing to complete (longer timeout)
        Thread.sleep(500);
        
        // Verify the truck status was updated
        verify(truckRepository, timeout(1000)).findById(1);
        verify(truckRepository, timeout(1000)).save(any(Truck.class));
    }
    
    @Test
    public void testProcessDelivery() throws Exception {
        // Create a package
        Package pkg = new Package();
        pkg.setId(1001L);
        pkg.setStatus(PackageStatus.DELIVERING);
        
        // Create a truck for the notification
        Truck truck = new Truck();
        truck.setId(1);
        
        // Mock repositories
        when(packageRepository.findById(1001L)).thenReturn(Optional.of(pkg));
        when(truckRepository.findById(1)).thenReturn(Optional.of(truck));
        
        // Create a delivery response
        WorldUpsProto.UDeliveryMade.Builder deliveryBuilder = WorldUpsProto.UDeliveryMade.newBuilder();
        deliveryBuilder.setTruckid(1);
        deliveryBuilder.setPackageid(1001L);
        deliveryBuilder.setSeqnum(1);
        
        WorldUpsProto.UResponses.Builder responseBuilder = WorldUpsProto.UResponses.newBuilder();
        responseBuilder.addDelivered(deliveryBuilder.build());
        
        // Process the response
        responseHandler.queueResponse(responseBuilder.build());
        
        // Wait longer for processing to complete
        Thread.sleep(1000);
        
        // Verify the package status was updated
        verify(packageRepository, timeout(2000)).findById(1001L);
        verify(packageRepository, timeout(2000)).save(any(Package.class));
    }
    
    @Test
    public void testProcessTruckStatus() throws Exception {
        // Create a truck
        Truck truck = new Truck();
        truck.setId(1);
        truck.setStatus(TruckStatus.IDLE);
        
        // Mock repository
        when(truckRepository.findById(1)).thenReturn(Optional.of(truck));
        
        // Create a truck status response
        WorldUpsProto.UTruck.Builder truckStatusBuilder = WorldUpsProto.UTruck.newBuilder();
        truckStatusBuilder.setTruckid(1);
        truckStatusBuilder.setStatus("delivering");
        truckStatusBuilder.setX(15);
        truckStatusBuilder.setY(25);
        truckStatusBuilder.setSeqnum(1);
        
        WorldUpsProto.UResponses.Builder responseBuilder = WorldUpsProto.UResponses.newBuilder();
        responseBuilder.addTruckstatus(truckStatusBuilder.build());
        
        // Process the response
        responseHandler.queueResponse(responseBuilder.build());
        
        // Wait for processing to complete (longer timeout)
        Thread.sleep(500);
        
        // Verify the truck status and location were updated
        verify(truckRepository, timeout(1000)).findById(1);
        verify(truckRepository, timeout(1000)).save(any(Truck.class));
    }
    
    @org.junit.jupiter.api.AfterEach
    public void tearDown() throws Exception {
        // Stop the response handler
        responseHandler.stop();
        
        // Shutdown the executor cleanly
        executor.shutdown();
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}