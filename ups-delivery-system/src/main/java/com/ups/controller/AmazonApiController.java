package com.ups.controller;

import com.ups.model.Location;
import com.ups.model.amazon.CreateShipmentRequest;
import com.ups.model.amazon.CreateShipmentResponse;
import com.ups.model.amazon.ChangeDestinationRequest;
import com.ups.model.amazon.ChangeDestinationResponse;
import com.ups.model.amazon.NotifyTruckArrived;
import com.ups.model.amazon.NotifyDeliveryComplete;
import com.ups.model.amazon.QueryShipmentStatusRequest;
import com.ups.model.amazon.QueryShipmentStatusResponse;
import com.ups.model.amazon.UpdateShipmentStatus;
import com.ups.model.amazon.UPSGeneralError;
import com.ups.model.entity.Package;
import com.ups.model.entity.PackageStatus;
import com.ups.model.entity.Truck;
import com.ups.model.entity.TruckStatus;
import com.ups.repository.PackageRepository;
import com.ups.repository.TruckRepository;
import com.ups.service.ShipmentService;
import com.ups.service.MessageTrackingService;
import com.ups.service.world.Ups;
import com.ups.model.amazon.PackageLoadedRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class AmazonApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(AmazonApiController.class);
    private final ShipmentService shipmentService;
    private final MessageTrackingService messageTrackingService;
    private final PackageRepository packageRepository;
    private final TruckRepository truckRepository;
    private final Ups ups;
    
    private final Map<Long, Object> responseCache = new ConcurrentHashMap<>();
    
    @Autowired
    public AmazonApiController(ShipmentService shipmentService, 
                              MessageTrackingService messageTrackingService,
                              PackageRepository packageRepository,
                              TruckRepository truckRepository,
                              Ups ups) {
        this.shipmentService = shipmentService;
        this.messageTrackingService = messageTrackingService;
        this.packageRepository = packageRepository;
        this.truckRepository = truckRepository;
        this.ups = ups;
    }
    
    
    @PostMapping("/createshipment")
    public ResponseEntity<CreateShipmentResponse> createShipment(@RequestBody CreateShipmentRequest request) {
        logger.info("Received createshipment request: {}", request.getSeqNum());
        
        // Validate request
        if (!"CreateShipmentRequest".equals(request.getMessageType())) {
            logger.error("Invalid message type: {}", request.getMessageType());
            return ResponseEntity.badRequest().build();
        }
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(request.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", request.getSeqNum());
            // Return the cached response
            Object cachedResponse = responseCache.get(request.getSeqNum());
            if (cachedResponse != null && cachedResponse instanceof CreateShipmentResponse) {
                logger.info("Returning cached response for message with seq_num: {}", request.getSeqNum());
                return ResponseEntity.ok((CreateShipmentResponse) cachedResponse);
            }
            return ResponseEntity.ok().build();
        }
        
        try {
            // Process the shipment request
            CreateShipmentResponse response = shipmentService.processShipmentRequest(request);
            
            // Cache the response
            responseCache.put(request.getSeqNum(), response);
            
            // Mark the message as processed
            messageTrackingService.markMessageProcessed(request.getSeqNum(), request.getMessageType());
            
            // Track the outgoing message
            messageTrackingService.recordOutgoingMessage(response.getSeqNum(), response.getMessageType());
            
            logger.info("Sending response with seq_num: {}", response.getSeqNum());
            
            // Return response
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing shipment request", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/changedestination")
    public ResponseEntity<ChangeDestinationResponse> changeDestination(@RequestBody ChangeDestinationRequest request) {
        logger.info("Received changedestination request for package: {}", request.getPackageId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(request.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", request.getSeqNum());
            // Return the cached response
            Object cachedResponse = responseCache.get(request.getSeqNum());
            if (cachedResponse != null && cachedResponse instanceof ChangeDestinationResponse) {
                logger.info("Returning cached response for message with seq_num: {}", request.getSeqNum());
                return ResponseEntity.ok((ChangeDestinationResponse) cachedResponse);
            }
            return ResponseEntity.ok().build();
        }
        
        // Create response
        ChangeDestinationResponse response = new ChangeDestinationResponse();
        response.setMessageType("ChangeDestinationResponse");
        response.setSeqNum(messageTrackingService.getNextSeqNum());
        response.setAck(request.getSeqNum());
        response.setTimestamp(Instant.now());
        
        try {
            // Find the package
            Optional<Package> packageOpt = packageRepository.findById(request.getPackageId());
            
            if (packageOpt.isPresent()) {
                Package pkg = packageOpt.get();
                
                // Check if the package is in a state where destination change is allowed
                if (pkg.getStatus() == PackageStatus.DELIVERING || pkg.getStatus() == PackageStatus.DELIVERED) {
                    response.setStatus("FAILED");
                    response.setError("Package is already out for delivery or delivered and cannot be redirected");
                    logger.warn("Attempted to change destination for package {} which is in state {}", 
                            pkg.getId(), pkg.getStatus());
                } else {
                    // Update the destination
                    pkg.setDestinationX(request.getNewDestination().getX());
                    pkg.setDestinationY(request.getNewDestination().getY());
                    packageRepository.save(pkg);
                    
                    // If the package is assigned to a truck and the truck is at the warehouse,
                    // we need to update the delivery instructions
                    if (pkg.getTruck() != null && pkg.getTruck().getStatus() == TruckStatus.ARRIVE_WAREHOUSE) {
                        // Send updated delivery instructions to world simulator
                        Location newLocation = new Location(
                                request.getNewDestination().getX(), 
                                request.getNewDestination().getY());
                        ups.sendTruckToDeliver(pkg.getTruck().getId(), pkg.getId(), newLocation);
                        
                        logger.info("Updated delivery instructions for package {} to new destination ({},{})",
                                pkg.getId(), newLocation.getX(), newLocation.getY());
                    }
                    
                    response.setStatus("UPDATED");
                    logger.info("Successfully changed destination for package {} to ({},{})",
                            pkg.getId(), request.getNewDestination().getX(), request.getNewDestination().getY());
                }
            } else {
                response.setStatus("FAILED");
                response.setError("Package not found with ID: " + request.getPackageId());
                logger.warn("Destination change request for non-existent package: {}", request.getPackageId());
            }
        } catch (Exception e) {
            response.setStatus("FAILED");
            response.setError("Error processing destination change: " + e.getMessage());
            logger.error("Error processing destination change for package {}: {}", 
                    request.getPackageId(), e.getMessage(), e);
        }
        
        // Cache the response
        responseCache.put(request.getSeqNum(), response);
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(request.getSeqNum(), request.getMessageType());
        
        // Track the outgoing message
        messageTrackingService.recordOutgoingMessage(response.getSeqNum(), response.getMessageType());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/queryshipmentstatus")
    public ResponseEntity<QueryShipmentStatusResponse> queryShipmentStatus(@RequestBody QueryShipmentStatusRequest request) {
        logger.info("Received queryshipmentstatus request for package: {}", request.getPackageId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(request.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", request.getSeqNum());
            // Return cached response if available
            Object cachedResponse = responseCache.get(request.getSeqNum());
            if (cachedResponse != null && cachedResponse instanceof QueryShipmentStatusResponse) {
                logger.info("Returning cached response for message with seq_num: {}", request.getSeqNum());
                return ResponseEntity.ok((QueryShipmentStatusResponse) cachedResponse);
            }
            return ResponseEntity.ok().build();
        }
        
        // Create response
        QueryShipmentStatusResponse response = new QueryShipmentStatusResponse();
        response.setMessageType("QueryShipmentStatusResponse");
        response.setSeqNum(messageTrackingService.getNextSeqNum());
        response.setAck(request.getSeqNum());
        response.setTimestamp(Instant.now());
        response.setPackageId(request.getPackageId());
        
        try {
            // Find the package in the database
            Optional<Package> packageOpt = packageRepository.findById(request.getPackageId());
            
            if (packageOpt.isPresent()) {
                Package pkg = packageOpt.get();
                Truck truck = pkg.getTruck();
                
                // Set truck ID if available
                if (truck != null) {
                    response.setTruckId(truck.getId());
                    
                    // Create and set the current location
                    QueryShipmentStatusResponse.Location location = new QueryShipmentStatusResponse.Location(
                        truck.getX(), truck.getY());
                    response.setCurrentLocation(location);
                } else {
                    // If no truck assigned, use warehouse location if available
                    if (pkg.getWarehouse() != null) {
                        QueryShipmentStatusResponse.Location location = new QueryShipmentStatusResponse.Location(
                            pkg.getWarehouse().getX(), pkg.getWarehouse().getY());
                        response.setCurrentLocation(location);
                    }
                }
                
                // Map our internal status to a user-friendly status
                String currentStatus;
                switch (pkg.getStatus()) {
                    case CREATED:
                    case PACKING:
                        currentStatus = "PROCESSING";
                        break;
                    case PACKED:
                    case PICKUP_READY:
                        currentStatus = "READY_FOR_PICKUP";
                        break;
                    case LOADING:
                    case LOADED:
                        currentStatus = "LOADING";
                        break;
                    case ASSIGNED:
                    case OUT_FOR_DELIVERY:
                    case DELIVERING:
                        currentStatus = "IN_TRANSIT";
                        break;
                    case DELIVERED:
                        currentStatus = "DELIVERED";
                        break;
                    case FAILED:
                        currentStatus = "FAILED";
                        break;
                    default:
                        currentStatus = "UNKNOWN";
                }
                response.setCurrentStatus(currentStatus);
                
                // Calculate estimated delivery time if package is in transit
                if (currentStatus.equals("IN_TRANSIT") && truck != null) {
                    // Calculate distance
                    int distanceToDestination = calculateDistance(
                        truck.getX(), truck.getY(), 
                        pkg.getDestinationX(), pkg.getDestinationY());
                    
                    // Assume average speed (1 unit per minute)
                    int estimatedMinutes = distanceToDestination;
                    
                    // Set expected delivery time
                    response.setExpectedDeliveryTime(Instant.now().plusSeconds(estimatedMinutes * 60));
                }
                
                logger.info("Found package {} with status {}", pkg.getId(), currentStatus);
            } else {
                // Package not found
                response.setCurrentStatus("NOT_FOUND");
                logger.warn("Package not found with ID: {}", request.getPackageId());
            }
        } catch (Exception e) {
            // Handle exceptions
            response.setCurrentStatus("ERROR");
            logger.error("Error processing status query for package {}: {}", 
                request.getPackageId(), e.getMessage(), e);
        }
        
        // Cache the response
        responseCache.put(request.getSeqNum(), response);
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(request.getSeqNum(), request.getMessageType());
        
        // Track the outgoing message
        messageTrackingService.recordOutgoingMessage(response.getSeqNum(), response.getMessageType());
        
        return ResponseEntity.ok(response);
    }

    // Helper method to calculate Euclidean distance
    private int calculateDistance(int x1, int y1, int x2, int y2) {
        return (int) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
    
    @PostMapping("/notifytruckarrived")
    public ResponseEntity<Void> notifyTruckArrived(@RequestBody NotifyTruckArrived notification) {
        logger.info("Received truck arrived notification for package: {}, truck: {}", 
                notification.getPackageId(), notification.getTruckId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(notification.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", notification.getSeqNum());
            return ResponseEntity.ok().build();
        }
        
        try {
            // Process truck arrived notification
            Optional<Package> packageOpt = packageRepository.findById(notification.getPackageId());
            Optional<Truck> truckOpt = truckRepository.findById(notification.getTruckId());
            
            if (packageOpt.isPresent() && truckOpt.isPresent()) {
                Package pkg = packageOpt.get();
                Truck truck = truckOpt.get();
                
                // Update package status to PICKUP_READY
                pkg.setStatus(PackageStatus.PICKUP_READY);
                packageRepository.save(pkg);
                
                logger.info("Updated package {} status to PICKUP_READY", pkg.getId());
            } else {
                logger.error("Package {} or Truck {} not found for truck arrival notification", 
                        notification.getPackageId(), notification.getTruckId());
            }
        } catch (Exception e) {
            logger.error("Error processing truck arrival notification", e);
        }
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(notification.getSeqNum(), notification.getMessageType());
        
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/notifydeliverycomplete")
    public ResponseEntity<Void> notifyDeliveryComplete(@RequestBody NotifyDeliveryComplete notification) {
        logger.info("Received delivery complete notification for package: {}, truck: {}", 
                notification.getPackageId(), notification.getTruckId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(notification.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", notification.getSeqNum());
            return ResponseEntity.ok().build();
        }
        
        // Process delivery complete notification
        // TODO: Update package status in database
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(notification.getSeqNum(), notification.getMessageType());
        
        // No response is expected for notifications
        return ResponseEntity.ok().build();
    }

    @PostMapping("/updateshipmentstatus")
    public ResponseEntity<Void> updateShipmentStatus(@RequestBody UpdateShipmentStatus update) {
        logger.info("Received shipment status update for package: {}, status: {}", 
                update.getPackageId(), update.getStatus());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(update.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", update.getSeqNum());
            return ResponseEntity.ok().build();
        }
        
        // Process shipment status update
        // TODO: Update package status in database and notify users if applicable
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(update.getSeqNum(), update.getMessageType());
        
        // No response is expected for updates
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/packageloaded")
    public ResponseEntity<Void> handlePackageLoaded(@RequestBody PackageLoadedRequest request) {
        logger.info("Received package loaded notification for package: {}, truck: {}", 
                request.getPackageId(), request.getTruckId());
        
        // Check if the message has already been processed
        if (messageTrackingService.isMessageProcessed(request.getSeqNum())) {
            logger.info("Duplicate message received with seq_num: {}", request.getSeqNum());
            return ResponseEntity.ok().build();
        }
        
        try {
            // Find the package
            Optional<Package> packageOpt = packageRepository.findById(request.getPackageId());
            
            if (packageOpt.isPresent()) {
                Package pkg = packageOpt.get();
                
                // Update package status to LOADED
                pkg.setStatus(PackageStatus.LOADED);
                
                // Set the truck if provided
                if (request.getTruckId() != null) {
                    Optional<Truck> truckOpt = truckRepository.findById(request.getTruckId());
                    if (truckOpt.isPresent()) {
                        pkg.setTruck(truckOpt.get());
                    }
                }
                
                packageRepository.save(pkg);
                
                logger.info("Updated package {} status to LOADED", pkg.getId());
            } else {
                logger.error("Package {} not found for package loaded notification", 
                        request.getPackageId());
            }
        } catch (Exception e) {
            logger.error("Error processing package loaded notification", e);
        }
        
        // Mark the message as processed
        messageTrackingService.markMessageProcessed(request.getSeqNum(), request.getMessageType());
        
        return ResponseEntity.ok().build();
    }
    
    // Helper method to generate a general error response
    private UPSGeneralError createErrorResponse(long seqNum, int errorCode, String errorMsg) {
        UPSGeneralError error = new UPSGeneralError();
        error.setMessageType("UPSGeneralError");
        error.setSeqNum(messageTrackingService.getNextSeqNum());
        error.setTimestamp(Instant.now());
        error.setErrorCode(errorCode);
        error.setErrorMsg(errorMsg);
        
        // Track the outgoing message
        messageTrackingService.recordOutgoingMessage(error.getSeqNum(), error.getMessageType());
        
        return error;
    }
}