package com.ups.service.world;

import com.ups.WorldUpsProto;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
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
    private final BlockingQueue<WorldUpsProto.UResponses> responseQueue;
    private volatile boolean running = true;
    
    @Autowired
    public WorldResponseHandler(TruckRepository truckRepository, PackageRepository packageRepository) {
        this.truckRepository = truckRepository;
        this.packageRepository = packageRepository;
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
            // Handle simulation finished scenario
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
                    // TODO: Notify Amazon about truck arrival
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
        if (packageOpt.isPresent()) {
            Package pkg = packageOpt.get();
            pkg.setStatus(PackageStatus.DELIVERED);
            packageRepository.save(pkg);
            logger.info("Marked package {} as delivered", pkg.getId());
            
            // TODO: Notify Amazon about successful delivery
        } else {
            logger.error("Package {} not found for delivery update", delivery.getPackageid());
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
            switch (truckStatus.getStatus().toLowerCase()) {
                case "idle":
                    truck.setStatus(TruckStatus.IDLE);
                    break;
                case "traveling":
                    truck.setStatus(TruckStatus.TRAVELING);
                    break;
                case "arrive warehouse":
                    truck.setStatus(TruckStatus.ARRIVE_WAREHOUSE);
                    break;
                case "loading":
                    truck.setStatus(TruckStatus.LOADING);
                    break;
                case "delivering":
                    truck.setStatus(TruckStatus.DELIVERING);
                    break;
                default:
                    logger.warn("Unknown truck status: {}", truckStatus.getStatus());
            }
            
            truckRepository.save(truck);
        } else {
            logger.error("Truck {} not found for status update", truckStatus.getTruckid());
        }
    }
    
    private void processError(WorldUpsProto.UErr error) {
        logger.error("World error for sequence {}: {}", error.getOriginseqnum(), error.getErr());
        // TODO: Implement error handling logic, possibly retry the operation
    }
    
    public void stop() {
        running = false;
    }
}