package com.ups.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener to start the World Response processor thread when the application is ready
 */
@Component
public class WorldResponseStartupListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WorldResponseStartupListener.class);
    
    private final Thread worldResponseProcessorThread;
    
    @Autowired
    public WorldResponseStartupListener(Thread worldResponseProcessorThread) {
        this.worldResponseProcessorThread = worldResponseProcessorThread;
    }
    
    /**
     * Start the World Response processor thread when the application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!worldResponseProcessorThread.isAlive()) {
            worldResponseProcessorThread.start();
            logger.info("Started World Response Processor thread");
        } else {
            logger.info("World Response Processor thread is already running");
        }
    }
}