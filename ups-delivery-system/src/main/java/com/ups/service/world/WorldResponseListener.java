package com.ups.service.world;

import com.google.protobuf.CodedInputStream;
import com.ups.WorldUpsProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens for responses from the World Simulator and forwards them to the WorldResponseHandler
 */
@Service
public class WorldResponseListener {
    private static final Logger logger = LoggerFactory.getLogger(WorldResponseListener.class);
    
    private final WorldResponseHandler responseHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;
    private Socket socket;
    
    @Autowired
    public WorldResponseListener(WorldResponseHandler responseHandler) {
        this.responseHandler = responseHandler;
    }
    
    /**
     * Starts the listener thread that continuously receives responses from the World Simulator
     * @param socket The socket connected to the World Simulator
     */
    public void startListening(Socket socket) {
        if (socket == null || socket.isClosed()) {
            logger.error("Cannot start listening on null or closed socket");
            return;
        }
        
        if (running.compareAndSet(false, true)) {
            this.socket = socket;
            listenerThread = new Thread(this::listenForResponses, "WorldResponseListener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            logger.info("Started World Response Listener thread");
        } else {
            logger.warn("Listener is already running");
        }
    }
    
    /**
     * Stops the listener thread
     */
    public void stopListening() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping World Response Listener");
            
            if (listenerThread != null) {
                listenerThread.interrupt();
                try {
                    listenerThread.join(5000); // Wait up to 5 seconds for thread to stop
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while waiting for listener thread to stop");
                }
                listenerThread = null;
            }
        } else {
            logger.info("Listener is not running");
        }
    }
    
    /**
     * Main loop for listening to responses from the World Simulator
     */
    private void listenForResponses() {
        logger.info("World Response Listener thread started");
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                WorldUpsProto.UResponses response = receiveResponse();
                if (response != null) {
                    responseHandler.queueResponse(response);
                    logger.debug("Received and queued response from World Simulator");
                }
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error receiving response from World Simulator: {}", e.getMessage());
                    // Attempt to reconnect or handle error
                    try {
                        Thread.sleep(1000); // Wait before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Unexpected error in listener thread: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000); // Wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("World Response Listener thread stopping");
    }
    
    /**
     * Receives a response from the World Simulator
     * @return The UResponses message received from the World Simulator
     * @throws IOException If an error occurs while reading from the socket
     */
    private WorldUpsProto.UResponses receiveResponse() throws IOException {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            throw new IOException("Socket is not connected");
        }
        
        try {
            InputStream in = socket.getInputStream();
            CodedInputStream codedIn = CodedInputStream.newInstance(in);
            
            // Read the message size
            int size = codedIn.readRawVarint32();
            if (size <= 0) {
                logger.warn("Received invalid message size: {}", size);
                return null;
            }
            
            // Read the message
            int oldLimit = codedIn.pushLimit(size);
            WorldUpsProto.UResponses response = WorldUpsProto.UResponses.parseFrom(codedIn);
            codedIn.popLimit(oldLimit);
            
            logger.debug("Received message of size {} bytes", size);
            return response;
        } catch (IOException e) {
            logger.error("Error reading response from socket: {}", e.getMessage());
            throw e;
        }
    }
}