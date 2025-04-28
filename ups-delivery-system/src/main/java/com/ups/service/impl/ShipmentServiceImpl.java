package com.ups.service.impl;

import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageItem;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.model.entity.User;
import com.ups.model.entity.Warehouse;
import com.ups.repository.PackageItemRepository;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import com.ups.repository.UserRepository;
import com.ups.repository.WarehouseRepository;
import com.ups.service.ShipmentService;
import com.ups.service.world.Ups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ShipmentServiceImpl implements ShipmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ShipmentServiceImpl.class);
    private static final AtomicLong nextSeqNum = new AtomicLong(200);
    
    private final PackageRepository packageRepository;
    private final PackageItemRepository packageItemRepository;
    private final UserRepository userRepository;
    private final TruckRepository truckRepository;
    private final WarehouseRepository warehouseRepository;
    private final Ups ups;
    
    @Autowired
    public ShipmentServiceImpl(
            PackageRepository packageRepository,
            PackageItemRepository packageItemRepository,
            UserRepository userRepository,
            TruckRepository truckRepository,
            WarehouseRepository warehouseRepository,
            Ups ups) {
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.userRepository = userRepository;
        this.truckRepository = truckRepository;
        this.warehouseRepository = warehouseRepository;
        this.ups = ups;
    }
    
    @Override
    @Transactional
    public CreateShipmentResponse processShipmentRequest(CreateShipmentRequest request) {
        logger.info("Processing shipment request for package ID: {}", 
                request.getShipmentInfo().getPackageId());
        
        CreateShipmentResponse response = new CreateShipmentResponse();
        response.setMessageType("CreateShipmentResponse");
        response.setSeqNum(nextSeqNum.getAndIncrement());
        response.setAck(request.getSeqNum());
        response.setTimestamp(Instant.now());
        
        try {
            // 1. Find or create warehouse
            Warehouse warehouse = findOrCreateWarehouse(
                request.getShipmentInfo().getWarehouseId(),
                request.getShipmentInfo().getDestination().getX(),
                request.getShipmentInfo().getDestination().getY()
            );
            
            // 2. Find user if ups_account_name is provided
            User user = null;
            if (request.getShipmentInfo().getUpsAccountName() != null && 
                !request.getShipmentInfo().getUpsAccountName().isEmpty()) {
                Optional<User> userOpt = userRepository.findByUsername(
                        request.getShipmentInfo().getUpsAccountName());
                user = userOpt.orElse(null);
                
                if (user == null) {
                    logger.warn("UPS account name provided but user not found: {}", 
                            request.getShipmentInfo().getUpsAccountName());
                }
            }
            
            // 3. Find available truck
            Optional<Truck> truckOpt = truckRepository.findByStatus(TruckStatus.IDLE).stream().findFirst();
            
            if (truckOpt.isPresent()) {
                Truck truck = truckOpt.get();
                
                // 4. Create package entity
                Package pkg = new Package();
                pkg.setId(request.getShipmentInfo().getPackageId());
                pkg.setWarehouse(warehouse);
                pkg.setUser(user);
                pkg.setDestinationX(request.getShipmentInfo().getDestination().getX());
                pkg.setDestinationY(request.getShipmentInfo().getDestination().getY());
                pkg.setStatus(PackageStatus.ASSIGNED);
                pkg.setTruck(truck);
                
                // 5. Save package
                packageRepository.save(pkg);
                
                // 6. Add items to package
                if (request.getShipmentInfo().getItems() != null) {
                    for (CreateShipmentRequest.Item item : request.getShipmentInfo().getItems()) {
                        PackageItem packageItem = new PackageItem();
                        packageItem.setProductId(item.getProductId());
                        packageItem.setDescription(item.getDescription());
                        packageItem.setCount(item.getCount());
                        packageItem.setPkg(pkg);
                        packageItemRepository.save(packageItem);
                    }
                }
                
                // 7. Update truck status and send it to the warehouse
                truck.setStatus(TruckStatus.TRAVELING);
                truckRepository.save(truck);
                
                // 8. Send truck to pick up package
                ups.sendTruckToPickup(truck.getId(), warehouse.getId());
                
                // 9. Set response
                response.setStatus("ACCEPTED");
                response.setTruckId(truck.getId());
                
                logger.info("Shipment request accepted for package ID: {} with truck ID: {}", 
                        pkg.getId(), truck.getId());
            } else {
                response.setStatus("REJECTED");
                response.setError("No available trucks");
                logger.warn("Shipment request rejected: No available trucks");
            }
        } catch (Exception e) {
            response.setStatus("REJECTED");
            response.setError("Error processing shipment: " + e.getMessage());
            logger.error("Error processing shipment request", e);
        }
        
        return response;
    }
    
    private Warehouse findOrCreateWarehouse(Integer warehouseId, Integer x, Integer y) {
        return warehouseRepository.findById(warehouseId)
                .orElseGet(() -> {
                    Warehouse newWarehouse = new Warehouse();
                    newWarehouse.setId(warehouseId);
                    newWarehouse.setX(x); // Use the provided coordinates
                    newWarehouse.setY(y);
                    return warehouseRepository.save(newWarehouse);
                });
    }
}