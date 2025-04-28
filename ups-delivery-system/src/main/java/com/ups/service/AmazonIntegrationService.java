package com.ups.service;

import com.ups.model.Location;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import com.ups.service.world.Ups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service to handle Amazon integration flows and package lifecycle
 */
@Service
public class AmazonIntegrationService {
    private static final Logger logger = LoggerFactory.getLogger(AmazonIntegrationService.class);
    
    private final PackageRepository packageRepository;
    private final TruckRepository truckRepository;
    private final Ups ups;
    private final AmazonNotificationService amazonNotificationService;
    
    @Autowired
    public AmazonIntegrationService(
            PackageRepository packageRepository,
            TruckRepository truckRepository,
            Ups ups,
            AmazonNotificationService amazonNotificationService) {
        this.packageRepository = packageRepository;
        this.truckRepository = truckRepository;
        this.ups = ups;
        this.amazonNotificationService = amazonNotificationService;
    }
    
    /**
     * Handle package loading confirmation from Amazon
     */
    @Transactional
    public void handlePackageLoaded(Long packageId) {
        logger.info("Handling package loaded confirmation for package {}", packageId);
        
        Optional<Package> packageOpt = packageRepository.findById(packageId);
        if (packageOpt.isEmpty()) {
            logger.error("Package {} not found", packageId);
            return;
        }
        
        Package pkg = packageOpt.get();
        Truck truck = pkg.getTruck();
        
        if (truck == null) {
            logger.error("No truck assigned to package {}", packageId);
            return;
        }
        
        // Update package status to LOADED
        pkg.setStatus(PackageStatus.LOADED);
        packageRepository.save(pkg);
        
        // Send truck to deliver the package
        Location destination = new Location(pkg.getDestinationX(), pkg.getDestinationY());
        ups.sendTruckToDeliver(truck.getId(), packageId, destination);
        
        logger.info("Package {} loaded on truck {} and sent for delivery to ({},{})", 
                packageId, truck.getId(), destination.getX(), destination.getY());
    }
    
    /**
     * Handle package delivery completion
     */
    @Transactional
    public void handlePackageDelivered(Long packageId, Integer truckId) {
        logger.info("Handling package delivered for package {} by truck {}", packageId, truckId);
        
        Optional<Package> packageOpt = packageRepository.findById(packageId);
        Optional<Truck> truckOpt = truckRepository.findById(truckId);
        
        if (packageOpt.isEmpty() || truckOpt.isEmpty()) {
            logger.error("Package {} or Truck {} not found", packageId, truckId);
            return;
        }
        
        Package pkg = packageOpt.get();
        Truck truck = truckOpt.get();
        
        // Update package status to DELIVERED
        pkg.setStatus(PackageStatus.DELIVERED);
        packageRepository.save(pkg);
        
        // Notify Amazon about delivery completion
        try {
            amazonNotificationService.notifyDeliveryComplete(pkg, truck);
            logger.info("Delivery completion notification sent to Amazon for package {}", packageId);
        } catch (Exception e) {
            logger.error("Failed to notify Amazon about delivery completion for package {}", packageId, e);
        }
    }
}