import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.io.IOException;
import java.net.Socket;

public class WorldConnector {
    private Socket socket;

    public WorldConnector(String host, int port, List<Truck> trucks) throws IOException {
        try {
            this.socket = new Socket(host, port);
            connect(1, false, trucks);
        }
        // catch (com.google.protobuf.InvalidProtocolBufferException e) {
        //     connect(1, true);
        // }
        catch (IllegalArgumentException e) {
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
                Location loc = truck.getLocation();
                truckBuilder.setX(loc.getX());
                truckBuilder.setY(loc.getY());
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
            System.out.println(connectionResult);
        }
        else {
            throw new IllegalArgumentException(connectionResult);
        }
        if (newWorld) {
            System.out.print("New ");
        }
        System.out.println("world ID: " + connectedResponse.getWorldid());
    }

    public void deliver(int truckId, int packageId, Location location, long seqNum) throws IOException {
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
        printResponse(responses);
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
        printResponse(responses);
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
        printResponse(responses);
    }

    private WorldUps1.UResponses sendAndReceive(WorldUps1.UCommands commandsRequest) throws IOException {
        sendMessage(commandsRequest);
        WorldUps1.UResponses response = receiveMessage(WorldUps1.UResponses.parser());
        // List<java.lang.Long> ackList = response.getAcksList();
        // for (java.lang.Long item : ackList) {
        //     ack(socket, item);
        // }
        System.out.println("Response message received");
        return response;
    }

    private static void ack(Socket socket, long ackNum) throws IOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        // === Create UCommands request ===
        WorldUps1.UCommands.Builder commandsBuilder = WorldUps1.UCommands.newBuilder();
        commandsBuilder.addAcks(ackNum);
        WorldUps1.UCommands commandsRequest = commandsBuilder.build();
        sendMessage(commandsRequest, out);
    }

    private void printResponse(WorldUps1.UResponses response) {
        System.out.println("=== Response info ===");
        List<WorldUps1.UFinished> finishedList = response.getCompletionsList();
        for (WorldUps1.UFinished item : finishedList) {
            System.out.println("truck id: " + item.getTruckid());
            System.out.println("x: " + item.getX());
            System.out.println("y: " + item.getY());
            System.out.println("status: " + item.getStatus());
            System.out.println("seq num: " + item.getSeqnum());
        }
        List<WorldUps1.UDeliveryMade> deliveryMadeList = response.getDeliveredList();
        for (WorldUps1.UDeliveryMade item : deliveryMadeList) {
            System.out.println("truck id: " + item.getTruckid());
            System.out.println("package id: " + item.getPackageid());
            System.out.println("seq num: " + item.getSeqnum());
        }
        if (response.hasFinished()) {
            System.out.println("finished: " + response.getFinished());
        }
        else {
            System.out.println("no finished field");
        }
        List<java.lang.Long> ackList = response.getAcksList();
        for (java.lang.Long item : ackList) {
            System.out.println("ack: " + item);
        }
        List<WorldUps1.UTruck> truckList = response.getTruckstatusList();
        for (WorldUps1.UTruck item : truckList) {
            System.out.println("truck id: " + item.getTruckid());
            System.out.println("status: " + item.getStatus());
            System.out.println("x: " + item.getX());
            System.out.println("y: " + item.getY());
            System.out.println("seq num: " + item.getSeqnum());
        }
        List<WorldUps1.UErr> errList = response.getErrorList();
        for (WorldUps1.UErr item : errList) {
            System.out.println("err: " + item.getErr());
            System.out.println("origin seq num: " + item.getOriginseqnum());
            System.out.println("seq num: " + item.getSeqnum());
        }
        System.out.println("=== Response info ===");
    }

    // Send a Protobuf message with length prefix
    public static <T extends com.google.protobuf.Message> void sendMessage(T msg, OutputStream out) throws IOException {
        byte[] data = msg.toByteArray();
        CodedOutputStream codedOut = CodedOutputStream.newInstance(out);
        codedOut.writeUInt32NoTag(data.length);  // Varint32 length prefix
        codedOut.writeRawBytes(data);
        codedOut.flush();
    }

    // Send a Protobuf message with length prefix
    private <T extends com.google.protobuf.Message> void sendMessage(T msg) throws IOException {
        OutputStream out = socket.getOutputStream();
        byte[] data = msg.toByteArray();
        CodedOutputStream codedOut = CodedOutputStream.newInstance(out);
        codedOut.writeUInt32NoTag(data.length);  // Varint32 length prefix
        codedOut.writeRawBytes(data);
        codedOut.flush();
    }

    // Receive a Protobuf message with length prefix
    private <T extends com.google.protobuf.Message> T receiveMessage(com.google.protobuf.Parser<T> parser) throws IOException {
        InputStream in = socket.getInputStream();
        CodedInputStream codedIn = CodedInputStream.newInstance(in);
        int size = codedIn.readRawVarint32();
        int oldLimit = codedIn.pushLimit(size);
        T msg = parser.parseFrom(codedIn);
        codedIn.popLimit(oldLimit);
        return msg;
    }
}