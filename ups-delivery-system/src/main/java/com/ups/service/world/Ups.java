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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class Ups {
    private static final Logger logger = LoggerFactory.getLogger(Ups.class);
    
    private WorldConnector worldConnector;
    private final TruckRepository truckRepository;
    private final PackageRepository packageRepository;
    private final WorldResponseListener responseListener;
    private final WorldResponseHandler responseHandler;
    
    @Value("${ups.world.host:localhost}")
    private String worldHost;
    
    @Value("${ups.world.port:12345}")
    private int worldPort;
    
    @Value("${ups.init.trucks:5}")
    private int initialTruckCount;
    
    @Value("${ups.world.sim.speed:1000}")
    private int worldSimSpeed;
    
    @Autowired
    public Ups(TruckRepository truckRepository, 
               PackageRepository packageRepository,
               WorldResponseListener responseListener,
               WorldResponseHandler responseHandler) {
        this.truckRepository = truckRepository;
        this.packageRepository = packageRepository;
        this.responseListener = responseListener;
        this.responseHandler = responseHandler;
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
            
            // Connect to world simulator with response listener
            try {
                this.worldConnector = new WorldConnector(worldHost, worldPort, trucks, responseListener);
                
                // Set simulation speed
                worldConnector.setSimulationSpeed(worldSimSpeed);
                
                logger.info("Connected to world simulator at {}:{} with world ID: {}", 
                        worldHost, worldPort, worldConnector.getWorldId());
            } catch (IOException e) {
                logger.error("Failed to connect to world simulator: {}", e.getMessage());
                // In test environments, continue without world connection
                if (isTestEnvironment()) {
                    logger.warn("Running in test environment - continuing without world connection");
                } else {
                    throw e; // Re-throw in production environment
                }
            }
            
            // Start the response handler in a separate thread
            CompletableFuture.runAsync(() -> {
                responseHandler.processResponses();
            }).exceptionally(ex -> {
                logger.error("Response handler thread failed", ex);
                return null;
            });
        } catch (IOException e) {
            logger.error("Failed to initialize UPS service: {}", e.getMessage(), e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            // Stop the response handler
            responseHandler.stop();
            
            // Stop the response listener
            responseListener.stopListening();
            
            // Disconnect from world simulator
            if (worldConnector != null && worldConnector.isConnected()) {
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
                
                // Send command to world simulator if connected
                if (worldConnector != null && worldConnector.isConnected()) {
                    worldConnector.pickup(truckId, warehouseId, worldConnector.getNextSeqNum());
                    logger.info("Sent truck {} to warehouse {}", truckId, warehouseId);
                } else {
                    logger.warn("Not connected to world simulator. Database updated but command not sent.");
                }
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
                
                // Send command to world simulator if connected
                if (worldConnector != null && worldConnector.isConnected()) {
                    worldConnector.deliver(truckId, packageId, destination, worldConnector.getNextSeqNum());
                    logger.info("Sent truck {} to deliver package {} to ({},{})", 
                            truckId, packageId, destination.getX(), destination.getY());
                } else {
                    logger.warn("Not connected to world simulator. Database updated but command not sent.");
                }
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
                if (worldConnector != null && worldConnector.isConnected()) {
                    worldConnector.query(truckId, worldConnector.getNextSeqNum());
                    logger.info("Queried status for truck {}", truckId);
                } else {
                    logger.warn("Not connected to world simulator. Query command not sent.");
                }
            } else {
                logger.error("Truck with ID {} not found", truckId);
            }
        } catch (IOException e) {
            logger.error("Failed to query truck status: {}", e.getMessage(), e);
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
        return (worldConnector != null && worldConnector.isConnected()) ? worldConnector.getWorldId() : null;
    }
    
    // Helper method to check if running in test environment
    private boolean isTestEnvironment() {
        for (String profile : System.getProperty("spring.profiles.active", "").split(",")) {
            if (profile.trim().equals("test") || profile.trim().equals("test-cli")) {
                return true;
            }
        }
        return false;
    }
}