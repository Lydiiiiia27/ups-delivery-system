package com.ups.service.world;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.ups.WorldUpsProto;
import com.ups.model.Location;
import com.ups.model.entity.Truck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles connection to the World Simulator and sending commands
 */
@Component
public class WorldConnector {
    private static final Logger logger = LoggerFactory.getLogger(WorldConnector.class);
    
    private Socket socket;
    private AtomicLong seqNum = new AtomicLong(1);
    private Long worldId;
    private final WorldResponseListener responseListener;
    
    // Default constructor for Spring
    public WorldConnector() {
        this.responseListener = null;
    }
    
    @Autowired
    public WorldConnector(WorldResponseListener responseListener) {
        this.responseListener = responseListener;
    }
    
    /**
     * Connect to the World Simulator
     */
    public void connect(String host, int port, List<Truck> trucks, boolean newWorld, Long existingWorldId) throws IOException {
        try {
            this.socket = new Socket(host, port);
            
            if (newWorld) {
                // Create a new world
                connectToWorld(null, trucks);
            } else {
                // Connect to existing world
                connectToWorld(existingWorldId, trucks);
            }
            
            // Start the response listener
            if (responseListener != null) {
                responseListener.startListening(socket);
            }
            
        } catch (IOException e) {
            logger.error("Failed to connect to World Simulator at {}:{}: {}", host, port, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Connect to the World Simulator
     */
    private void connectToWorld(Long worldId, List<Truck> trucks) throws IOException {
        // Create UConnect request
        WorldUpsProto.UConnect.Builder connectBuilder = WorldUpsProto.UConnect.newBuilder();
        
        if (worldId != null) {
            connectBuilder.setWorldid(worldId);
        }
        
        // Add trucks to the request
        if (trucks != null && !trucks.isEmpty()) {
            for (Truck truck : trucks) {
                WorldUpsProto.UInitTruck truckRequest = WorldUpsProto.UInitTruck.newBuilder()
                        .setId(truck.getId())
                        .setX(truck.getX())
                        .setY(truck.getY())
                        .build();
                connectBuilder.addTrucks(truckRequest);
            }
        }
        
        // Set isAmazon to false (we are UPS)
        connectBuilder.setIsAmazon(false);
        
        // Build and send the request
        WorldUpsProto.UConnect connectRequest = connectBuilder.build();
        sendMessage(connectRequest);
        
        // Receive the response
        WorldUpsProto.UConnected connectedResponse = receiveMessage(WorldUpsProto.UConnected.parser());
        
        // Check if connection was successful
        String connectionResult = connectedResponse.getResult();
        if ("connected!".equals(connectionResult)) {
            this.worldId = connectedResponse.getWorldid();
            
            if (worldId == null) {
                logger.info("Successfully created new world with ID: {}", this.worldId);
            } else {
                logger.info("Successfully connected to existing world with ID: {}", this.worldId);
            }
        } else {
            logger.error("Failed to connect to World Simulator: {}", connectionResult);
            throw new IOException("Failed to connect to World Simulator: " + connectionResult);
        }
    }
    
    /**
     * Get the world ID
     */
    public Long getWorldId() {
        return worldId;
    }
    
    /**
     * Send a truck to deliver a package
     */
    public void deliver(int truckId, long packageId, Location location) throws IOException {
        // Get the next sequence number
        long sequenceNumber = seqNum.getAndIncrement();
        
        // Create UGoDeliver request
        WorldUpsProto.UGoDeliver.Builder deliverBuilder = WorldUpsProto.UGoDeliver.newBuilder();
        deliverBuilder.setTruckid(truckId);
        
        // Create UDeliveryLocation
        WorldUpsProto.UDeliveryLocation.Builder locationBuilder = WorldUpsProto.UDeliveryLocation.newBuilder();
        locationBuilder.setPackageid(packageId);
        locationBuilder.setX(location.getX());
        locationBuilder.setY(location.getY());
        
        // Add the delivery location to the request
        deliverBuilder.addPackages(locationBuilder.build());
        deliverBuilder.setSeqnum(sequenceNumber);
        
        // Create UCommands request
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.addDeliveries(deliverBuilder.build());
        
        // Send the request
        WorldUpsProto.UCommands command = commandsBuilder.build();
        sendMessage(command);
        
        logger.info("Sent delivery command for truck {} to deliver package {} to ({},{})",
                truckId, packageId, location.getX(), location.getY());
    }
    
    /**
     * Send a truck to pick up a package from a warehouse
     */
    public void pickup(int truckId, int warehouseId) throws IOException {
        // Get the next sequence number
        long sequenceNumber = seqNum.getAndIncrement();
        
        // Create UGoPickup request
        WorldUpsProto.UGoPickup.Builder pickupBuilder = WorldUpsProto.UGoPickup.newBuilder();
        pickupBuilder.setTruckid(truckId);
        pickupBuilder.setWhid(warehouseId);
        pickupBuilder.setSeqnum(sequenceNumber);
        
        // Create UCommands request
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.addPickups(pickupBuilder.build());
        
        // Send the request
        WorldUpsProto.UCommands command = commandsBuilder.build();
        sendMessage(command);
        
        logger.info("Sent pickup command for truck {} to warehouse {}", truckId, warehouseId);
    }
    
    /**
     * Query the status of a truck
     */
    public void queryTruckStatus(int truckId) throws IOException {
        // Get the next sequence number
        long sequenceNumber = seqNum.getAndIncrement();
        
        // Create UQuery request
        WorldUpsProto.UQuery.Builder queryBuilder = WorldUpsProto.UQuery.newBuilder();
        queryBuilder.setTruckid(truckId);
        queryBuilder.setSeqnum(sequenceNumber);
        
        // Create UCommands request
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.addQueries(queryBuilder.build());
        
        // Send the request
        WorldUpsProto.UCommands command = commandsBuilder.build();
        sendMessage(command);
        
        logger.info("Sent query command for truck {}", truckId);
    }
    
    /**
     * Set the simulation speed
     */
    public void setSimulationSpeed(int speed) throws IOException {
        // Create UCommands request
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.setSimspeed(speed);
        
        // Send the request
        WorldUpsProto.UCommands command = commandsBuilder.build();
        sendMessage(command);
        
        logger.info("Set simulation speed to {}", speed);
    }
    
    /**
     * Disconnect from the World Simulator
     */
    public void disconnect() throws IOException {
        if (socket == null || socket.isClosed()) {
            logger.info("Already disconnected from World Simulator");
            return;
        }
        
        try {
            // Stop the response listener first
            if (responseListener != null) {
                responseListener.stopListening();
            }
            
            // Create UCommands request with disconnect flag
            WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
            commandsBuilder.setDisconnect(true);
            
            // Send the request
            WorldUpsProto.UCommands command = commandsBuilder.build();
            sendMessage(command);
            
            logger.info("Sent disconnect command to World Simulator for world ID: {}", worldId);
        } catch (Exception e) {
            logger.warn("Error during disconnect from World Simulator: {}", e.getMessage());
        } finally {
            // Always close the socket
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    logger.info("Socket closed for world ID: {}", worldId);
                }
            } catch (IOException e) {
                logger.warn("Error closing socket: {}", e.getMessage());
            }
            
            // Reset state
            this.socket = null;
            this.worldId = null;
        }
    }
    
    /**
     * Check if the connector is connected to the World Simulator
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
    
    /**
     * Get the next sequence number
     */
    public long getNextSeqNum() {
        return seqNum.getAndIncrement();
    }
    
    /**
     * Send a Protobuf message with length prefix
     */
    private <T extends com.google.protobuf.Message> void sendMessage(T message) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not connected");
        }
        
        OutputStream out = socket.getOutputStream();
        byte[] data = message.toByteArray();
        
        // Create a CodedOutputStream to handle writing the message
        CodedOutputStream codedOut = CodedOutputStream.newInstance(out);
        
        // Write the message size as a Varint32
        codedOut.writeUInt32NoTag(data.length);
        
        // Write the message data
        codedOut.writeRawBytes(data);
        
        // Flush the stream
        codedOut.flush();
        
        logger.debug("Sent message of type {}, size: {} bytes", message.getClass().getSimpleName(), data.length);
    }
    
    /**
     * Receive a Protobuf message with length prefix
     */
    private <T extends com.google.protobuf.Message> T receiveMessage(com.google.protobuf.Parser<T> parser) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not connected");
        }
        
        InputStream in = socket.getInputStream();
        
        // Create a CodedInputStream to handle reading the message
        CodedInputStream codedIn = CodedInputStream.newInstance(in);
        
        // Read the message size as a Varint32
        int size = codedIn.readRawVarint32();
        
        // Read the message data
        int oldLimit = codedIn.pushLimit(size);
        T message = parser.parseFrom(codedIn);
        codedIn.popLimit(oldLimit);
        
        logger.debug("Received message of type {}, size: {} bytes", message.getClass().getSimpleName(), size);
        return message;
    }

    /**
     * Send acknowledgements for received messages
     */
    public void sendAcknowledgements(List<Long> acks) throws IOException {
        if (acks == null || acks.isEmpty()) {
            return;
        }
        
        // Create UCommands request with acknowledgements
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.addAllAcks(acks);
        
        // Send the request
        WorldUpsProto.UCommands command = commandsBuilder.build();
        sendMessage(command);
        
        logger.debug("Sent {} acknowledgements to World Simulator", acks.size());
    }
}