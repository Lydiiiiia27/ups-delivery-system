package com.ups.service.world;

import com.ups.model.Location;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class Ups {
    private static final Logger logger = LoggerFactory.getLogger(Ups.class);
    
    private final WorldConnector worldConnector;
    private final TruckRepository truckRepository;
    private final PackageRepository packageRepository;
    
    @Value("${ups.world.host:localhost}")
    private String worldHost;
    
    @Value("${ups.world.port:12345}")
    private int worldPort;
    
    @Value("${ups.init.trucks:5}")
    private int initialTruckCount;
    
    @Value("${ups.world.sim.speed:1000}")
    private int worldSimSpeed;
    
    @Autowired
    public Ups(TruckRepository truckRepository, PackageRepository packageRepository) {
        this.truckRepository = truckRepository;
        this.packageRepository = packageRepository;
        this.worldConnector = new WorldConnector();
    }
    
    @PostConstruct
    public void initialize() {
        try {
            // Initialize with saved trucks or create new ones
            List<Truck> trucks = truckRepository.findAll();
            if (trucks.isEmpty()) {
                // Create initial trucks if none exist
                createInitialTrucks();
                trucks = truckRepository.findAll();
            }
            
            // Connect to world simulator
            WorldConnector connector = new WorldConnector(worldHost, worldPort, trucks);
            
            // Set simulation speed
            connector.setSimulationSpeed(worldSimSpeed);
            
            logger.info("Connected to world simulator at {}:{} with world ID: {}", 
                    worldHost, worldPort, connector.getWorldId());
        } catch (IOException e) {
            logger.error("Failed to connect to world simulator: {}", e.getMessage(), e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            if (worldConnector.isConnected()) {
                worldConnector.disconnect();
                logger.info("Disconnected from world simulator");
            }
        } catch (IOException e) {
            logger.error("Error disconnecting from world simulator: {}", e.getMessage(), e);
        }
    }
    
    private void createInitialTrucks() {
        // Create initial trucks at default location (0,0)
        for (int i = 0; i < initialTruckCount; i++) {
            Truck truck = new Truck();
            truck.setX(0);
            truck.setY(0);
            truck.setStatus(TruckStatus.IDLE);
            truckRepository.save(truck);
            logger.info("Created truck with ID: {}", truck.getId());
        }
    }
    
    @Transactional
    public void sendTruckToPickup(int truckId, int warehouseId) {
        try {
            // Update truck status in database
            Optional<Truck> truckOpt = truckRepository.findById(truckId);
            if (truckOpt.isPresent()) {
                Truck truck = truckOpt.get();
                truck.setStatus(TruckStatus.TRAVELING);
                truckRepository.save(truck);
                
                // Send command to world simulator
                worldConnector.pickup(truckId, warehouseId, worldConnector.getNextSeqNum());
                logger.info("Sent truck {} to warehouse {}", truckId, warehouseId);
            } else {
                logger.error("Truck with ID {} not found", truckId);
            }
        } catch (IOException e) {
            logger.error("Failed to send truck to pickup: {}", e.getMessage(), e);
        }
    }
    
    @Transactional
    public void sendTruckToDeliver(int truckId, long packageId, Location destination) {
        try {
            // Update package and truck status in database
            Optional<Truck> truckOpt = truckRepository.findById(truckId);
            Optional<Package> packageOpt = packageRepository.findById(packageId);
            
            if (truckOpt.isPresent() && packageOpt.isPresent()) {
                Truck truck = truckOpt.get();
                Package pkg = packageOpt.get();
                
                // Update truck status
                truck.setStatus(TruckStatus.DELIVERING);
                truckRepository.save(truck);
                
                // Update package status and destination
                pkg.setStatus(PackageStatus.DELIVERING);
                pkg.setDestinationX(destination.getX());
                pkg.setDestinationY(destination.getY());
                pkg.setTruck(truck);
                packageRepository.save(pkg);
                
                // Send command to world simulator
                worldConnector.deliver(truckId, packageId, destination, worldConnector.getNextSeqNum());
                logger.info("Sent truck {} to deliver package {} to ({},{})", 
                        truckId, packageId, destination.getX(), destination.getY());
            } else {
                if (!truckOpt.isPresent()) {
                    logger.error("Truck with ID {} not found", truckId);
                }
                if (!packageOpt.isPresent()) {
                    logger.error("Package with ID {} not found", packageId);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to send truck to deliver: {}", e.getMessage(), e);
        }
    }
    
    @Transactional
    public void queryTruckStatus(int truckId) {
        try {
            Optional<Truck> truckOpt = truckRepository.findById(truckId);
            if (truckOpt.isPresent()) {
                worldConnector.query(truckId, worldConnector.getNextSeqNum());
                logger.info("Queried status for truck {}", truckId);
            } else {
                logger.error("Truck with ID {} not found", truckId);
            }
        } catch (IOException e) {
            logger.error("Failed to query truck status: {}", e.getMessage(), e);
        }
    }
    
    @Transactional
    public void updateTruckStatus(int truckId, TruckStatus status, int x, int y) {
        Optional<Truck> truckOpt = truckRepository.findById(truckId);
        if (truckOpt.isPresent()) {
            Truck truck = truckOpt.get();
            truck.setStatus(status);
            truck.setX(x);
            truck.setY(y);
            truckRepository.save(truck);
            logger.info("Updated truck {} status to {} at location ({},{})", truckId, status, x, y);
        } else {
            logger.error("Truck with ID {} not found for status update", truckId);
        }
    }
    
    @Transactional
    public void markPackageDelivered(long packageId) {
        Optional<Package> packageOpt = packageRepository.findById(packageId);
        if (packageOpt.isPresent()) {
            Package pkg = packageOpt.get();
            pkg.setStatus(PackageStatus.DELIVERED);
            packageRepository.save(pkg);
            logger.info("Marked package {} as delivered", packageId);
        } else {
            logger.error("Package with ID {} not found for delivery update", packageId);
        }
    }
    
    // Periodically check truck statuses
    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkTruckStatuses() {
        List<Truck> activeTrucks = truckRepository.findByStatusNot(TruckStatus.IDLE);
        for (Truck truck : activeTrucks) {
            try {
                queryTruckStatus(truck.getId());
            } catch (Exception e) {
                logger.error("Error checking status for truck {}: {}", truck.getId(), e.getMessage());
            }
        }
    }
    
    // Method for other services to get the world ID
    public Long getWorldId() {
        return worldConnector.getWorldId();
    }
}