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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class WorldResponseHandler {
    private static final Logger logger = LoggerFactory.getLogger(WorldResponseHandler.class);
    
    private final TruckRepository truckRepository;
    private final PackageRepository packageRepository;
    private final WarehouseRepository warehouseRepository;
    private final AmazonNotificationService amazonNotificationService;
    private final BlockingQueue<WorldUpsProto.UResponses> responseQueue;
    private volatile boolean running = true;
    
    @Autowired
    public WorldResponseHandler(TruckRepository truckRepository, 
                                PackageRepository packageRepository,
                                WarehouseRepository warehouseRepository,
                                AmazonNotificationService amazonNotificationService) {
        this.truckRepository = truckRepository;
        this.packageRepository = packageRepository;
        this.warehouseRepository = warehouseRepository;
        this.amazonNotificationService = amazonNotificationService;
        this.responseQueue = new LinkedBlockingQueue<>();
    }
    
    /**
     * Adds a response to the processing queue
     */
    public void queueResponse(WorldUpsProto.UResponses response) {
        try {
            responseQueue.put(response);
            logger.debug("Queued response for processing");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while queuing response", e);
        }
    }
    
    /**
     * Processes responses from the queue
     */
    @Transactional
    public void processResponses() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                WorldUpsProto.UResponses response = responseQueue.take();
                processResponse(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Response processor interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error processing response", e);
            }
        }
    }
    
    private void processResponse(WorldUpsProto.UResponses response) {
        // Process completions
        for (WorldUpsProto.UFinished completion : response.getCompletionsList()) {
            processCompletion(completion);
        }
        
        // Process deliveries
        for (WorldUpsProto.UDeliveryMade delivery : response.getDeliveredList()) {
            processDelivery(delivery);
        }
        
        // Process truck status updates
        for (WorldUpsProto.UTruck truckStatus : response.getTruckstatusList()) {
            processTruckStatus(truckStatus);
        }
        
        // Process errors
        for (WorldUpsProto.UErr error : response.getErrorList()) {
            processError(error);
        }
        
        // Handle finished flag
        if (response.hasFinished() && response.getFinished()) {
            logger.info("World simulation finished");
            
            // Notify Amazon about all incomplete packages
            notifySimulationFinished();
            
            // Stop the response handler
            stop();
        }
    }
    
    private void processCompletion(WorldUpsProto.UFinished completion) {
        logger.info("Processing completion for truck {} at ({},{}) with status {}", 
                completion.getTruckid(), completion.getX(), completion.getY(), completion.getStatus());
        
        Optional<Truck> truckOpt = truckRepository.findById(completion.getTruckid());
        if (truckOpt.isPresent()) {
            Truck truck = truckOpt.get();
            truck.setX(completion.getX());
            truck.setY(completion.getY());
            
            // Update truck status based on completion status
            switch (completion.getStatus().toLowerCase()) {
                case "arrive warehouse":
                    truck.setStatus(TruckStatus.ARRIVE_WAREHOUSE);
                    
                    // Find packages assigned to this truck
                    List<Package> packages = packageRepository.findByTruckAndStatus(truck, PackageStatus.ASSIGNED);
                    if (!packages.isEmpty()) {
                        // Find the warehouse
                        Optional<Warehouse> warehouseOpt = findNearestWarehouse(completion.getX(), completion.getY());
                        if (warehouseOpt.isPresent()) {
                            Warehouse warehouse = warehouseOpt.get();
                            // Notify Amazon for each package
                            for (Package pkg : packages) {
                                amazonNotificationService.notifyTruckArrival(pkg, truck, warehouse);
                                // Update package status
                                pkg.setStatus(PackageStatus.PICKUP_READY);
                                packageRepository.save(pkg);
                            }
                            logger.info("Notified Amazon about truck {} arrival at warehouse {}", 
                                    truck.getId(), warehouse.getId());
                        } else {
                            logger.error("Could not find warehouse near coordinates ({},{})", 
                                    completion.getX(), completion.getY());
                        }
                    }
                    break;
                case "idle":
                    truck.setStatus(TruckStatus.IDLE);
                    break;
                default:
                    logger.warn("Unknown completion status: {}", completion.getStatus());
            }
            
            truckRepository.save(truck);
            logger.info("Updated truck {} status to {}", truck.getId(), truck.getStatus());
        } else {
            logger.error("Truck {} not found for completion update", completion.getTruckid());
        }
    }
    
    private void processDelivery(WorldUpsProto.UDeliveryMade delivery) {
        logger.info("Processing delivery for package {} by truck {}", 
                delivery.getPackageid(), delivery.getTruckid());
        
        Optional<Package> packageOpt = packageRepository.findById(delivery.getPackageid());
        Optional<Truck> truckOpt = truckRepository.findById(delivery.getTruckid());
        
        if (packageOpt.isPresent() && truckOpt.isPresent()) {
            Package pkg = packageOpt.get();
            Truck truck = truckOpt.get();
            
            pkg.setStatus(PackageStatus.DELIVERED);
            packageRepository.save(pkg);
            logger.info("Marked package {} as delivered", pkg.getId());
            
            // Notify Amazon about successful delivery
            amazonNotificationService.notifyDeliveryComplete(pkg, truck);
            
            // Send a status update about the delivery
            amazonNotificationService.sendStatusUpdate(pkg, truck, "DELIVERED", 
                    "Package " + pkg.getId() + " delivered successfully");
            
            logger.info("Notified Amazon about delivery of package {}", pkg.getId());
        } else {
            if (!packageOpt.isPresent()) {
                logger.error("Package {} not found for delivery update", delivery.getPackageid());
            }
            if (!truckOpt.isPresent()) {
                logger.error("Truck {} not found for delivery update", delivery.getTruckid());
            }
        }
    }
    
    private void processTruckStatus(WorldUpsProto.UTruck truckStatus) {
        logger.info("Processing truck status update for truck {} - status: {}, location: ({},{})", 
                truckStatus.getTruckid(), truckStatus.getStatus(), truckStatus.getX(), truckStatus.getY());
        
        Optional<Truck> truckOpt = truckRepository.findById(truckStatus.getTruckid());
        if (truckOpt.isPresent()) {
            Truck truck = truckOpt.get();
            truck.setX(truckStatus.getX());
            truck.setY(truckStatus.getY());
            
            // Map world status to our TruckStatus enum
            TruckStatus oldStatus = truck.getStatus();
            TruckStatus newStatus = oldStatus;
            
            switch (truckStatus.getStatus().toLowerCase()) {
                case "idle":
                    newStatus = TruckStatus.IDLE;
                    break;
                case "traveling":
                    newStatus = TruckStatus.TRAVELING;
                    break;
                case "arrive warehouse":
                    newStatus = TruckStatus.ARRIVE_WAREHOUSE;
                    break;
                case "loading":
                    newStatus = TruckStatus.LOADING;
                    break;
                case "delivering":
                    newStatus = TruckStatus.DELIVERING;
                    break;
                default:
                    logger.warn("Unknown truck status: {}", truckStatus.getStatus());
            }
            
            // If status has changed, update Amazon
            if (oldStatus != newStatus) {
                truck.setStatus(newStatus);
                
                // Find packages associated with this truck and update Amazon
                List<Package> packages = packageRepository.findByTruck(truck);
                for (Package pkg : packages) {
                    amazonNotificationService.sendStatusUpdate(pkg, truck, 
                            truck.getStatus().toString(), 
                            "Truck " + truck.getId() + " status changed to " + truck.getStatus());
                }
            }
            
            truckRepository.save(truck);
        } else {
            logger.error("Truck {} not found for status update", truckStatus.getTruckid());
        }
    }
    
    private void processError(WorldUpsProto.UErr error) {
        logger.error("World error for sequence {}: {}", error.getOriginseqnum(), error.getErr());
        
        // Notify packages that might be affected by this error
        List<Package> packages = packageRepository.findAll();
        for (Package pkg : packages) {
            // Only notify for packages that are in transit or being processed
            if (pkg.getStatus() == PackageStatus.ASSIGNED || 
                pkg.getStatus() == PackageStatus.OUT_FOR_DELIVERY || 
                pkg.getStatus() == PackageStatus.LOADING) {
                
                // Get the truck if available
                Truck truck = pkg.getTruck();
                
                // Send status update with error information
                amazonNotificationService.sendStatusUpdate(
                    pkg, 
                    truck, 
                    "ERROR", 
                    "Operation error: " + error.getErr() + " (seq: " + error.getOriginseqnum() + ")"
                );
                
                logger.info("Sent error notification to Amazon for package {}", pkg.getId());
            }
        }
        
        // TODO: Implement retry mechanism for specific operations if needed
    }
    
    /**
     * Find the nearest warehouse to the given coordinates
     */
    private Optional<Warehouse> findNearestWarehouse(int x, int y) {
        List<Warehouse> warehouses = warehouseRepository.findAll();
        if (warehouses.isEmpty()) {
            return Optional.empty();
        }
        
        Warehouse nearest = warehouses.get(0);
        int minDistance = distance(x, y, nearest.getX(), nearest.getY());
        
        for (int i = 1; i < warehouses.size(); i++) {
            Warehouse warehouse = warehouses.get(i);
            int d = distance(x, y, warehouse.getX(), warehouse.getY());
            if (d < minDistance) {
                minDistance = d;
                nearest = warehouse;
            }
        }
        
        // Consider only warehouses that are very close (threshold of 10 units)
        if (minDistance <= 10) {
            return Optional.of(nearest);
        }
        
        return Optional.empty();
    }
    
    /**
     * Calculate Euclidean distance between two points
     */
    private int distance(int x1, int y1, int x2, int y2) {
        return (int) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
    
    /**
     * Notify Amazon that the simulation has finished and handle any incomplete deliveries
     */
    private void notifySimulationFinished() {
        // Find all packages that are not in terminal state
        List<Package> incompletePackages = packageRepository.findAll().stream()
                .filter(pkg -> pkg.getStatus() != PackageStatus.DELIVERED && pkg.getStatus() != PackageStatus.FAILED)
                .toList();
        
        for (Package pkg : incompletePackages) {
            // Mark the package as failed
            pkg.setStatus(PackageStatus.FAILED);
            packageRepository.save(pkg);
            
            // Get the truck if available
            Truck truck = pkg.getTruck();
            
            // Notify Amazon
            amazonNotificationService.sendStatusUpdate(
                pkg,
                truck,
                "FAILED",
                "Delivery failed because simulation ended before completion"
            );
            
            logger.info("Marked package {} as FAILED due to simulation end", pkg.getId());
        }
        
        logger.info("Processed {} incomplete packages at simulation end", incompletePackages.size());
    }
    
    public void stop() {
        running = false;
    }
}