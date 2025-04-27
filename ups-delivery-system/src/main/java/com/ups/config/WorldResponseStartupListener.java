package com.ups.config;

import com.ups.service.world.WorldResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorldResponseStartupListener {
    private static final Logger logger = LoggerFactory.getLogger(WorldResponseStartupListener.class);
    
    private final WorldResponseHandler worldResponseHandler;
    private final Thread worldResponseProcessorThread;
    
    @Autowired
    public WorldResponseStartupListener(WorldResponseHandler worldResponseHandler,
                                       Thread worldResponseProcessorThread) {
        this.worldResponseHandler = worldResponseHandler;
        this.worldResponseProcessorThread = worldResponseProcessorThread;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Start the response processor thread when the application is ready
        if (!worldResponseProcessorThread.isAlive()) {
            worldResponseProcessorThread.start();
            logger.info("Started World Response Processor thread");
        }
    }
}