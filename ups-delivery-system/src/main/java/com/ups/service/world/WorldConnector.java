package com.ups.service.world;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.ups.model.Location;
import com.ups.model.entity.Truck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WorldConnector {
    private static final Logger logger = LoggerFactory.getLogger(WorldConnector.class);
    private Socket socket;
    private AtomicLong seqNum = new AtomicLong(1);
    private Long worldId;

    public WorldConnector() {
        // Default constructor for Spring
    }

    public WorldConnector(String host, int port, List<Truck> trucks) throws IOException {
        try {
            this.socket = new Socket(host, port);
            connect(1, false, trucks);
        } catch (IllegalArgumentException e) {
            this.socket = new Socket(host, port);
            connect(1, true, trucks);
        }
    }

    private void connect(int worldId, boolean newWorld, List<Truck> trucks) throws IOException {
        // === 1. Create UConnect request ===
        WorldUps1.UConnect.Builder connectBuilder = WorldUps1.UConnect.newBuilder();
        if (!newWorld) {
            connectBuilder.setWorldid(worldId);
        }
        if (trucks != null && trucks.size() > 0) {
            WorldUps1.UInitTruck.Builder truckBuilder = WorldUps1.UInitTruck.newBuilder();
            for (Truck truck : trucks) {
                truckBuilder.setId(truck.getId());
                truckBuilder.setX(truck.getX());
                truckBuilder.setY(truck.getY());
                WorldUps1.UInitTruck truckRequest = truckBuilder.build();
                connectBuilder.addTrucks(truckRequest);
            }
        }
        connectBuilder.setIsAmazon(false); // false means UPS
        WorldUps1.UConnect connectRequest = connectBuilder.build();
        sendMessage(connectRequest);

        // === 2. Receive UConnected response ===
        WorldUps1.UConnected connectedResponse = receiveMessage(WorldUps1.UConnected.parser());
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
        WorldUps1.UGoDeliver.Builder deliverBuilder = WorldUps1.UGoDeliver.newBuilder();
        deliverBuilder.setTruckid(truckId);
        WorldUps1.UDeliveryLocation.Builder deliveryLocationBuilder = WorldUps1.UDeliveryLocation.newBuilder();
        deliveryLocationBuilder.setPackageid(packageId);
        deliveryLocationBuilder.setX(location.getX());
        deliveryLocationBuilder.setY(location.getY());
        WorldUps1.UDeliveryLocation deliveryLocation = deliveryLocationBuilder.build();
        deliverBuilder.addPackages(deliveryLocation);
        deliverBuilder.setSeqnum(seqNum);
        WorldUps1.UGoDeliver deliverRequest = deliverBuilder.build();

        // === 2. Create UCommands request ===
        WorldUps1.UCommands.Builder commandsBuilder = WorldUps1.UCommands.newBuilder();
        commandsBuilder.addDeliveries(deliverRequest);
        WorldUps1.UCommands commandsRequest = commandsBuilder.build();

        WorldUps1.UResponses responses = sendAndReceive(commandsRequest);
        processResponse(responses);
    }

    public void pickup(int truckId, int warehouseId, long seqNum) throws IOException {
        // === 1. Create UGoPickup request ===
        WorldUps1.UGoPickup.Builder pickupBuilder = WorldUps1.UGoPickup.newBuilder();
        pickupBuilder.setTruckid(truckId);
        pickupBuilder.setWhid(warehouseId);
        pickupBuilder.setSeqnum(seqNum);
        WorldUps1.UGoPickup pickupRequest = pickupBuilder.build();

        // === 2. Create UCommands request ===
        WorldUps1.UCommands.Builder commandsBuilder = WorldUps1.UCommands.newBuilder();
        commandsBuilder.addPickups(pickupRequest);
        WorldUps1.UCommands commandsRequest = commandsBuilder.build();

        WorldUps1.UResponses responses = sendAndReceive(commandsRequest);
        processResponse(responses);
    }
    
    public void query(int truckId, long seqNum) throws IOException {
        // === 1. Create UQuery request ===
        WorldUps1.UQuery.Builder queryBuilder = WorldUps1.UQuery.newBuilder();
        queryBuilder.setTruckid(truckId);
        queryBuilder.setSeqnum(seqNum);
        WorldUps1.UQuery queryRequest = queryBuilder.build();

        // === 2. Create UCommands request ===
        WorldUps1.UCommands.Builder commandsBuilder = WorldUps1.UCommands.newBuilder();
        commandsBuilder.addQueries(queryRequest);
        WorldUps1.UCommands commandsRequest = commandsBuilder.build();

        WorldUps1.UResponses responses = sendAndReceive(commandsRequest);
        processResponse(responses);
    }

    public void setSimulationSpeed(int speed) throws IOException {
        WorldUps1.UCommands.Builder commandsBuilder = WorldUps1.UCommands.newBuilder();
        commandsBuilder.setSimspeed(speed);
        WorldUps1.UCommands commandsRequest = commandsBuilder.build();
        
        WorldUps1.UResponses responses = sendAndReceive(commandsRequest);
        processResponse(responses);
        logger.info("Simulation speed set to: {}", speed);
    }
    
    public void disconnect() throws IOException {
        WorldUps1.UCommands.Builder commandsBuilder = WorldUps1.UCommands.newBuilder();
        commandsBuilder.setDisconnect(true);
        WorldUps1.UCommands commandsRequest = commandsBuilder.build();
        
        WorldUps1.UResponses responses = sendAndReceive(commandsRequest);
        processResponse(responses);
        
        if (responses.hasFinished() && responses.getFinished()) {
            logger.info("Successfully disconnected from world simulator");
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private WorldUps1.UResponses sendAndReceive(WorldUps1.UCommands commandsRequest) throws IOException {
        sendMessage(commandsRequest);
        WorldUps1.UResponses response = receiveMessage(WorldUps1.UResponses.parser());
        
        // Process acknowledgments
        if (response.getAcksCount() > 0) {
            logger.debug("Received {} acknowledgments", response.getAcksCount());
        }
        
        return response;
    }

    private void processResponse(WorldUps1.UResponses response) {
        // Process completions
        for (WorldUps1.UFinished completion : response.getCompletionsList()) {
            logger.info("Truck {} completed job at ({},{}) with status: {}", 
                    completion.getTruckid(), completion.getX(), completion.getY(), completion.getStatus());
        }
        
        // Process deliveries
        for (WorldUps1.UDeliveryMade delivery : response.getDeliveredList()) {
            logger.info("Truck {} delivered package {}", delivery.getTruckid(), delivery.getPackageid());
        }
        
        // Process truck status updates
        for (WorldUps1.UTruck truck : response.getTruckstatusList()) {
            logger.info("Truck {} status: {} at location ({},{})", 
                    truck.getTruckid(), truck.getStatus(), truck.getX(), truck.getY());
        }
        
        // Process errors
        for (WorldUps1.UErr error : response.getErrorList()) {
            logger.error("Error for sequence {}: {}", error.getOriginseqnum(), error.getErr());
        }
        
        // Handle finished flag
        if (response.hasFinished()) {
            logger.info("World simulation finished: {}", response.getFinished());
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