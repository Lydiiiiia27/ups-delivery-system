package com.ups.service.world;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.ups.model.Location;
import com.ups.model.entity.Truck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ups.WorldUpsProto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

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

    public WorldConnector(String host, int port, List<Truck> trucks, WorldResponseListener listener) throws IOException {
        this.responseListener = listener;
        try {
            this.socket = new Socket(host, port);
            connect(1, false, trucks);
            // Start the response listener after successful connection
            if (responseListener != null) {
                responseListener.startListening(socket);
            }
        } catch (IllegalArgumentException e) {
            this.socket = new Socket(host, port);
            connect(1, true, trucks);
            if (responseListener != null) {
                responseListener.startListening(socket);
            }
        }
    }

    private void connect(int worldId, boolean newWorld, List<Truck> trucks) throws IOException {
        // === 1. Create UConnect request ===
        WorldUpsProto.UConnect.Builder connectBuilder = WorldUpsProto.UConnect.newBuilder();
        if (!newWorld) {
            connectBuilder.setWorldid(worldId);
        }
        if (trucks != null && trucks.size() > 0) {
            WorldUpsProto.UInitTruck.Builder truckBuilder = WorldUpsProto.UInitTruck.newBuilder();
            for (Truck truck : trucks) {
                truckBuilder.setId(truck.getId());
                truckBuilder.setX(truck.getX());
                truckBuilder.setY(truck.getY());
                WorldUpsProto.UInitTruck truckRequest = truckBuilder.build();
                connectBuilder.addTrucks(truckRequest);
            }
        }
        connectBuilder.setIsAmazon(false); // false means UPS
        WorldUpsProto.UConnect connectRequest = connectBuilder.build();
        sendMessage(connectRequest);

        // === 2. Receive UConnected response ===
        WorldUpsProto.UConnected connectedResponse = receiveMessage(WorldUpsProto.UConnected.parser());
        String connectionResult = connectedResponse.getResult();
        if (connectionResult.equals("connected!")) {
            logger.info("Successfully connected to world simulator: {}", connectionResult);
            this.worldId = connectedResponse.getWorldid();
        } else {
            logger.error("Failed to connect to world simulator: {}", connectionResult);
            throw new IllegalArgumentException(connectionResult);
        }
        if (newWorld) {
            logger.info("Created new world with ID: {}", this.worldId);
        } else {
            logger.info("Connected to existing world with ID: {}", this.worldId);
        }
    }

    public Long getWorldId() {
        return worldId;
    }

    public void deliver(int truckId, long packageId, Location location, long seqNum) throws IOException {
        // === 1. Create UGoDeliver request ===
        WorldUpsProto.UGoDeliver.Builder deliverBuilder = WorldUpsProto.UGoDeliver.newBuilder();
        deliverBuilder.setTruckid(truckId);
        WorldUpsProto.UDeliveryLocation.Builder deliveryLocationBuilder = WorldUpsProto.UDeliveryLocation.newBuilder();
        deliveryLocationBuilder.setPackageid(packageId);
        deliveryLocationBuilder.setX(location.getX());
        deliveryLocationBuilder.setY(location.getY());
        WorldUpsProto.UDeliveryLocation deliveryLocation = deliveryLocationBuilder.build();
        deliverBuilder.addPackages(deliveryLocation);
        deliverBuilder.setSeqnum(seqNum);
        WorldUpsProto.UGoDeliver deliverRequest = deliverBuilder.build();

        // === 2. Create UCommands request ===
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.addDeliveries(deliverRequest);
        WorldUpsProto.UCommands commandsRequest = commandsBuilder.build();

        // Send command without waiting for response (response will be handled by listener)
        sendMessage(commandsRequest);
    }

    public void pickup(int truckId, int warehouseId, long seqNum) throws IOException {
        // === 1. Create UGoPickup request ===
        WorldUpsProto.UGoPickup.Builder pickupBuilder = WorldUpsProto.UGoPickup.newBuilder();
        pickupBuilder.setTruckid(truckId);
        pickupBuilder.setWhid(warehouseId);
        pickupBuilder.setSeqnum(seqNum);
        WorldUpsProto.UGoPickup pickupRequest = pickupBuilder.build();

        // === 2. Create UCommands request ===
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.addPickups(pickupRequest);
        WorldUpsProto.UCommands commandsRequest = commandsBuilder.build();

        // Send command without waiting for response (response will be handled by listener)
        sendMessage(commandsRequest);
    }
    
    public void query(int truckId, long seqNum) throws IOException {
        // === 1. Create UQuery request ===
        WorldUpsProto.UQuery.Builder queryBuilder = WorldUpsProto.UQuery.newBuilder();
        queryBuilder.setTruckid(truckId);
        queryBuilder.setSeqnum(seqNum);
        WorldUpsProto.UQuery queryRequest = queryBuilder.build();

        // === 2. Create UCommands request ===
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.addQueries(queryRequest);
        WorldUpsProto.UCommands commandsRequest = commandsBuilder.build();

        // Send command without waiting for response (response will be handled by listener)
        sendMessage(commandsRequest);
    }

    public void setSimulationSpeed(int speed) throws IOException {
        WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
        commandsBuilder.setSimspeed(speed);
        WorldUpsProto.UCommands commandsRequest = commandsBuilder.build();
        
        // Send command without waiting for response (response will be handled by listener)
        sendMessage(commandsRequest);
        logger.info("Simulation speed set to: {}", speed);
    }
    
    public void disconnect() throws IOException {
        if (socket == null || socket.isClosed()) {
            logger.info("Already disconnected from world simulator");
            return;
        }
        
        try {
            // Stop the response listener first
            if (responseListener != null) {
                responseListener.stopListening();
            }
            
            // Send disconnect command
            WorldUpsProto.UCommands.Builder commandsBuilder = WorldUpsProto.UCommands.newBuilder();
            commandsBuilder.setDisconnect(true);
            WorldUpsProto.UCommands commandsRequest = commandsBuilder.build();
            
            // Send the disconnect command
            sendMessage(commandsRequest);
            
            logger.info("Sent disconnect command to world simulator for world ID: {}", worldId);
        } catch (Exception e) {
            logger.warn("Error during disconnect from world simulator: {}", e.getMessage());
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
            this.seqNum.set(1);
            this.worldId = null;
        }
    }

    // Send a Protobuf message with length prefix
    private <T extends com.google.protobuf.Message> void sendMessage(T msg) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not connected");
        }
        
        OutputStream out = socket.getOutputStream();
        byte[] data = msg.toByteArray();
        CodedOutputStream codedOut = CodedOutputStream.newInstance(out);
        codedOut.writeUInt32NoTag(data.length);  // Varint32 length prefix
        codedOut.writeRawBytes(data);
        codedOut.flush();
        
        logger.debug("Sent message of type {}, size: {} bytes", msg.getClass().getSimpleName(), data.length);
    }

    // Receive a Protobuf message with length prefix
    private <T extends com.google.protobuf.Message> T receiveMessage(com.google.protobuf.Parser<T> parser) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is not connected");
        }
        
        InputStream in = socket.getInputStream();
        CodedInputStream codedIn = CodedInputStream.newInstance(in);
        int size = codedIn.readRawVarint32();
        int oldLimit = codedIn.pushLimit(size);
        T msg = parser.parseFrom(codedIn);
        codedIn.popLimit(oldLimit);
        
        logger.debug("Received message of type {}, size: {} bytes", msg.getClass().getSimpleName(), size);
        return msg;
    }
    
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }
    
    public long getNextSeqNum() {
        return seqNum.getAndIncrement();
    }
}