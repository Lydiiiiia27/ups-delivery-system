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
import java.util.stream.Collectors;

/**
 * Handles responses from the World Simulator
 */
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
        if (response == null) {
            logger.warn("Attempted to queue null response");
            return;
        }
        
        try {
            responseQueue.put(response);
            logger.debug("Queued response for processing");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while queuing response", e);
        }
    }
    
    /**
     * Processes responses from the queue in a continuous loop
     */
    public void processResponses() {
        logger.info("Starting World Response Handler processing loop");
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
        logger.info("Exiting World Response Handler processing loop");
    }
    
    /**
     * Process a response from the World Simulator
     */
    @Transactional
    private void processResponse(WorldUpsProto.UResponses response) {
        logger.debug("Processing World Simulator response");
        
        // Process acknowledgements first
        processAcknowledgements(response);
        
        // Process completions (truck arrivals, etc.)
        for (WorldUpsProto.UFinished completion : response.getCompletionsList()) {
            processCompletion(completion);
        }
        
        // Process deliveries (package deliveries)
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
        
        // Handle finished flag (simulation termination)
        if (response.hasFinished() && response.getFinished()) {
            logger.info("World simulation finished");
            handleSimulationFinished();
        }
    }
    
    /**
     * Process acknowledgements from the World Simulator
     */
    private void processAcknowledgements(WorldUpsProto.UResponses response) {
        if (response.getAcksCount() > 0) {
            logger.debug("Received {} acknowledgements", response.getAcksCount());
            // Track acknowledgments if needed
        }
    }
    
    /**
     * Process a completion notification from the World Simulator
     */
    @Transactional
    private void processCompletion(WorldUpsProto.UFinished completion) {
        logger.info("Processing completion for truck {} at ({},{}) with status {}", 
                completion.getTruckid(), completion.getX(), completion.getY(), completion.getStatus());
        
        Optional<Truck> truckOpt = truckRepository.findById(completion.getTruckid());
        if (truckOpt.isPresent()) {
            Truck truck = truckOpt.get();
            
            // Update truck location
            truck.setX(completion.getX());
            truck.setY(completion.getY());
            
            // Update truck status based on completion status
            switch (completion.getStatus().toLowerCase()) {
                case "arrive warehouse":
                    truck.setStatus(TruckStatus.ARRIVE_WAREHOUSE);
                    truckRepository.save(truck);
                    
                    // Notify Amazon that the truck has arrived for packages assigned to this truck
                    processArrivalAtWarehouse(truck, completion);
                    break;
                    
                case "idle":
                    truck.setStatus(TruckStatus.IDLE);
                    truckRepository.save(truck);
                    break;
                
                default:
                    logger.warn("Unknown completion status: {}", completion.getStatus());
                    break;
            }
            
            logger.info("Updated truck {} status to {}", truck.getId(), truck.getStatus());
        } else {
            logger.error("Truck {} not found for completion update", completion.getTruckid());
        }
    }
    
    /**
     * Process truck arrival at warehouse
     */
    private void processArrivalAtWarehouse(Truck truck, WorldUpsProto.UFinished completion) {
        // Find packages assigned to this truck
        List<Package> packages = packageRepository.findByTruckAndStatus(truck, PackageStatus.ASSIGNED);
        if (packages.isEmpty()) {
            logger.info("No packages assigned to truck {} at warehouse", truck.getId());
            return;
        }
        
        // Find the warehouse at current location
        Optional<Warehouse> warehouseOpt = findNearestWarehouse(completion.getX(), completion.getY());
        if (warehouseOpt.isPresent()) {
            Warehouse warehouse = warehouseOpt.get();
            
            // Notify Amazon for each package
            for (Package pkg : packages) {
                // Update package status
                pkg.setStatus(PackageStatus.PICKUP_READY);
                packageRepository.save(pkg);
                
                // Send notification to Amazon
                try {
                    amazonNotificationService.notifyTruckArrival(pkg, truck, warehouse);
                    logger.info("Notified Amazon about truck {} arrival at warehouse {} for package {}", 
                            truck.getId(), warehouse.getId(), pkg.getId());
                } catch (Exception e) {
                    logger.error("Failed to notify Amazon about truck arrival", e);
                }
            }
        } else {
            logger.error("Could not find warehouse near location ({},{})", completion.getX(), completion.getY());
        }
    }
    
    /**
     * Process a delivery notification from the World Simulator
     */
    @Transactional
    private void processDelivery(WorldUpsProto.UDeliveryMade delivery) {
        logger.info("Processing delivery for package {} by truck {}", 
                delivery.getPackageid(), delivery.getTruckid());
        
        Optional<Package> packageOpt = packageRepository.findById(delivery.getPackageid());
        Optional<Truck> truckOpt = truckRepository.findById(delivery.getTruckid());
        
        if (packageOpt.isPresent() && truckOpt.isPresent()) {
            Package pkg = packageOpt.get();
            Truck truck = truckOpt.get();
            
            // Update package status to delivered
            pkg.setStatus(PackageStatus.DELIVERED);
            packageRepository.save(pkg);
            
            // Notify Amazon about delivery completion
            try {
                amazonNotificationService.notifyDeliveryComplete(pkg, truck);
                amazonNotificationService.sendStatusUpdate(pkg, truck, "DELIVERED", 
                        "Package " + pkg.getId() + " delivered successfully");
                logger.info("Notified Amazon about delivery completion for package {}", pkg.getId());
            } catch (Exception e) {
                logger.error("Failed to notify Amazon about delivery completion", e);
            }
        } else {
            if (!packageOpt.isPresent()) {
                logger.error("Package {} not found for delivery update", delivery.getPackageid());
            }
            if (!truckOpt.isPresent()) {
                logger.error("Truck {} not found for delivery update", delivery.getTruckid());
            }
        }
    }
    
    /**
     * Process a truck status update from the World Simulator
     */
    @Transactional
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
            TruckStatus newStatus = mapWorldStatusToTruckStatus(truckStatus.getStatus());
            
            // If status has changed, update packages and notify Amazon
            if (oldStatus != newStatus) {
                truck.setStatus(newStatus);
                updatePackagesForTruckStatusChange(truck, oldStatus, newStatus);
            }
            
            truckRepository.save(truck);
        } else {
            logger.error("Truck {} not found for status update", truckStatus.getTruckid());
        }
    }
    
    /**
     * Map world status string to our TruckStatus enum
     */
    private TruckStatus mapWorldStatusToTruckStatus(String worldStatus) {
        switch (worldStatus.toLowerCase()) {
            case "idle":
                return TruckStatus.IDLE;
            case "traveling":
                return TruckStatus.TRAVELING;
            case "arrive warehouse":
                return TruckStatus.ARRIVE_WAREHOUSE;
            case "loading":
                return TruckStatus.LOADING;
            case "delivering":
                return TruckStatus.DELIVERING;
            default:
                logger.warn("Unknown world status: {}, defaulting to IDLE", worldStatus);
                return TruckStatus.IDLE;
        }
    }
    
    /**
     * Update packages associated with a truck when truck status changes
     */
    private void updatePackagesForTruckStatusChange(Truck truck, TruckStatus oldStatus, TruckStatus newStatus) {
        List<Package> packages = packageRepository.findByTruck(truck);
        
        for (Package pkg : packages) {
            // Update package status based on truck status
            PackageStatus oldPackageStatus = pkg.getStatus();
            PackageStatus newPackageStatus = determinePackageStatus(pkg.getStatus(), newStatus);
            
            if (oldPackageStatus != newPackageStatus) {
                pkg.setStatus(newPackageStatus);
                packageRepository.save(pkg);
                logger.info("Updated package {} status from {} to {}", 
                        pkg.getId(), oldPackageStatus, newPackageStatus);
            }
            
            // Notify Amazon about status update
            try {
                amazonNotificationService.sendStatusUpdate(pkg, truck, 
                        newStatus.toString(), 
                        "Truck " + truck.getId() + " status changed to " + newStatus);
            } catch (Exception e) {
                logger.error("Failed to send status update to Amazon", e);
            }
        }
    }
    
    /**
     * Determine package status based on truck status
     */
    private PackageStatus determinePackageStatus(PackageStatus currentStatus, TruckStatus truckStatus) {
        switch (truckStatus) {
            case LOADING:
                return PackageStatus.LOADING;
            case DELIVERING:
                return PackageStatus.DELIVERING;
            case IDLE:
                // If truck is idle after delivering, the package might be delivered
                if (currentStatus == PackageStatus.DELIVERING) {
                    return PackageStatus.DELIVERED;
                }
                // Otherwise, keep current status
                return currentStatus;
            default:
                return currentStatus;
        }
    }
    
    /**
     * Process an error from the World Simulator
     */
    @Transactional
    private void processError(WorldUpsProto.UErr error) {
        logger.error("World error for sequence {}: {}", error.getOriginseqnum(), error.getErr());
        
        // Find all packages that might be affected and notify Amazon
        List<Package> activePackages = packageRepository.findAll().stream()
                .filter(pkg -> pkg.getStatus() != PackageStatus.DELIVERED && pkg.getStatus() != PackageStatus.FAILED)
                .collect(Collectors.toList());
        
        for (Package pkg : activePackages) {
            try {
                amazonNotificationService.sendStatusUpdate(
                    pkg, 
                    pkg.getTruck(), 
                    "ERROR", 
                    "Operation error: " + error.getErr() + " (seq: " + error.getOriginseqnum() + ")"
                );
            } catch (Exception e) {
                logger.error("Failed to send error notification to Amazon", e);
            }
        }
    }
    
    /**
     * Handle the end of simulation
     */
    @Transactional
    private void handleSimulationFinished() {
        // Find all packages that are not in a terminal state
        List<Package> activePackages = packageRepository.findAll().stream()
                .filter(pkg -> pkg.getStatus() != PackageStatus.DELIVERED && pkg.getStatus() != PackageStatus.FAILED)
                .collect(Collectors.toList());
        
        // Mark them as failed and notify Amazon
        for (Package pkg : activePackages) {
            pkg.setStatus(PackageStatus.FAILED);
            packageRepository.save(pkg);
            
            try {
                amazonNotificationService.sendStatusUpdate(
                    pkg,
                    pkg.getTruck(),
                    "FAILED",
                    "Delivery failed: world simulation ended"
                );
            } catch (Exception e) {
                logger.error("Failed to send simulation end notification to Amazon", e);
            }
        }
        
        logger.info("Processed {} undelivered packages due to simulation end", activePackages.size());
        
        // Stop the response handler
        stop();
    }
    
    /**
     * Find the nearest warehouse to a location
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
            int dist = distance(x, y, warehouse.getX(), warehouse.getY());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = warehouse;
            }
        }
        
        // Only return if warehouse is close enough (within 5 units)
        if (minDistance <= 5) {
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
     * Stop the response handler
     */
    public void stop() {
        logger.info("Stopping World Response Handler");
        running = false;
    }

    /**
     * Process truck arrival at warehouse
     */
    private void processArrivalAtWarehouse(Truck truck, WorldUpsProto.UFinished completion) {
        // Find packages assigned to this truck
        List<Package> packages = packageRepository.findByTruckAndStatus(truck, PackageStatus.ASSIGNED);
        
        if (packages.isEmpty()) {
            logger.info("No packages assigned to truck {} at warehouse", truck.getId());
            return;
        }
        
        // Find the warehouse at current location
        Optional<Warehouse> warehouseOpt = findNearestWarehouse(completion.getX(), completion.getY());
        if (warehouseOpt.isEmpty()) {
            logger.error("Could not find warehouse near location ({},{})", completion.getX(), completion.getY());
            return;
        }
        
        Warehouse warehouse = warehouseOpt.get();
        logger.info("Truck {} arrived at warehouse {} at location ({},{})", 
                truck.getId(), warehouse.getId(), completion.getX(), completion.getY());
        
        // Process each package assigned to this truck
        for (Package pkg : packages) {
            try {
                // Only process packages that are in ASSIGNED status and belong to this warehouse
                if (pkg.getWarehouse() != null && pkg.getWarehouse().getId().equals(warehouse.getId())) {
                    // Update package status to PICKUP_READY
                    pkg.setStatus(PackageStatus.PICKUP_READY);
                    packageRepository.save(pkg);
                    
                    // Send notification to Amazon
                    amazonNotificationService.notifyTruckArrival(pkg, truck, warehouse);
                    logger.info("Notified Amazon about truck {} arrival at warehouse {} for package {}", 
                            truck.getId(), warehouse.getId(), pkg.getId());
                    
                    // After notifying Amazon, the package can be loaded
                    // In real flow, Amazon would send a load command, but for testing we can simulate it
                    // TODO: Remove this auto-load in production
                    pkg.setStatus(PackageStatus.LOADED);
                    packageRepository.save(pkg);
                    
                    // Immediately send truck to deliver after loading
                    if (truck.getStatus() == TruckStatus.ARRIVE_WAREHOUSE) {
                        try {
                            ups.sendTruckToDeliver(truck.getId(), pkg.getId(), 
                                new Location(pkg.getDestinationX(), pkg.getDestinationY()));
                            logger.info("Sent truck {} to deliver package {} to ({},{})", 
                                    truck.getId(), pkg.getId(), pkg.getDestinationX(), pkg.getDestinationY());
                        } catch (Exception e) {
                            logger.error("Error sending truck to deliver", e);
                        }
                    }
                } else {
                    logger.warn("Package {} assigned to truck {} but belongs to different warehouse", 
                            pkg.getId(), truck.getId());
                }
            } catch (Exception e) {
                logger.error("Error processing package {} for truck arrival", pkg.getId(), e);
            }
        }
    }
}